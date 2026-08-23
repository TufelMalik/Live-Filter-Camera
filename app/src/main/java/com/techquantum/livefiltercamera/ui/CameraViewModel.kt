package com.techquantum.livefiltercamera.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.techquantum.livefiltercamera.camera.FlashMode
import com.techquantum.livefiltercamera.lut.LutLoader
import com.techquantum.livefiltercamera.model.BeautyEffect
import com.techquantum.livefiltercamera.model.BeautyRepository
import com.techquantum.livefiltercamera.model.FilterCategory
import com.techquantum.livefiltercamera.model.FilterPreset
import com.techquantum.livefiltercamera.model.FilterRepository
import com.techquantum.livefiltercamera.model.ShaderEffect
import com.techquantum.livefiltercamera.model.FilterPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TimerMode(val seconds: Int, val label: String) {
    OFF(0, "Off"),
    SEC_3(3, "3s"),
    SEC_5(5, "5s"),
    SEC_10(10, "10s")
}

data class CameraUiState(
    val presets: List<FilterPreset> = FilterRepository.presets,
    val selectedCategory: FilterCategory = FilterCategory.ALL,
    val selectedPreset: FilterPreset = FilterRepository.presets.first(),
    val favoriteFilterIds: Set<String> = emptySet(),
    val recentFilterIds: List<String> = emptyList(),
    val shaderEffects: List<ShaderEffect> = FilterRepository.getDefaultShaderEffects(),
    val beautyEffects: List<BeautyEffect> = BeautyRepository.getDefaultBeautyEffects(),
    val isFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val timerMode: TimerMode = TimerMode.OFF,
    val countdownRemaining: Int? = null,
    val showEffectsPanel: Boolean = false,
    val showBeautyPanel: Boolean = false,
    val showHdPanel: Boolean = false,
    val showGalleryScreen: Boolean = false,
    val hdParameters: com.techquantum.livefiltercamera.model.HdParameters = com.techquantum.livefiltercamera.model.HdParameters(),
    val isLoadingLut: Boolean = false,
    val isPreloadingLuts: Boolean = true,
    val zoomRatio: Float = 0.5f,
    // Photo Capture & Burst States
    val isCapturingPhoto: Boolean = false,
    val isBurstCapturing: Boolean = false,
    val burstProgress: Pair<Int, Int>? = null,
    val showShutterFlash: Boolean = false,
    val lastCapturedThumbnail: Bitmap? = null,
    val lastCapturedUri: Uri? = null,
    // Video Recording States
    val isRecordingVideo: Boolean = false,
    val recordingDurationSec: Int = 0,
    // Live Comparison (Before / After)
    val isComparingOriginal: Boolean = false,
    val filterThumbnails: Map<String, Bitmap> = emptyMap(),
    // UI Visibility & Auto-hide
    val isControlsVisible: Boolean = true,
    val isIntensitySliderVisible: Boolean = false
) {
    val displayedPresets: List<FilterPreset>
        get() = when (selectedCategory) {
            FilterCategory.FAVORITES -> {
                presets.filter { favoriteFilterIds.contains(it.id) }
            }
            FilterCategory.RECENT -> {
                val presetMap = presets.associateBy { it.id }
                recentFilterIds.mapNotNull { presetMap[it] }
            }
            FilterCategory.BEAUTY -> {
                emptyList()
            }
            else -> {
                // Continuous slidable list from original till end across all categories
                presets
            }
        }
}

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val filterPreferences = FilterPreferences(application)

    private val _uiState = MutableStateFlow(
        CameraUiState(
            favoriteFilterIds = filterPreferences.getFavoriteFilterIds(),
            recentFilterIds = filterPreferences.getRecentFilterIds()
        )
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var onPresetChangedListener: ((FilterPreset, Bitmap?) -> Unit)? = null
    private var onLutIntensityChangedListener: ((Float) -> Unit)? = null
    private var onShaderEffectChangedListener: ((String, Boolean, Float) -> Unit)? = null
    private var onBeautyEffectChangedListener: ((String, Boolean, Float) -> Unit)? = null
    private var onHdOptionChangedListener: ((String, Float) -> Unit)? = null
    private var onBypassChangedListener: ((Boolean) -> Unit)? = null

    private var sliderAutoHideJob: Job? = null
    private var controlsAutoHideJob: Job? = null
    private var countdownJob: Job? = null

    init {
        resetControlsAutoHideTimer()
        // Initialize person face filter thumbnails on app start
        com.techquantum.livefiltercamera.lut.FilterThumbnailGenerator.initializePersonThumbnails(application)

        // Observe filter thumbnail updates
        viewModelScope.launch {
            com.techquantum.livefiltercamera.lut.FilterThumbnailGenerator.thumbnailsFlow.collect { thumbs ->
                _uiState.update { it.copy(filterThumbnails = thumbs) }
            }
        }

        // Eagerly preload ALL LUT bitmaps in the background at app start
        preloadAllLuts()
    }

    fun toggleFavorite(presetId: String) {
        if (presetId == "normal") return
        val updatedFavorites = filterPreferences.toggleFavorite(presetId)
        _uiState.update { it.copy(favoriteFilterIds = updatedFavorites) }
    }

    /**
     * Preloads all 30 LUT .cube files -> 512x512 bitmaps in parallel background threads.
     * After this completes, every filter click is an instant cache-hit (0ms latency).
     */
    private fun preloadAllLuts() {
        val allLutPaths = FilterRepository.presets
            .mapNotNull { it.lutAssetPath }
            .distinct()

        viewModelScope.launch(Dispatchers.Default) {
            LutLoader.preloadAll(getApplication(), allLutPaths)
            _uiState.update { it.copy(isPreloadingLuts = false) }
            // Render person face thumbnails once all LUTs are preloaded
            com.techquantum.livefiltercamera.lut.FilterThumbnailGenerator.initializePersonThumbnails(getApplication())
        }
    }

    fun setFilterListeners(
        onPresetChanged: (FilterPreset, Bitmap?) -> Unit,
        onLutIntensityChanged: (Float) -> Unit,
        onShaderEffectChanged: (String, Boolean, Float) -> Unit,
        onBeautyEffectChanged: (String, Boolean, Float) -> Unit,
        onHdOptionChanged: (String, Float) -> Unit,
        onBypassChanged: (Boolean) -> Unit
    ) {
        this.onPresetChangedListener = onPresetChanged
        this.onLutIntensityChangedListener = onLutIntensityChanged
        this.onShaderEffectChangedListener = onShaderEffectChanged
        this.onBeautyEffectChangedListener = onBeautyEffectChanged
        this.onHdOptionChangedListener = onHdOptionChanged
        this.onBypassChangedListener = onBypassChanged
    }

    fun selectCategory(category: FilterCategory) {
        resetControlsAutoHideTimer()
        _uiState.update { it.copy(selectedCategory = category) }
        if (category == FilterCategory.BEAUTY) {
            _uiState.update { it.copy(showBeautyPanel = true) }
        }
    }

    fun setScrolledCategory(category: FilterCategory) {
        if (_uiState.value.selectedCategory != category &&
            _uiState.value.selectedCategory != FilterCategory.FAVORITES &&
            _uiState.value.selectedCategory != FilterCategory.RECENT &&
            _uiState.value.selectedCategory != FilterCategory.BEAUTY
        ) {
            _uiState.update { it.copy(selectedCategory = category) }
        }
    }

    /**
     * Ultra-fast filter selection:
     * 1. Instantly update the UI state and add to recents
     * 2. Grab the pre-cached LUT bitmap or HD enhance filter
     * 3. Apply to GPU pipeline immediately
     * Falls back to async loading only if the cache somehow misses.
     */
    fun selectPreset(preset: FilterPreset) {
        if (_uiState.value.isRecordingVideo) return

        // Update recents list on selection
        val updatedRecents = if (preset.id != "normal") {
            filterPreferences.addRecentFilterId(preset.id)
        } else {
            _uiState.value.recentFilterIds
        }

        // Update UI state immediately for responsive feel
        _uiState.update {
            it.copy(
                selectedPreset = preset,
                recentFilterIds = updatedRecents,
                isIntensitySliderVisible = preset.lutAssetPath != null || preset.isHDEnhance
            )
        }
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()

        if (preset.isHDEnhance) {
            // HD Enhance Filter preset
            onPresetChangedListener?.invoke(preset, null)
        } else if (preset.lutAssetPath != null) {
            // Fast path: try cached bitmap first (instant, no coroutine needed)
            val cachedBitmap = LutLoader.getCachedLutBitmap(preset.lutAssetPath)
            if (cachedBitmap != null) {
                // INSTANT: Cache hit — apply filter with zero delay
                onPresetChangedListener?.invoke(preset, cachedBitmap)
            } else {
                // Slow fallback: load async (should only happen before preload completes)
                _uiState.update { it.copy(isLoadingLut = true) }
                viewModelScope.launch {
                    val bitmap = LutLoader.loadLutBitmap(getApplication(), preset.lutAssetPath)
                    _uiState.update { it.copy(isLoadingLut = false) }
                    onPresetChangedListener?.invoke(preset, bitmap)
                }
            }
        } else {
            // "Original" / no LUT — instant clear
            onPresetChangedListener?.invoke(preset, null)
        }
    }

    /**
     * Fast-path intensity update during active slider drag:
     * Dispatches directly to GPU pipeline without incurring full UI state recomposition overhead.
     */
    fun updateLiveIntensity(intensity: Float) {
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()
        onLutIntensityChangedListener?.invoke(intensity)
    }

    fun updatePresetIntensity(intensity: Float) {
        val updated = _uiState.value.selectedPreset.copy(intensity = intensity)
        _uiState.update { it.copy(selectedPreset = updated, isIntensitySliderVisible = true) }
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()
        onLutIntensityChangedListener?.invoke(intensity)
    }

    fun resetAllFilters() {
        val originalPreset = FilterRepository.presets.first()
        val defaultEffects = FilterRepository.getDefaultShaderEffects()
        val defaultBeauty = BeautyRepository.getDefaultBeautyEffects()
        val defaultHd = com.techquantum.livefiltercamera.model.HdParameters()

        _uiState.update {
            it.copy(
                selectedPreset = originalPreset,
                selectedCategory = FilterCategory.ALL,
                shaderEffects = defaultEffects,
                beautyEffects = defaultBeauty,
                hdParameters = defaultHd,
                isComparingOriginal = false,
                isIntensitySliderVisible = false,
                showBeautyPanel = false,
                showEffectsPanel = false,
                showHdPanel = false
            )
        }

        onPresetChangedListener?.invoke(originalPreset, null)
        onLutIntensityChangedListener?.invoke(1.0f)
        defaultEffects.forEach { effect ->
            onShaderEffectChangedListener?.invoke(effect.id, false, effect.intensity)
        }
        defaultBeauty.forEach { effect ->
            onBeautyEffectChangedListener?.invoke(effect.id, false, effect.intensity)
        }
        defaultHd.toAdjustmentList().forEach { item ->
            onHdOptionChangedListener?.invoke(item.id, item.value)
        }
        onBypassChangedListener?.invoke(false)
    }

    fun toggleBeautyEffect(effectId: String) {
        resetControlsAutoHideTimer()
        val currentEffects = _uiState.value.beautyEffects.map { effect ->
            if (effect.id == effectId) {
                val updated = effect.copy(isEnabled = !effect.isEnabled)
                onBeautyEffectChangedListener?.invoke(updated.id, updated.isEnabled, updated.intensity)
                updated
            } else {
                effect
            }
        }
        _uiState.update { it.copy(beautyEffects = currentEffects) }
    }

    fun updateBeautyEffectIntensity(effectId: String, intensity: Float) {
        resetControlsAutoHideTimer()
        val currentEffects = _uiState.value.beautyEffects.map { effect ->
            if (effect.id == effectId) {
                val updated = effect.copy(intensity = intensity)
                onBeautyEffectChangedListener?.invoke(updated.id, updated.isEnabled, updated.intensity)
                updated
            } else {
                effect
            }
        }
        _uiState.update { it.copy(beautyEffects = currentEffects) }
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

    fun cycleTimerMode(): TimerMode {
        resetControlsAutoHideTimer()
        val next = when (_uiState.value.timerMode) {
            TimerMode.OFF -> TimerMode.SEC_3
            TimerMode.SEC_3 -> TimerMode.SEC_5
            TimerMode.SEC_5 -> TimerMode.SEC_10
            TimerMode.SEC_10 -> TimerMode.OFF
        }
        _uiState.update { it.copy(timerMode = next) }
        return next
    }

    fun startTimerCountdown(onComplete: () -> Unit) {
        val totalSec = _uiState.value.timerMode.seconds
        if (totalSec <= 0) {
            onComplete()
            return
        }

        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in totalSec downTo 1) {
                _uiState.update { it.copy(countdownRemaining = sec) }
                delay(1000)
            }
            _uiState.update { it.copy(countdownRemaining = null) }
            onComplete()
        }
    }

    fun cancelTimerCountdown() {
        countdownJob?.cancel()
        _uiState.update { it.copy(countdownRemaining = null) }
    }

    fun setBurstProgress(current: Int, total: Int) {
        _uiState.update {
            it.copy(
                isBurstCapturing = true,
                burstProgress = Pair(current, total),
                showShutterFlash = true
            )
        }
        viewModelScope.launch {
            delay(80)
            _uiState.update { it.copy(showShutterFlash = false) }
        }
    }

    fun endBurst() {
        _uiState.update { it.copy(isBurstCapturing = false, burstProgress = null) }
    }

    fun setComparingOriginal(isComparing: Boolean) {
        _uiState.update { it.copy(isComparingOriginal = isComparing) }
        onBypassChangedListener?.invoke(isComparing)
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
        _uiState.update { it.copy(zoomRatio = zoom.coerceIn(0.5f, 10f)) }
    }

    fun toggleEffectsPanel() {
        resetControlsAutoHideTimer()
        _uiState.update { it.copy(showEffectsPanel = !it.showEffectsPanel, showBeautyPanel = false, showHdPanel = false) }
    }

    fun toggleBeautyPanel() {
        resetControlsAutoHideTimer()
        _uiState.update { it.copy(showBeautyPanel = !it.showBeautyPanel, showEffectsPanel = false, showHdPanel = false) }
    }

    fun toggleHdPanel() {
        resetControlsAutoHideTimer()
        _uiState.update { it.copy(showHdPanel = !it.showHdPanel, showEffectsPanel = false, showBeautyPanel = false) }
    }

    fun updateLiveHdOption(optionId: String, value: Float) {
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()
        onHdOptionChangedListener?.invoke(optionId, value)
    }

    fun updateHdOption(optionId: String, value: Float) {
        resetControlsAutoHideTimer()
        resetSliderAutoHideTimer()
        val updated = _uiState.value.hdParameters.withUpdatedValue(optionId, value)
        _uiState.update { it.copy(hdParameters = updated) }
        onHdOptionChangedListener?.invoke(optionId, value)
    }

    fun resetHdParameters() {
        val defaultParams = com.techquantum.livefiltercamera.model.HdParameters()
        _uiState.update { it.copy(hdParameters = defaultParams) }
        defaultParams.toAdjustmentList().forEach { item ->
            onHdOptionChangedListener?.invoke(item.id, item.value)
        }
    }

    fun openGallery() {
        _uiState.update { it.copy(showGalleryScreen = true) }
    }

    fun closeGallery() {
        _uiState.update { it.copy(showGalleryScreen = false) }
    }

    fun onUserInteraction() {
        val newVisibility = !_uiState.value.isControlsVisible
        _uiState.update { it.copy(isControlsVisible = newVisibility) }
        if (newVisibility) {
            resetControlsAutoHideTimer()
        } else {
            controlsAutoHideJob?.cancel()
        }
    }

    fun triggerShutterAnimation() {
        _uiState.update { it.copy(showShutterFlash = true, isCapturingPhoto = true) }
        viewModelScope.launch {
            delay(120)
            _uiState.update { it.copy(showShutterFlash = false, isCapturingPhoto = false) }
        }
    }

    fun onMediaSaved(uri: Uri, thumbnail: Bitmap?) {
        val currentPresetId = _uiState.value.selectedPreset.id
        val updatedRecents = if (currentPresetId != "normal") {
            filterPreferences.addRecentFilterId(currentPresetId)
        } else {
            _uiState.value.recentFilterIds
        }
        _uiState.update {
            it.copy(
                lastCapturedUri = uri,
                lastCapturedThumbnail = thumbnail ?: it.lastCapturedThumbnail,
                isCapturingPhoto = false,
                recentFilterIds = updatedRecents
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
        val currentPresetId = _uiState.value.selectedPreset.id
        val updatedRecents = if (currentPresetId != "normal") {
            filterPreferences.addRecentFilterId(currentPresetId)
        } else {
            _uiState.value.recentFilterIds
        }
        _uiState.update {
            it.copy(
                isRecordingVideo = false,
                recordingDurationSec = 0,
                lastCapturedUri = uri,
                lastCapturedThumbnail = thumbnail ?: it.lastCapturedThumbnail,
                recentFilterIds = updatedRecents
            )
        }
    }

    private fun resetSliderAutoHideTimer() {
        sliderAutoHideJob?.cancel()
        sliderAutoHideJob = viewModelScope.launch {
            delay(10000)
            _uiState.update { it.copy(isIntensitySliderVisible = false) }
        }
    }

    private fun resetControlsAutoHideTimer() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = viewModelScope.launch {
            delay(10000)
            if (!_uiState.value.showEffectsPanel && !_uiState.value.showBeautyPanel && !_uiState.value.isRecordingVideo) {
                _uiState.update { it.copy(isControlsVisible = false, isIntensitySliderVisible = false) }
            }
        }
    }
}
