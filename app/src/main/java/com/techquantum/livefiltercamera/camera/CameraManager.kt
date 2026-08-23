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
    private val CameraManagerTag = "CameraManager"
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
                Log.e(CameraManagerTag, "Use case binding failed", e)
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
                    Size(960, 540),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        // 1. ImageAnalysis UseCase (High-fps live GL preview)
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "filter-thread").also { it.priority = Thread.MAX_PRIORITY }
            },
            filterEngine
        )

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
                    Log.w(CameraManagerTag, "Binding all 3 use cases failed, falling back to analysis and capture: ${e.message}")
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
            cameraInfo?.let { info ->
                filterEngine.setSensorRotation(info.sensorRotationDegrees)
                // Set default 0.5x zoom for both front and back cameras
                applyZoomPreset(selectedZoomPreset)
            }

            // Sync flash mode
            applyFlashMode(currentFlashMode)

        } catch (e: Exception) {
            Log.e(CameraManagerTag, "Failed to bind camera use cases", e)
        }
    }

    private var selectedZoomPreset: Float = 0.5f

    fun setZoomRatio(ratio: Float) {
        selectedZoomPreset = ratio
        applyZoomPreset(ratio)
    }

    fun getZoomRatio(): Float = selectedZoomPreset

    private fun applyZoomPreset(preset: Float) {
        val info = cameraInfo ?: return
        val control = cameraControl ?: return
        val zoomState = info.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1.0f
        val maxZoom = zoomState?.maxZoomRatio ?: 8.0f

        val targetRatio = if (minZoom < 0.85f) {
            // Hardware has native ultra-wide zoom (< 1.0x)
            when {
                preset <= 0.55f -> minZoom.coerceIn(0.5f, 1.0f)
                preset <= 1.1f -> 1.0f
                else -> 2.0f
            }.coerceIn(minZoom, maxZoom)
        } else {
            // Standard sensor where minZoom (1.0x) is the widest available hardware view
            when {
                preset <= 0.55f -> minZoom // 1.0x widest full sensor (0.5x wide view)
                preset <= 1.1f -> (minZoom * 1.75f).coerceAtMost(maxZoom) // 1.75x standard view (clear difference from 0.5x!)
                else -> (minZoom * 3.0f).coerceAtMost(maxZoom) // 3.0x close-up (2x view)
            }.coerceIn(minZoom, maxZoom)
        }

        control.setZoomRatio(targetRatio)
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
