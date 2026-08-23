package com.techquantum.livefiltercamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
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
    private var selectedZoomPreset: Float = 0.5f
    private var currentActiveSelector: CameraSelector? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "filter-analysis-thread").also { it.priority = Thread.MAX_PRIORITY }
    }

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

    @OptIn(ExperimentalCamera2Interop::class)
    private fun getDesiredCameraSelector(isUltraWideDesired: Boolean): CameraSelector {
        val provider = cameraProvider ?: return if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        val availableCameras = try {
            provider.availableCameraInfos
        } catch (e: Exception) {
            emptyList()
        }

        val matchingCameras = availableCameras.filter { info ->
            try {
                val cam2 = Camera2CameraInfo.from(info)
                val facing = cam2.getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    facing == CameraCharacteristics.LENS_FACING_FRONT
                } else {
                    facing == CameraCharacteristics.LENS_FACING_BACK
                }
            } catch (e: Exception) {
                false
            }
        }

        if (matchingCameras.isEmpty()) {
            return if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
        }

        if (isUltraWideDesired) {
            // Find the camera with the shortest focal length (wide/ultra-wide FOV)
            val ultraWideCamera = matchingCameras.minByOrNull { info ->
                try {
                    val cam2 = Camera2CameraInfo.from(info)
                    val focalLengths = cam2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    focalLengths?.firstOrNull() ?: Float.MAX_VALUE
                } catch (e: Exception) {
                    Float.MAX_VALUE
                }
            }

            if (ultraWideCamera != null && matchingCameras.size > 1) {
                return ultraWideCamera.cameraSelector
            }
        } else {
            // For standard / zoomed view (1x, 2x), prefer the standard main camera (focal length >= 3.0mm)
            val mainCamera = matchingCameras.filter { info ->
                try {
                    val cam2 = Camera2CameraInfo.from(info)
                    val focalLengths = cam2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    val focal = focalLengths?.firstOrNull() ?: 4.0f
                    focal >= 3.0f
                } catch (e: Exception) {
                    true
                }
            }.minByOrNull { info ->
                try {
                    val cam2 = Camera2CameraInfo.from(info)
                    val focalLengths = cam2.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                    focalLengths?.firstOrNull() ?: 4.0f
                } catch (e: Exception) {
                    4.0f
                }
            }

            if (mainCamera != null) {
                return mainCamera.cameraSelector
            }
        }

        return matchingCameras.firstOrNull()?.cameraSelector ?: (
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        )
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val isUltraWide = selectedZoomPreset <= 0.75f
        val cameraSelector = getDesiredCameraSelector(isUltraWide)
        currentActiveSelector = cameraSelector

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

        // 1. ImageAnalysis UseCase (High-fps live GL preview with direct RGBA_8888)
        val imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            analysisExecutor,
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
                applyZoomPreset(selectedZoomPreset)
            }

            // Sync flash mode
            applyFlashMode(currentFlashMode)

        } catch (e: Exception) {
            Log.e(CameraManagerTag, "Failed to bind camera use cases", e)
        }
    }

    fun setZoomRatio(ratio: Float) {
        selectedZoomPreset = ratio
        val wantsUltraWide = ratio <= 0.75f
        val desiredSelector = getDesiredCameraSelector(wantsUltraWide)

        if (currentActiveSelector != desiredSelector && cameraProvider != null) {
            bindCameraUseCases()
        } else {
            applyZoomPreset(ratio)
        }
    }

    fun getZoomRatio(): Float = selectedZoomPreset

    private fun applyZoomPreset(preset: Float) {
        val info = cameraInfo ?: return
        val control = cameraControl ?: return
        val zoomState = info.zoomState.value
        val minZoom = zoomState?.minZoomRatio ?: 1.0f
        val maxZoom = zoomState?.maxZoomRatio ?: 8.0f

        val targetRatio = if (minZoom < 0.85f) {
            // Hardware has native ultra-wide zoom (< 1.0x) on logical multi-camera
            when {
                preset <= 0.75f -> minZoom.coerceIn(0.5f, 1.0f)
                preset <= 1.25f -> 1.0f
                else -> preset.coerceIn(minZoom, maxZoom)
            }
        } else {
            // Standard sensor or dedicated physical camera where minZoom (1.0x) is base wide view
            when {
                preset <= 0.75f -> minZoom
                preset <= 1.25f -> 1.0f.coerceIn(minZoom, maxZoom)
                else -> preset.coerceIn(minZoom, maxZoom)
            }
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
        analysisExecutor.shutdown()
    }
}
