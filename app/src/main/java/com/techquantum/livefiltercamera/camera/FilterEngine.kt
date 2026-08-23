package com.techquantum.livefiltercamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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

    val pipelineManager: FilterPipelineManager = FilterPipelineManager(context) { newFilter ->
        gpuImageView.filter = newFilter
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
        val rotation = when (rotationDegrees) {
            90 -> Rotation.ROTATION_90
            180 -> Rotation.ROTATION_180
            270 -> Rotation.ROTATION_270
            else -> Rotation.NORMAL
        }
        gpuImageView.gpuImage.setRotation(rotation, isFrontCamera, false)
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessingFrame.get()) {
            imageProxy.close()
            return
        }

        isProcessingFrame.set(true)
        try {
            // CameraX built-in high performance bitmap extraction
            val bitmap = imageProxy.toBitmap()
            gpuImageView.setImage(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
            isProcessingFrame.set(false)
        }
    }

    fun applyFilter(filter: GPUImageFilter) {
        gpuImageView.filter = filter
    }
}
