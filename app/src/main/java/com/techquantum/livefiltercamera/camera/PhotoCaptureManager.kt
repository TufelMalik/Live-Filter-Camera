package com.techquantum.livefiltercamera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import com.techquantum.livefiltercamera.filter.FilterPipelineManager
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PhotoCaptureManager(
    private val context: Context,
    private val filterPipelineManager: FilterPipelineManager
) {
    private val TAG = "PhotoCaptureManager"
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun capturePhoto(
        imageCapture: ImageCapture?,
        isFrontCamera: Boolean,
        onCaptureStarted: () -> Unit = {},
        onPhotoSaved: (Uri, Bitmap) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (imageCapture == null) {
            onError(IllegalStateException("ImageCapture use case is not bound or initialized"))
            return
        }

        onCaptureStarted()

        imageCapture.takePicture(
            captureExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    try {
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val rawBitmap = imageProxy.toBitmap()
                        imageProxy.close()

                        // 1. Correct Orientation & Front Camera Mirroring
                        val matrix = Matrix()
                        if (rotationDegrees != 0) {
                            matrix.postRotate(rotationDegrees.toFloat())
                        }
                        if (isFrontCamera) {
                            matrix.postScale(-1f, 1f)
                        }

                        val orientedBitmap = if (rotationDegrees != 0 || isFrontCamera) {
                            Bitmap.createBitmap(
                                rawBitmap,
                                0,
                                0,
                                rawBitmap.width,
                                rawBitmap.height,
                                matrix,
                                true
                            )
                        } else {
                            rawBitmap
                        }

                        // 2. Pass frame through current GPUImage filter pipeline (Bake Filter)
                        val filteredBitmap = filterPipelineManager.applyToBitmap(orientedBitmap)

                        // 3. Generate a square / proportionate thumbnail
                        val thumbSize = 180
                        val thumbnail = Bitmap.createScaledBitmap(
                            filteredBitmap,
                            thumbSize,
                            (thumbSize * (filteredBitmap.height.toFloat() / filteredBitmap.width)).toInt().coerceAtLeast(1),
                            true
                        )

                        // 4. Save to MediaStore / DCIM
                        val savedUri = saveBitmapToMediaStore(filteredBitmap)

                        if (savedUri != null) {
                            onPhotoSaved(savedUri, thumbnail)
                        } else {
                            onError(IllegalStateException("Failed to save image to MediaStore"))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing captured photo", e)
                        onError(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    onError(exception)
                }
            }
        )
    }

    fun captureBurst(
        imageCapture: ImageCapture?,
        isFrontCamera: Boolean,
        totalCount: Int = 5,
        onBurstProgress: (Int, Int) -> Unit,
        onPhotoSaved: (Uri, Bitmap) -> Unit,
        onBurstComplete: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (imageCapture == null) {
            onError(IllegalStateException("ImageCapture use case is not bound or initialized"))
            return
        }

        var completedCount = 0

        fun takeNextShot(index: Int) {
            if (index > totalCount) {
                onBurstComplete()
                return
            }

            onBurstProgress(index, totalCount)

            imageCapture.takePicture(
                captureExecutor,
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(imageProxy: ImageProxy) {
                        try {
                            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                            val rawBitmap = imageProxy.toBitmap()
                            imageProxy.close()

                            val matrix = Matrix()
                            if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())
                            if (isFrontCamera) matrix.postScale(-1f, 1f)

                            val orientedBitmap = if (rotationDegrees != 0 || isFrontCamera) {
                                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                            } else {
                                rawBitmap
                            }

                            val filteredBitmap = filterPipelineManager.applyToBitmap(orientedBitmap)
                            val thumbSize = 180
                            val thumbnail = Bitmap.createScaledBitmap(
                                filteredBitmap,
                                thumbSize,
                                (thumbSize * (filteredBitmap.height.toFloat() / filteredBitmap.width)).toInt().coerceAtLeast(1),
                                true
                            )

                            val savedUri = saveBitmapToMediaStore(filteredBitmap)
                            if (savedUri != null) {
                                onPhotoSaved(savedUri, thumbnail)
                            }

                            completedCount++
                            if (completedCount < totalCount) {
                                // Small delay before taking next burst photo
                                Thread.sleep(120)
                                takeNextShot(completedCount + 1)
                            } else {
                                onBurstComplete()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Burst capture step failed", e)
                            completedCount++
                            if (completedCount < totalCount) {
                                takeNextShot(completedCount + 1)
                            } else {
                                onBurstComplete()
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Burst photo capture failed at $index: ${exception.message}", exception)
                        completedCount++
                        if (completedCount < totalCount) {
                            takeNextShot(completedCount + 1)
                        } else {
                            onBurstComplete()
                        }
                    }
                }
            )
        }

        takeNextShot(1)
    }

    private fun saveBitmapToMediaStore(bitmap: Bitmap): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "IMG_LFC_$timeStamp.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + "LiveFilterCamera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        try {
            resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }

            return uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write bitmap to URI: $uri", e)
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {}
            return null
        }
    }

    fun shutdown() {
        captureExecutor.shutdown()
    }
}
