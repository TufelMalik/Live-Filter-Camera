package com.techquantum.livefiltercamera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techquantum.livefiltercamera.camera.CameraManager
import com.techquantum.livefiltercamera.camera.FilterEngine
import com.techquantum.livefiltercamera.camera.FlashMode
import com.techquantum.livefiltercamera.gallery.GalleryScreen
import com.techquantum.livefiltercamera.model.BeautyEffect
import com.techquantum.livefiltercamera.model.FilterCategory
import com.techquantum.livefiltercamera.model.FilterPreset
import com.techquantum.livefiltercamera.model.HdParameters
import com.techquantum.livefiltercamera.model.ShaderEffect
import jp.co.cyberagent.android.gpuimage.GPUImageView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CameraScreen(
    viewModel: CameraViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
    }

    if (!hasCameraPermission) {
        CameraPermissionRequestScreen(
            onRequestPermission = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    )
                )
            },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
        return
    }

    // In-App Gallery Navigation
    if (uiState.showGalleryScreen) {
        GalleryScreen(
            onNavigateBack = { viewModel.closeGallery() }
        )
        return
    }

    // Camera and Filter Setup
    val gpuImageView = remember { GPUImageView(context) }
    val filterEngine = remember { FilterEngine(context, gpuImageView) }
    val cameraManager = remember { CameraManager(context, lifecycleOwner, filterEngine) }

    // Connect ViewModel to Filter Engine
    LaunchedEffect(Unit) {
        viewModel.setFilterListeners(
            onPresetChanged = { preset, bitmap ->
                filterEngine.pipelineManager.setLutPreset(preset, bitmap)
            },
            onLutIntensityChanged = { intensity ->
                filterEngine.pipelineManager.setLutIntensity(intensity)
            },
            onShaderEffectChanged = { effectId, isEnabled, intensity ->
                filterEngine.pipelineManager.updateShaderEffect(effectId, isEnabled, intensity)
            },
            onBeautyEffectChanged = { effectId, isEnabled, intensity ->
                filterEngine.pipelineManager.updateBeautyEffect(effectId, isEnabled, intensity)
            },
            onHdOptionChanged = { optionId, value ->
                filterEngine.pipelineManager.updateHdOption(optionId, value)
            },
            onBypassChanged = { bypass ->
                filterEngine.pipelineManager.setBypass(bypass)
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        cameraManager.startCamera()
        onDispose {
            cameraManager.shutdown()
        }
    }

    fun executeSinglePhotoCapture() {
        viewModel.triggerShutterAnimation()
        cameraManager.photoCaptureManager.capturePhoto(
            imageCapture = cameraManager.imageCapture,
            isFrontCamera = cameraManager.isFrontCamera(),
            onCaptureStarted = {},
            onPhotoSaved = { uri, thumbnail ->
                viewModel.onMediaSaved(uri, thumbnail)
                coroutineScope.launch {
                    Toast.makeText(context, "Photo saved to Gallery!", Toast.LENGTH_SHORT).show()
                }
            },
            onError = { e ->
                coroutineScope.launch {
                    Toast.makeText(context, "Capture failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    fun onShutterTrigger() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        if (uiState.timerMode.seconds > 0) {
            viewModel.startTimerCountdown {
                executeSinglePhotoCapture()
            }
        } else {
            executeSinglePhotoCapture()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (uiState.countdownRemaining != null) {
                            viewModel.cancelTimerCountdown()
                        } else {
                            viewModel.onUserInteraction()
                        }
                    },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cameraManager.switchCamera()
                        viewModel.setCameraFacing(cameraManager.isFrontCamera())
                    }
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    val newZoom = (uiState.zoomRatio * zoom).coerceIn(0.5f, 5.0f)
                    cameraManager.setZoomRatio(newZoom)
                    viewModel.setZoomRatio(newZoom)
                }
            }
    ) {
        // 1. Live Filter GPU Preview (Normal upright without inverted Compose rotation)
        AndroidView(
            factory = { gpuImageView },
            modifier = Modifier.fillMaxSize()
        )

        // Shutter White Flash Animation
        AnimatedVisibility(
            visible = uiState.showShutterFlash,
            enter = fadeIn(animationSpec = tween(40)),
            exit = fadeOut(animationSpec = tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }

        // Top & Bottom Vignette Gradient Overlays
        AnimatedVisibility(
            visible = uiState.isControlsVisible || uiState.isRecordingVideo,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                            )
                        )
                )
            }
        }

        // 2. Video Recording Top Banner
        if (uiState.isRecordingVideo) {
            val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .alpha(pulseAlpha)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val minutes = uiState.recordingDurationSec / 60
                val seconds = uiState.recordingDurationSec % 60
                Text(
                    text = String.format("%02d:%02d / 05:00", minutes, seconds),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 3. Top Bar Camera Controls (Flash, Timer, Compare, Beauty, Shader FX, Reset, Switch Camera)
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isRecordingVideo,
            enter = fadeIn(tween(200)) + slideInVertically { -it },
            exit = fadeOut(tween(200)) + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopBarControls(
                flashMode = uiState.flashMode,
                timerMode = uiState.timerMode,
                isFrontCamera = uiState.isFrontCamera,
                onCycleFlash = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newMode = viewModel.cycleFlashMode()
                    cameraManager.setFlashMode(newMode)
                },
                onCycleTimer = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.cycleTimerMode()
                },
                onSwitchCamera = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraManager.switchCamera()
                    viewModel.setCameraFacing(cameraManager.isFrontCamera())
                },
                onToggleEffects = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleEffectsPanel()
                },
                onToggleBeauty = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleBeautyPanel()
                },
                onHoldCompare = { isPressing ->
                    viewModel.setComparingOriginal(isPressing)
                },
                onResetFilters = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.resetAllFilters()
                    Toast.makeText(context, "Filters reset to Original", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        // 4. Timer Countdown Fullscreen Overlay
        uiState.countdownRemaining?.let { count ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$count",
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Tap anywhere to cancel",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // 5. Comparison Indicator Overlay
        SplitCompareOverlay(
            isComparing = uiState.isComparingOriginal
        )

        // 6. Unified Bottom Layout (Clean vertical sequencing with NO overlapping items)
        AnimatedVisibility(
            visible = uiState.isControlsVisible || uiState.isRecordingVideo,
            enter = fadeIn(tween(200)) + slideInVertically { it },
            exit = fadeOut(tween(200)) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Proper Horizontal Filter Intensity Adjustment Slider
                if ((uiState.selectedPreset.lutAssetPath != null || uiState.selectedPreset.isHDEnhance) && !uiState.isRecordingVideo) {
                    FilterIntensitySliderBar(
                        presetName = uiState.selectedPreset.name,
                        presetId = uiState.selectedPreset.id,
                        intensity = uiState.selectedPreset.intensity,
                        isHDEnhance = uiState.selectedPreset.isHDEnhance,
                        hdParameters = uiState.hdParameters,
                        isFavorite = uiState.favoriteFilterIds.contains(uiState.selectedPreset.id),
                        onToggleFavorite = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleFavorite(uiState.selectedPreset.id)
                        },
                        onOpenHdTune = {
                            viewModel.toggleHdPanel()
                        },
                        onLiveIntensityChange = { viewModel.updateLiveIntensity(it) },
                        onIntensityChangeFinished = { viewModel.updatePresetIntensity(it) },
                        onLiveHdOptionChange = { id, value -> viewModel.updateLiveHdOption(id, value) },
                        onHdOptionChangeFinished = { id, value -> viewModel.updateHdOption(id, value) },
                        onResetIntensity = { viewModel.updatePresetIntensity(1.0f) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 0.5x / 1x / 2x Zoom Control Bar (0.5x default for both cameras)
                if (!uiState.isRecordingVideo) {
                    ZoomControlBar(
                        currentZoom = uiState.zoomRatio,
                        onSelectZoom = { zoom ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            cameraManager.setZoomRatio(zoom)
                            viewModel.setZoomRatio(zoom)
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Category Tabs Row (All, Favorites, Recent, Film, Moody, Warm, Cool, Trendy, Bright, Beauty)
                if (!uiState.isRecordingVideo) {
                    CategoryTabsRow(
                        selectedCategory = uiState.selectedCategory,
                        onSelectCategory = { category ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.selectCategory(category)
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Filter Carousel with Square Preview Views (Clean cards with preview swatch + name)
                FilterCarousel(
                    presets = uiState.displayedPresets,
                    selectedPreset = uiState.selectedPreset,
                    favoriteFilterIds = uiState.favoriteFilterIds,
                    thumbnails = uiState.filterThumbnails,
                    selectedCategory = uiState.selectedCategory,
                    isEnabled = !uiState.isRecordingVideo,
                    onSelectPreset = { preset ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectPreset(preset)
                    },
                    onToggleFavorite = { presetId ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleFavorite(presetId)
                    },
                    onCategoryScrolled = { category ->
                        viewModel.setScrolledCategory(category)
                    },
                    onHoldPreset = { isHolding ->
                        viewModel.setComparingOriginal(isHolding)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Capture Controls Row (Gallery, Shutter Button, Switch Camera)
                CaptureControlsRow(
                    isRecording = uiState.isRecordingVideo,
                    lastCapturedThumbnail = uiState.lastCapturedThumbnail,
                    onPhotoCapture = { onShutterTrigger() },
                    onSwitchCamera = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cameraManager.switchCamera()
                        viewModel.setCameraFacing(cameraManager.isFrontCamera())
                    },
                    onStartVideoRecording = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cameraManager.videoRecordManager.startRecording(
                            onRecordingStarted = { viewModel.onRecordingStarted() },
                            onDurationTick = { sec -> viewModel.onRecordingTick(sec) },
                            onRecordingFinished = { uri, thumbnail ->
                                viewModel.onRecordingFinished(uri, thumbnail)
                                coroutineScope.launch {
                                    Toast.makeText(context, "Video saved to Gallery!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onError = { err ->
                                coroutineScope.launch {
                                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                    onStopVideoRecording = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cameraManager.videoRecordManager.stopRecording()
                    },
                    onOpenGallery = {
                        viewModel.openGallery()
                    }
                )
            }
        }

        // 7. GLSL Shader Effects Panel
        AnimatedVisibility(
            visible = uiState.showEffectsPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ShaderEffectsBottomSheet(
                effects = uiState.shaderEffects,
                onToggleEffect = { viewModel.toggleShaderEffect(it) },
                onIntensityChange = { id, value -> viewModel.updateShaderEffectIntensity(id, value) },
                onClose = { viewModel.toggleEffectsPanel() }
            )
        }

        // 8. Beauty Effects Panel
        AnimatedVisibility(
            visible = uiState.showBeautyPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BeautyEffectsBottomSheet(
                effects = uiState.beautyEffects,
                onToggleEffect = { viewModel.toggleBeautyEffect(it) },
                onIntensityChange = { id, value -> viewModel.updateBeautyEffectIntensity(id, value) },
                onClose = { viewModel.toggleBeautyPanel() }
            )
        }

        // 9. HD Pro Enhance Panel
        AnimatedVisibility(
            visible = uiState.showHdPanel,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            HDEnhanceBottomSheet(
                hdParameters = uiState.hdParameters,
                onOptionLiveChange = { id, value -> viewModel.updateLiveHdOption(id, value) },
                onOptionChangeFinished = { id, value -> viewModel.updateHdOption(id, value) },
                onResetAll = { viewModel.resetHdParameters() },
                onClose = { viewModel.toggleHdPanel() }
            )
        }
    }
}

@Composable
fun TopBarControls(
    flashMode: FlashMode,
    timerMode: TimerMode,
    isFrontCamera: Boolean,
    onCycleFlash: () -> Unit,
    onCycleTimer: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleEffects: () -> Unit,
    onToggleBeauty: () -> Unit,
    onHoldCompare: (Boolean) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flash Button
        IconButton(
            onClick = onCycleFlash,
            enabled = !isFrontCamera,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = when (flashMode) {
                    FlashMode.OFF -> Icons.Default.FlashOff
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                },
                contentDescription = "Flash Mode",
                tint = when (flashMode) {
                    FlashMode.ON -> Color.Yellow
                    FlashMode.AUTO -> MaterialTheme.colorScheme.primary
                    FlashMode.OFF -> Color.White
                },
                modifier = Modifier.size(19.dp)
            )
        }

        // Timer Button
        IconButton(
            onClick = onCycleTimer,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = when (timerMode) {
                    TimerMode.OFF -> Icons.Default.TimerOff
                    TimerMode.SEC_3 -> Icons.Default.Timer3
                    TimerMode.SEC_5 -> Icons.Default.Timer
                    TimerMode.SEC_10 -> Icons.Default.Timer10
                },
                contentDescription = "Timer: ${timerMode.label}",
                tint = if (timerMode != TimerMode.OFF) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        // Hold-to-Compare Button
        val compareInteraction = remember { MutableInteractionSource() }
        val isComparePressed by compareInteraction.collectIsPressedAsState()
        LaunchedEffect(isComparePressed) {
            onHoldCompare(isComparePressed)
        }

        IconButton(
            onClick = {},
            interactionSource = compareInteraction,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isComparePressed) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Compare,
                contentDescription = "Hold to Compare",
                tint = if (isComparePressed) Color.Black else Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        // Reset Filter Button
        IconButton(
            onClick = onResetFilters,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = "Reset Filters",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }

        // Beauty Filter Panel Button
        IconButton(
            onClick = onToggleBeauty,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = "Beauty Filters",
                tint = Color(0xFFFF80AB),
                modifier = Modifier.size(19.dp)
            )
        }

        // Shader Effects Panel Button
        IconButton(
            onClick = onToggleEffects,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "GLSL Effects",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        }

        // Switch Camera Flip Button
        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
fun FilterIntensitySliderBar(
    modifier: Modifier = Modifier,
    presetName: String,
    presetId: String,
    intensity: Float,
    isHDEnhance: Boolean = false,
    hdParameters: HdParameters = HdParameters(),
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenHdTune: () -> Unit = {},
    onLiveIntensityChange: (Float) -> Unit,
    onIntensityChangeFinished: (Float) -> Unit,
    onLiveHdOptionChange: (String, Float) -> Unit = { _, _ -> },
    onHdOptionChangeFinished: (String, Float) -> Unit = { _, _ -> },
    onResetIntensity: () -> Unit
) {
    val hdItems = remember(hdParameters) { hdParameters.toAdjustmentList() }
    var selectedHdOptionId by remember(presetId) { mutableStateOf("master") }
    val currentItem = hdItems.find { it.id == selectedHdOptionId } ?: hdItems.first()

    var localIntensity by remember(presetId, intensity, selectedHdOptionId, isHDEnhance) {
        mutableFloatStateOf(if (isHDEnhance) currentItem.value else intensity)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isHDEnhance) Icons.Default.AutoAwesome else Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHDEnhance) "$presetName • ${currentItem.name}" else presetName,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (presetId != "normal" && !isHDEnhance) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Unfavorite" else "Favorite",
                                tint = if (isFavorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.65f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else if (isHDEnhance) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenHdTune() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Tune HD",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "All Options",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayText = if (isHDEnhance) {
                        currentItem.displayFormat(localIntensity)
                    } else {
                        "${(localIntensity * 100).toInt()}%"
                    }
                    Text(
                        text = displayText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            val defaultVal = if (isHDEnhance) currentItem.defaultValue else 1.0f
                            localIntensity = defaultVal
                            if (isHDEnhance) {
                                onHdOptionChangeFinished(selectedHdOptionId, defaultVal)
                            } else {
                                onResetIntensity()
                            }
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset",
                            tint = Color.LightGray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            if (isHDEnhance) {
                Spacer(modifier = Modifier.height(4.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(hdItems) { item ->
                        val isSelected = item.id == selectedHdOptionId
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedHdOptionId = item.id
                                    localIntensity = item.value
                                }
                        ) {
                            Text(
                                text = item.name,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Slider(
                value = localIntensity,
                onValueChange = {
                    localIntensity = it
                    if (isHDEnhance) {
                        onLiveHdOptionChange(selectedHdOptionId, it)
                    } else {
                        onLiveIntensityChange(it)
                    }
                },
                onValueChangeFinished = {
                    if (isHDEnhance) {
                        onHdOptionChangeFinished(selectedHdOptionId, localIntensity)
                    } else {
                        onIntensityChangeFinished(localIntensity)
                    }
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
    }
}

@Composable
fun ZoomControlBar(
    currentZoom: Float,
    onSelectZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomLevels = listOf(0.5f, 1.0f, 2.0f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        zoomLevels.forEach { zoom ->
            val isSelected = kotlin.math.abs(currentZoom - zoom) < 0.2f
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelectZoom(zoom) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (zoom == 0.5f) ".5x" else "${zoom.toInt()}x",
                    color = if (isSelected) Color.Black else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CategoryTabsRow(
    selectedCategory: FilterCategory,
    onSelectCategory: (FilterCategory) -> Unit
) {
    val tabListState = rememberLazyListState()

    // Smoothly scroll the tab bar so the active category tab is centered/visible
    LaunchedEffect(selectedCategory) {
        val catIndex = FilterCategory.entries.indexOf(selectedCategory)
        if (catIndex >= 0) {
            tabListState.animateScrollToItem((catIndex - 1).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = tabListState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(FilterCategory.entries.toTypedArray()) { category ->
            val isSelected = category == selectedCategory
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isSelected) Color.White else Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectCategory(category) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    when (category) {
                        FilterCategory.FAVORITES -> {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color(0xFFFF4081),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        FilterCategory.RECENT -> {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        FilterCategory.BEAUTY -> {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else Color(0xFFFF80AB),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        else -> {}
                    }
                    Text(
                        text = category.displayName,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else Color.White
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterCarousel(
    presets: List<FilterPreset>,
    selectedPreset: FilterPreset,
    favoriteFilterIds: Set<String>,
    thumbnails: Map<String, Bitmap> = emptyMap(),
    selectedCategory: FilterCategory,
    isEnabled: Boolean,
    onSelectPreset: (FilterPreset) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCategoryScrolled: (FilterCategory) -> Unit = {},
    onHoldPreset: (Boolean) -> Unit = {}
) {
    if (presets.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            contentAlignment = Alignment.Center
        ) {
            val emptyMessage = when (selectedCategory) {
                FilterCategory.FAVORITES -> "No favorite filters yet\nTap the ❤️ on any filter to save it here!"
                FilterCategory.RECENT -> "No recent filters yet\nSelect any filter to use it!"
                FilterCategory.BEAUTY -> "Tap Beauty button above to configure beauty filters"
                else -> "No filters found in this category"
            }
            Text(
                text = emptyMessage,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()

    // Smooth scroll to center the selected preset when selected by user tap
    LaunchedEffect(selectedPreset.id, presets) {
        if (!listState.isScrollInProgress) {
            val selectedIndex = presets.indexOfFirst { it.id == selectedPreset.id }
            if (selectedIndex >= 0) {
                listState.animateScrollToItem(
                    index = (selectedIndex - 1).coerceAtLeast(0)
                )
            }
        }
    }

    // Scroll carousel to the first preset of selectedCategory when a category tab is tapped
    LaunchedEffect(selectedCategory) {
        if (!listState.isScrollInProgress) {
            val targetIndex = when (selectedCategory) {
                FilterCategory.ALL -> 0
                FilterCategory.FAVORITES, FilterCategory.RECENT, FilterCategory.BEAUTY -> -1
                else -> presets.indexOfFirst { it.category == selectedCategory }
            }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    // Observe live scrolling and sync the active Category Tab with the centered preset
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) null
            else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centerItem = visibleItems.minByOrNull {
                    kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                }
                centerItem?.index
            }
        }.distinctUntilChanged().collect { centerIndex ->
            if (centerIndex != null && listState.isScrollInProgress) {
                val preset = presets.getOrNull(centerIndex)
                if (preset != null) {
                    val category = if (preset.id == "normal" || preset.id == "hd") {
                        FilterCategory.ALL
                    } else {
                        preset.category
                    }
                    onCategoryScrolled(category)
                }
            }
        }
    }

    LazyRow(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(presets, key = { _, it -> it.id }) { index, preset ->
            val isSelected = preset.id == selectedPreset.id
            val isFav = favoriteFilterIds.contains(preset.id)
            val scaleAnim by animateFloatAsState(
                targetValue = if (isSelected) 1.08f else 0.95f,
                animationSpec = spring(dampingRatio = 0.75f, stiffness = 600f),
                label = "carousel_scale"
            )

            val colors = remember(preset.gradientColors) {
                preset.gradientColors.map { Color(it) }
            }

            val thumbBitmap = thumbnails[preset.id]

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scaleAnim)
                    .clip(RoundedCornerShape(14.dp))
                    .pointerInput(preset.id, isEnabled) {
                        detectTapGestures(
                            onTap = {
                                if (isEnabled) {
                                    onSelectPreset(preset)
                                    coroutineScope.launch {
                                        listState.animateScrollToItem((index - 1).coerceAtLeast(0))
                                    }
                                }
                            },
                            onPress = {
                                try {
                                    onHoldPreset(true)
                                    awaitRelease()
                                } finally {
                                    onHoldPreset(false)
                                }
                            }
                        )
                    }
                    .padding(2.dp)
            ) {
                // Square Preview View Card with Live / Pre-rendered Filter Thumbnail
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(2.5.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (thumbBitmap != null) {
                                Modifier.background(Color.Black)
                            } else {
                                Modifier.background(Brush.linearGradient(colors))
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap.asImageBitmap(),
                            contentDescription = preset.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (preset.id == "normal") {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (preset.isHDEnhance) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "HD ✨",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    if (preset.id != "normal") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .clickable { onToggleFavorite(preset.id) }
                        ) {
                            if (isFav) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Favorite",
                                    tint = Color(0xFFFF4081),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = preset.name,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    fontSize = if (isSelected) 11.sp else 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(62.dp)
                )
            }
        }
    }
}

@Composable
fun CaptureControlsRow(
    isRecording: Boolean,
    lastCapturedThumbnail: Bitmap?,
    onPhotoCapture: () -> Unit,
    onSwitchCamera: () -> Unit,
    onStartVideoRecording: () -> Unit,
    onStopVideoRecording: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Gallery Button / Thumbnail Preview
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onOpenGallery() },
            contentAlignment = Alignment.Center
        ) {
            if (lastCapturedThumbnail != null) {
                Image(
                    bitmap = lastCapturedThumbnail.asImageBitmap(),
                    contentDescription = "Gallery",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // 2. Shutter Button (Tap for Photo, Long-Press for Video)
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val shutterScale by animateFloatAsState(
            targetValue = if (isPressed || isRecording) 1.18f else 1.0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "shutter_scale"
        )

        Box(
            modifier = Modifier
                .size(76.dp)
                .scale(shutterScale)
                .border(
                    width = 4.dp,
                    color = if (isRecording) Color.Red else Color.White,
                    shape = CircleShape
                )
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (isRecording) Color.Red else Color.White)
                .pointerInput(isRecording) {
                    detectTapGestures(
                        onTap = {
                            if (isRecording) {
                                onStopVideoRecording()
                            } else {
                                onPhotoCapture()
                            }
                        },
                        onLongPress = {
                            if (!isRecording) {
                                onStartVideoRecording()
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (isRecording) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // 3. Switch / Flip Camera Button
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onSwitchCamera() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun BeautyEffectsBottomSheet(
    effects: List<BeautyEffect>,
    onToggleEffect: (String) -> Unit,
    onIntensityChange: (String, Float) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1C24).copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = Color(0xFFFF80AB)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Beauty Enhancements",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            effects.forEach { effect ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = effect.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = effect.isEnabled,
                            onCheckedChange = { onToggleEffect(effect.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFFFF80AB)
                            )
                        )
                    }

                    if (effect.isEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(effect.intensity * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                modifier = Modifier.width(36.dp)
                            )
                            Slider(
                                value = effect.intensity,
                                onValueChange = { onIntensityChange(effect.id, it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFFFF80AB)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShaderEffectsBottomSheet(
    effects: List<ShaderEffect>,
    onToggleEffect: (String) -> Unit,
    onIntensityChange: (String, Float) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E24).copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "GLSL Post-Processing FX",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            effects.forEach { effect ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = effect.name,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = effect.isEnabled,
                            onCheckedChange = { onToggleEffect(effect.id) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    if (effect.isEnabled) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${(effect.intensity * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                modifier = Modifier.width(36.dp)
                            )
                            Slider(
                                value = effect.intensity,
                                onValueChange = { onIntensityChange(effect.id, it) },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HDEnhanceBottomSheet(
    hdParameters: HdParameters,
    onOptionLiveChange: (String, Float) -> Unit,
    onOptionChangeFinished: (String, Float) -> Unit,
    onResetAll: () -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF18171E).copy(alpha = 0.98f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "HD Pro Customization",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onResetAll) {
                        Text(
                            text = "Reset All",
                            color = Color(0xFFFF80AB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val items = hdParameters.toAdjustmentList()
            items.forEach { item ->
                var localVal by remember(item.id, item.value) { mutableFloatStateOf(item.value) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = item.displayFormat(localVal),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Slider(
                        value = localVal,
                        onValueChange = {
                            localVal = it
                            onOptionLiveChange(item.id, it)
                        },
                        onValueChangeFinished = {
                            onOptionChangeFinished(item.id, localVal)
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CameraPermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhotoCamera,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Live Filter Camera",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Camera and microphone permissions are required to apply real-time 3D LUTs and GLSL shader effects while capturing photos and videos.",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                Text(
                    text = "Grant Permissions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(0.75f)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open App Settings",
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }
}
