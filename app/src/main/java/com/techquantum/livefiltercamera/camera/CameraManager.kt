package com.techquantum.livefiltercamera.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

enum class FlashMode {
    OFF,
    ON,
    AUTO
}

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val filterEngine: FilterEngine
) {
    private val TAG = "CameraManager"
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraControl: CameraControl? = null
    private var cameraInfo: CameraInfo? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var currentFlashMode = FlashMode.OFF

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    var imageCapture: ImageCapture? = null
        private set

    val photoCaptureManager = PhotoCaptureManager(context, filterEngine.pipelineManager)
    val videoRecordManager = VideoRecordManager(context)

    fun startCamera(onReady: () -> Unit = {}) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        filterEngine.setCameraFacing(lensFacing == CameraSelector.LENS_FACING_FRONT)

        val resolutionSelector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        // 1. ImageAnalysis UseCase (High-fps live GL preview)
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(cameraExecutor, filterEngine)

        // 2. ImageCapture UseCase (Full resolution photo capture)
        val imageCaptureBuilder = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setFlashMode(
                when (currentFlashMode) {
                    FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                    FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                    FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
                }
            )

        val captureUseCase = imageCaptureBuilder.build()
        imageCapture = captureUseCase

        // 3. VideoCapture UseCase (HD recording)
        val videoCaptureUseCase = videoRecordManager.videoCapture

        try {
            // Check which use cases can be bound together
            camera = if (provider.hasCamera(cameraSelector)) {
                try {
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis,
                        captureUseCase,
                        videoCaptureUseCase
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Binding all 3 use cases failed, falling back to analysis and capture: ${e.message}")
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageAnalysis,
                        captureUseCase
                    )
                }
            } else {
                null
            }

            cameraControl = camera?.cameraControl
            cameraInfo = camera?.cameraInfo

            // Set rotation from camera info
            cameraInfo?.let {
                filterEngine.setSensorRotation(it.sensorRotationDegrees)
            }

            // Sync flash mode
            applyFlashMode(currentFlashMode)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        bindCameraUseCases()
    }

    fun setFlashMode(flashMode: FlashMode) {
        currentFlashMode = flashMode
        applyFlashMode(flashMode)
    }

    private fun applyFlashMode(flashMode: FlashMode) {
        val camCapture = imageCapture ?: return
        when (flashMode) {
            FlashMode.OFF -> {
                camCapture.flashMode = ImageCapture.FLASH_MODE_OFF
                cameraControl?.enableTorch(false)
            }
            FlashMode.ON -> {
                camCapture.flashMode = ImageCapture.FLASH_MODE_ON
                if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    cameraControl?.enableTorch(true)
                }
            }
            FlashMode.AUTO -> {
                camCapture.flashMode = ImageCapture.FLASH_MODE_AUTO
                cameraControl?.enableTorch(false)
            }
        }
    }

    fun setLinearZoom(zoom: Float) {
        cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    fun isFrontCamera(): Boolean = lensFacing == CameraSelector.LENS_FACING_FRONT

    fun shutdown() {
        cameraProvider?.unbindAll()
        photoCaptureManager.shutdown()
        cameraExecutor.shutdown()
    }
}
