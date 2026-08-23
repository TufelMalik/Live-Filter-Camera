package com.techquantum.livefiltercamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.techquantum.livefiltercamera.filter.FilterPipelineManager
import jp.co.cyberagent.android.gpuimage.GPUImage
import jp.co.cyberagent.android.gpuimage.GPUImageView
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.util.Rotation
import java.util.concurrent.atomic.AtomicBoolean

class FilterEngine(
    private val context: Context,
    val gpuImageView: GPUImageView
) : ImageAnalysis.Analyzer {

    private val isProcessingFrame = AtomicBoolean(false)
    private var isFrontCamera = false
    private var rotationDegrees = 90

    // Double-buffered reusable bitmaps for zero-allocation, thread-safe texture updates
    private var pingPongBitmaps: Array<Bitmap?> = arrayOfNulls(2)
    private var pingPongIndex = 0
    private var rowBuffer: java.nio.ByteBuffer? = null

    val pipelineManager: FilterPipelineManager = FilterPipelineManager(context) { newFilter ->
        gpuImageView.post {
            gpuImageView.filter = newFilter
        }
    }

    init {
        gpuImageView.setScaleType(GPUImage.ScaleType.CENTER_CROP)
        updateGpuRotation()
    }

    fun setCameraFacing(isFront: Boolean) {
        this.isFrontCamera = isFront
        updateGpuRotation()
    }

    fun setSensorRotation(degrees: Int) {
        this.rotationDegrees = degrees
        updateGpuRotation()
    }

    private fun updateGpuRotation() {
        if (isFrontCamera) {
            // Front Camera: Sensor rotation is 270 deg in portrait.
            // In GPUImage, flipVertical=true with ROTATION_270 correctly produces upright, mirrored selfie view.
            val rotation = when (rotationDegrees) {
                90 -> Rotation.ROTATION_90
                180 -> Rotation.ROTATION_180
                270 -> Rotation.ROTATION_270
                else -> Rotation.ROTATION_270
            }
            gpuImageView.gpuImage.setRotation(rotation, false, true)
        } else {
            // Rear Camera: Sensor rotation is 90 deg in portrait, no flipping needed.
            val rotation = when (rotationDegrees) {
                90 -> Rotation.ROTATION_90
                180 -> Rotation.ROTATION_180
                270 -> Rotation.ROTATION_270
                else -> Rotation.ROTATION_90
            }
            gpuImageView.gpuImage.setRotation(rotation, false, false)
        }
    }

    override fun analyze(imageProxy: ImageProxy) {
        // Drop frame if GPU is still processing previous one
        if (!isProcessingFrame.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        try {
            val width = imageProxy.width
            val height = imageProxy.height

            val targetIndex = pingPongIndex % 2
            pingPongIndex++

            var bitmap = pingPongBitmaps[targetIndex]
            if (bitmap == null || bitmap.width != width || bitmap.height != height || bitmap.isRecycled) {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                pingPongBitmaps[targetIndex] = bitmap
            }

            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride

            if (pixelStride == 4 && rowStride == width * 4) {
                // Optimal zero-allocation path: direct buffer copy
                buffer.rewind()
                bitmap.copyPixelsFromBuffer(buffer)
            } else if (pixelStride == 4) {
                // Row-by-row packed copy if hardware added row padding
                val requiredCapacity = width * height * 4
                var directBuf = rowBuffer
                if (directBuf == null || directBuf.capacity() < requiredCapacity) {
                    directBuf = java.nio.ByteBuffer.allocateDirect(requiredCapacity)
                    rowBuffer = directBuf
                }
                directBuf.clear()

                val rowWidthBytes = width * 4
                for (row in 0 until height) {
                    buffer.position(row * rowStride)
                    buffer.limit(row * rowStride + rowWidthBytes)
                    directBuf.put(buffer)
                }
                directBuf.flip()
                bitmap.copyPixelsFromBuffer(directBuf)
            } else {
                // Fallback for non-standard pixel strides
                bitmap = imageProxy.toBitmap()
            }

            gpuImageView.setImage(bitmap)
        } catch (e: Exception) {
            // Silently handle frame conversion errors without logging to avoid logcat spam
        } finally {
            imageProxy.close()
            isProcessingFrame.set(false)
        }
    }

    fun applyFilter(filter: GPUImageFilter) {
        gpuImageView.post {
            gpuImageView.filter = filter
        }
    }
}
