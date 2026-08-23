package com.techquantum.livefiltercamera.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vignette
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techquantum.livefiltercamera.camera.CameraManager
import com.techquantum.livefiltercamera.camera.FilterEngine
import com.techquantum.livefiltercamera.camera.FlashMode
import com.techquantum.livefiltercamera.model.FilterPreset
import com.techquantum.livefiltercamera.model.ShaderEffect
import jp.co.cyberagent.android.gpuimage.GPUImageView
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

    // Camera and Filter Setup
    val gpuImageView = remember { GPUImageView(context) }
    val filterEngine = remember { FilterEngine(context, gpuImageView) }
    val cameraManager = remember { CameraManager(context, lifecycleOwner, filterEngine) }

    // 3D Flip Animation State
    var cameraFlipRotation by remember { mutableFloatStateOf(0f) }
    val animatedFlipRotation by animateFloatAsState(
        targetValue = cameraFlipRotation,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "camera_flip_anim"
    )

    // Zoom Pinch State
    var currentZoom by remember { mutableFloatStateOf(0f) }

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
            }
        )
    }

    DisposableEffect(lifecycleOwner) {
        cameraManager.startCamera()
        onDispose {
            cameraManager.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Tap to show UI & Double-tap to flip camera
                detectTapGestures(
                    onTap = {
                        viewModel.onUserInteraction()
                    },
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cameraFlipRotation += 180f
                        cameraManager.switchCamera()
                        viewModel.setCameraFacing(cameraManager.isFrontCamera())
                    }
                )
            }
            .pointerInput(Unit) {
                // Pinch to zoom gesture
                detectTransformGestures { _, _, zoom, _ ->
                    currentZoom = (currentZoom + (zoom - 1f) * 0.75f).coerceIn(0f, 1f)
                    cameraManager.setLinearZoom(currentZoom)
                    viewModel.setZoomRatio(currentZoom)
                }
            }
    ) {
        // 1. Live Filter GPU Preview with 3D Flip Transform
        AndroidView(
            factory = { gpuImageView },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = animatedFlipRotation
                    cameraDistance = 12 * density
                }
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

        // Top & Bottom Vignette Gradient Overlays for UI Readability
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
                                colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )
            }
        }

        // 2. Video Recording Top Banner (Pulsing Red Dot + Timer)
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
                    text = String.format("%02d:%02d / 01:00", minutes, seconds),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // 3. Top Bar Camera Controls
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isRecordingVideo,
            enter = fadeIn(tween(200)) + slideInVertically { -it },
            exit = fadeOut(tween(200)) + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopBarControls(
                flashMode = uiState.flashMode,
                isFrontCamera = uiState.isFrontCamera,
                onCycleFlash = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val newMode = viewModel.cycleFlashMode()
                    cameraManager.setFlashMode(newMode)
                },
                onSwitchCamera = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    cameraFlipRotation += 180f
                    cameraManager.switchCamera()
                    viewModel.setCameraFacing(cameraManager.isFrontCamera())
                },
                onToggleEffects = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleEffectsPanel()
                },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // 4. Side Intensity Slider (Auto-hiding overlay)
        AnimatedVisibility(
            visible = uiState.isIntensitySliderVisible && uiState.selectedPreset.lutAssetPath != null,
            enter = fadeIn(tween(200)) + scaleIn(),
            exit = fadeOut(tween(200)) + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.65f)),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(uiState.selectedPreset.intensity * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .height(160.dp)
                            .width(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = uiState.selectedPreset.intensity,
                            onValueChange = { viewModel.updatePresetIntensity(it) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .graphicsLayer {
                                    rotationZ = 270f
                                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                                }
                                .width(160.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Intensity",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 5. Quick Shader Effect Chips Row (Grain, Vignette, Fade, Bloom)
        AnimatedVisibility(
            visible = uiState.isControlsVisible && !uiState.isRecordingVideo,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 190.dp)
        ) {
            ShaderQuickTogglesRow(
                effects = uiState.shaderEffects,
                onToggle = { id ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.toggleShaderEffect(id)
                }
            )
        }

        // 6. Bottom Controls: Filter Carousel + Shutter & Gallery Controls
        AnimatedVisibility(
            visible = uiState.isControlsVisible || uiState.isRecordingVideo,
            enter = fadeIn(tween(200)) + slideInVertically { it },
            exit = fadeOut(tween(200)) + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Snapchat-Style Filter Carousel
                FilterCarousel(
                    presets = uiState.presets,
                    selectedPreset = uiState.selectedPreset,
                    isEnabled = !uiState.isRecordingVideo,
                    onSelectPreset = { preset ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.selectPreset(preset)
                    }
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Capture Controls Row (Gallery Button, Shutter Button, FX Toggle)
                CaptureControlsRow(
                    isRecording = uiState.isRecordingVideo,
                    lastCapturedThumbnail = uiState.lastCapturedThumbnail,
                    lastCapturedUri = uiState.lastCapturedUri,
                    onPhotoCapture = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                    },
                    onStartVideoRecording = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        cameraManager.videoRecordManager.startRecording(
                            onRecordingStarted = {
                                viewModel.onRecordingStarted()
                            },
                            onDurationTick = { sec ->
                                viewModel.onRecordingTick(sec)
                            },
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
                        val galleryIntent = Intent(Intent.ACTION_VIEW).apply {
                            type = "image/*"
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(galleryIntent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "No gallery app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // 7. Expandable Shader Effects Panel (Full Adjustments)
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
    }
}

@Composable
fun TopBarControls(
    flashMode: FlashMode,
    isFrontCamera: Boolean,
    onCycleFlash: () -> Unit,
    onSwitchCamera: () -> Unit,
    onToggleEffects: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flash Mode Cycle Button (Off -> On -> Auto)
        IconButton(
            onClick = onCycleFlash,
            enabled = !isFrontCamera,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
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
                modifier = Modifier.size(22.dp)
            )
        }

        // Shader Effects Panel Button
        IconButton(
            onClick = onToggleEffects,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "GLSL Effects",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Switch Camera Flip Button
        IconButton(
            onClick = onSwitchCamera,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(
                imageVector = Icons.Default.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ShaderQuickTogglesRow(
    effects: List<ShaderEffect>,
    onToggle: (String) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        effects.forEach { effect ->
            val icon = when (effect.id) {
                "grain" -> Icons.Default.Grain
                "vignette" -> Icons.Default.Vignette
                "fade" -> Icons.Default.Lens
                "bloom" -> Icons.Default.WbSunny
                else -> Icons.Default.AutoAwesome
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (effect.isEnabled) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (effect.isEnabled) Color.White else Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggle(effect.id) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = effect.name,
                        tint = if (effect.isEnabled) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = effect.name.split(" ").first(),
                        fontSize = 11.sp,
                        fontWeight = if (effect.isEnabled) FontWeight.Bold else FontWeight.Normal,
                        color = if (effect.isEnabled) Color.Black else Color.White
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
    isEnabled: Boolean,
    onSelectPreset: (FilterPreset) -> Unit
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LazyRow(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(presets, key = { _, it -> it.id }) { index, preset ->
            val isSelected = preset.id == selectedPreset.id
            val scaleAnim by animateFloatAsState(
                targetValue = if (isSelected) 1.15f else 0.95f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "carousel_scale"
            )

            val presetGradient = when (preset.id) {
                "film_warm" -> listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                "fade_cool" -> listOf(Color(0xFF00BCD4), Color(0xFF3F51B5))
                "cinema" -> listOf(Color(0xFF009688), Color(0xFFFF7043))
                "vintage" -> listOf(Color(0xFF8D6E63), Color(0xFFFFD54F))
                "moody" -> listOf(Color(0xFF37474F), Color(0xFF212121))
                else -> listOf(Color.LightGray, Color.DarkGray)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scaleAnim)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = isEnabled) { onSelectPreset(preset) }
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        )
                        .padding(3.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(presetGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    if (preset.id == "normal") {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preset.name,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = if (isSelected) 12.sp else 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun CaptureControlsRow(
    isRecording: Boolean,
    lastCapturedThumbnail: android.graphics.Bitmap?,
    lastCapturedUri: Uri?,
    onPhotoCapture: () -> Unit,
    onStartVideoRecording: () -> Unit,
    onStopVideoRecording: () -> Unit,
    onOpenGallery: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
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
                    contentDescription = "Last captured",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Photo,
                    contentDescription = "Gallery",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 2. Shutter Button (Tap for Photo, Long-Press for Video)
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val shutterScale by animateFloatAsState(
            targetValue = if (isPressed || isRecording) 1.2f else 1.0f,
            animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
            label = "shutter_scale"
        )

        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(shutterScale)
                .border(
                    width = 4.dp,
                    color = if (isRecording) Color.Red else Color.White,
                    shape = CircleShape
                )
                .padding(6.dp)
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
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 3. Right Action Spacer (Symmetrical layout)
        Box(modifier = Modifier.size(52.dp))
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
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E1E24).copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            effects.forEach { effect ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = effect.name,
                            color = Color.White,
                            fontSize = 14.sp,
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
                                fontSize = 12.sp,
                                modifier = Modifier.width(40.dp)
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
