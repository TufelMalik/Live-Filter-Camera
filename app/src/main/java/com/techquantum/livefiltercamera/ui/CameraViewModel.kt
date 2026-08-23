package com.techquantum.livefiltercamera.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techquantum.livefiltercamera.camera.FlashMode
import com.techquantum.livefiltercamera.lut.LutLoader
import com.techquantum.livefiltercamera.model.FilterPreset
import com.techquantum.livefiltercamera.model.FilterRepository
import com.techquantum.livefiltercamera.model.ShaderEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val presets: List<FilterPreset> = FilterRepository.presets,
    val selectedPreset: FilterPreset = FilterRepository.presets.first(),
    val shaderEffects: List<ShaderEffect> = FilterRepository.getDefaultShaderEffects(),
    val isFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val showEffectsPanel: Boolean = false,
    val isLoadingLut: Boolean = false,
    val zoomRatio: Float = 0.0f,
    // Photo Capture States
    val isCapturingPhoto: Boolean = false,
    val showShutterFlash: Boolean = false,
    val lastCapturedThumbnail: Bitmap? = null,
    val lastCapturedUri: Uri? = null,
    // Video Recording States
    val isRecordingVideo: Boolean = false,
    val recordingDurationSec: Int = 0,
    // UI Visibility & Auto-hide
    val isControlsVisible: Boolean = true,
    val isIntensitySliderVisible: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var onPresetChangedListener: ((FilterPreset, Bitmap?) -> Unit)? = null
    private var onLutIntensityChangedListener: ((Float) -> Unit)? = null
    private var onShaderEffectChangedListener: ((String, Boolean, Float) -> Unit)? = null

    private var sliderAutoHideJob: Job? = null
    private var controlsAutoHideJob: Job? = null

    init {
        resetControlsAutoHideTimer()
    }

    fun setFilterListeners(
        onPresetChanged: (FilterPreset, Bitmap?) -> Unit,
        onLutIntensityChanged: (Float) -> Unit,
        onShaderEffectChanged: (String, Boolean, Float) -> Unit
    ) {
        this.onPresetChangedListener = onPresetChanged
        this.onLutIntensityChangedListener = onLutIntensityChanged
        this.onShaderEffectChangedListener = onShaderEffectChanged
    }

    fun selectPreset(preset: FilterPreset) {
        if (_uiState.value.isRecordingVideo) return // Locked during video recording
        _uiState.update { 
            it.copy(
                selectedPreset = preset,
                isLoadingLut = true,
                isIntensitySliderVisible = preset.lutAssetPath != null
            ) 
        }
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()

        viewModelScope.launch {
            val bitmap = if (preset.lutAssetPath != null) {
                LutLoader.loadLutBitmap(getApplication(), preset.lutAssetPath)
            } else {
                null
            }
            _uiState.update { it.copy(isLoadingLut = false) }
            onPresetChangedListener?.invoke(preset, bitmap)
        }
    }

    fun updatePresetIntensity(intensity: Float) {
        val updated = _uiState.value.selectedPreset.copy(intensity = intensity)
        _uiState.update { it.copy(selectedPreset = updated, isIntensitySliderVisible = true) }
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()
        onLutIntensityChangedListener?.invoke(intensity)
    }

    fun toggleShaderEffect(effectId: String) {
        resetControlsAutoHideTimer()
        val currentEffects = _uiState.value.shaderEffects.map { effect ->
            if (effect.id == effectId) {
                val updated = effect.copy(isEnabled = !effect.isEnabled)
                onShaderEffectChangedListener?.invoke(updated.id, updated.isEnabled, updated.intensity)
                updated
            } else {
                effect
            }
        }
        _uiState.update { it.copy(shaderEffects = currentEffects) }
    }

    fun updateShaderEffectIntensity(effectId: String, intensity: Float) {
        resetControlsAutoHideTimer()
        val currentEffects = _uiState.value.shaderEffects.map { effect ->
            if (effect.id == effectId) {
                val updated = effect.copy(intensity = intensity)
                onShaderEffectChangedListener?.invoke(updated.id, updated.isEnabled, updated.intensity)
                updated
            } else {
                effect
            }
        }
        _uiState.update { it.copy(shaderEffects = currentEffects) }
    }

    fun cycleFlashMode(): FlashMode {
        resetControlsAutoHideTimer()
        val nextMode = when (_uiState.value.flashMode) {
            FlashMode.OFF -> FlashMode.ON
            FlashMode.ON -> FlashMode.AUTO
            FlashMode.AUTO -> FlashMode.OFF
        }
        _uiState.update { it.copy(flashMode = nextMode) }
        return nextMode
    }

    fun setCameraFacing(isFront: Boolean) {
        resetControlsAutoHideTimer()
        _uiState.update { 
            it.copy(
                isFrontCamera = isFront,
                flashMode = if (isFront && it.flashMode == FlashMode.ON) FlashMode.OFF else it.flashMode
            ) 
        }
    }

    fun setZoomRatio(zoom: Float) {
        _uiState.update { it.copy(zoomRatio = zoom.coerceIn(0f, 1f)) }
    }

    fun toggleEffectsPanel() {
        resetControlsAutoHideTimer()
        _uiState.update { it.copy(showEffectsPanel = !it.showEffectsPanel) }
    }

    fun onUserInteraction() {
        if (!_uiState.value.isControlsVisible) {
            _uiState.update { it.copy(isControlsVisible = true) }
        }
        resetControlsAutoHideTimer()
    }

    fun triggerShutterAnimation() {
        _uiState.update { it.copy(showShutterFlash = true, isCapturingPhoto = true) }
        viewModelScope.launch {
            delay(120)
            _uiState.update { it.copy(showShutterFlash = false, isCapturingPhoto = false) }
        }
    }

    fun onMediaSaved(uri: Uri, thumbnail: Bitmap?) {
        _uiState.update { 
            it.copy(
                lastCapturedUri = uri,
                lastCapturedThumbnail = thumbnail ?: it.lastCapturedThumbnail,
                isCapturingPhoto = false
            ) 
        }
    }

    fun onRecordingStarted() {
        _uiState.update { it.copy(isRecordingVideo = true, recordingDurationSec = 0) }
    }

    fun onRecordingTick(seconds: Int) {
        _uiState.update { it.copy(recordingDurationSec = seconds) }
    }

    fun onRecordingFinished(uri: Uri, thumbnail: Bitmap?) {
        _uiState.update { 
            it.copy(
                isRecordingVideo = false,
                recordingDurationSec = 0,
                lastCapturedUri = uri,
                lastCapturedThumbnail = thumbnail ?: it.lastCapturedThumbnail
            ) 
        }
    }

    private fun resetSliderAutoHideTimer() {
        sliderAutoHideJob?.cancel()
        sliderAutoHideJob = viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(isIntensitySliderVisible = false) }
        }
    }

    private fun resetControlsAutoHideTimer() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = viewModelScope.launch {
            delay(4000)
            if (!_uiState.value.showEffectsPanel && !_uiState.value.isRecordingVideo) {
                _uiState.update { it.copy(isControlsVisible = false, isIntensitySliderVisible = false) }
            }
        }
    }
}
