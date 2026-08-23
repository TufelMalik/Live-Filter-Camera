package com.techquantum.livefiltercamera.filter

import android.content.Context
import android.graphics.Bitmap
import com.techquantum.livefiltercamera.model.FilterPreset
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter

class FilterPipelineManager(
    private val context: Context,
    private val onPipelineChanged: (GPUImageFilter) -> Unit
) {
    private var currentPreset: FilterPreset = FilterPreset(
        id = "normal",
        name = "Original",
        category = com.techquantum.livefiltercamera.model.FilterCategory.ALL,
        lutAssetPath = null,
        intensity = 1.0f
    )
    private var currentLutFilter: GPUImageLookupFilter? = null
    private var currentLutBitmap: Bitmap? = null
    private var hdEnhanceFilter: HDEnhanceFilter? = null
    private var currentHDEnhanceFilter: HDEnhanceFilter? = null
    private var currentHdParameters = com.techquantum.livefiltercamera.model.HdParameters()

    // Pre-allocated shader filter instances (reused, never recreated)
    private var grainFilter: GPUImageCustomShaderFilter? = null
    private var vignetteFilter: GPUImageCustomShaderFilter? = null
    private var fadeFilter: GPUImageCustomShaderFilter? = null
    private var bloomFilter: GPUImageCustomShaderFilter? = null
    private var glowFilter: GPUImageCustomShaderFilter? = null

    // Track active shader effects
    private val effectStates = mutableMapOf<String, Pair<Boolean, Float>>()

    // Beauty Effects State
    private val beautyStates = mutableMapOf<String, Pair<Boolean, Float>>()

    // Pre-allocated beauty filter instances (reused to avoid GC)
    private var smoothFilter: GPUImageGaussianBlurFilter? = null
    private var brightenFilter: GPUImageBrightnessFilter? = null
    private var sharpenFilter: GPUImageSharpenFilter? = null
    private var whitenFilter: GPUImageWhiteBalanceFilter? = null
    private var contrastFilter: GPUImageContrastFilter? = null

    // Comparison Mode Bypass
    private var isBypassActive: Boolean = false

    // Debounce: avoid redundant rebuilds within the same frame
    private var pendingRebuild = false
    private var lastRebuildTimeNanos = 0L
    private val minRebuildIntervalNanos = 16_000_000L // ~60fps max rebuild rate

    // Passthrough filter singleton (reused for "no filter" state)
    private val passthroughFilter = GPUImageFilter()

    init {
        // Initialize shader filters once at construction
        try {
            grainFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/grain.glsl", 0.4f)
            vignetteFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/vignette.glsl", 0.5f)
            fadeFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/fade.glsl", 0.5f)
            bloomFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/bloom.glsl", 0.6f)
            glowFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/glow.glsl", 0.35f)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Pre-allocate beauty filters with default sweet-spot params (0.35f)
        smoothFilter = GPUImageGaussianBlurFilter(0.35f)
        brightenFilter = GPUImageBrightnessFilter()
        sharpenFilter = GPUImageSharpenFilter()
        whitenFilter = GPUImageWhiteBalanceFilter()
        contrastFilter = GPUImageContrastFilter()

        // Pre-allocate HD Enhance filter
        hdEnhanceFilter = HDEnhanceFilter(1.0f)
    }

    private var activeFilter: GPUImageFilter = passthroughFilter

    fun getCurrentFilter(): GPUImageFilter = activeFilter

    /**
     * Fast path: Sets a LUT preset using a pre-cached bitmap or HD Enhance preset.
     */
    fun setLutPreset(preset: FilterPreset, lutBitmap: Bitmap?) {
        currentPreset = preset
        currentLutBitmap = lutBitmap
        if (preset.isHDEnhance) {
            currentLutFilter = null
            val hd = hdEnhanceFilter ?: HDEnhanceFilter(preset.intensity, currentHdParameters)
            hd.setHdParameters(currentHdParameters.copy(masterIntensity = preset.intensity))
            currentHDEnhanceFilter = hd
        } else if (lutBitmap != null && preset.lutAssetPath != null) {
            currentHDEnhanceFilter = null
            val lookup = currentLutFilter ?: GPUImageLookupFilter(preset.intensity)
            lookup.bitmap = lutBitmap
            lookup.setIntensity(preset.intensity)
            currentLutFilter = lookup
        } else {
            currentLutFilter = null
            currentHDEnhanceFilter = null
        }
        rebuildPipeline()
    }

    fun setLutIntensity(intensity: Float) {
        currentPreset = currentPreset.copy(intensity = intensity)
        if (currentPreset.isHDEnhance) {
            currentHdParameters = currentHdParameters.copy(masterIntensity = intensity)
            currentHDEnhanceFilter?.setIntensity(intensity)
        } else {
            currentLutFilter?.setIntensity(intensity)
        }
        // For intensity-only changes, just update the existing filter without full rebuild
        // Only rebuild if there are stacked effects that need recalculating
        if (hasActiveStackedEffects()) {
            rebuildPipeline()
        } else {
            // Fast path: intensity change doesn't need pipeline rebuild
            onPipelineChanged(activeFilter)
        }
    }

    fun updateHdOption(optionId: String, value: Float) {
        currentHdParameters = currentHdParameters.withUpdatedValue(optionId, value)
        currentHDEnhanceFilter?.setOption(optionId, value)
        if (hasActiveStackedEffects()) {
            rebuildPipeline()
        } else {
            onPipelineChanged(activeFilter)
        }
    }

    fun setHdParameters(parameters: com.techquantum.livefiltercamera.model.HdParameters) {
        currentHdParameters = parameters
        currentHDEnhanceFilter?.setHdParameters(parameters)
        if (hasActiveStackedEffects()) {
            rebuildPipeline()
        } else {
            onPipelineChanged(activeFilter)
        }
    }

    fun updateShaderEffect(effectId: String, isEnabled: Boolean, intensity: Float) {
        effectStates[effectId] = Pair(isEnabled, intensity)

        // Update intensity on the pre-allocated instance directly (no new object)
        when (effectId) {
            "grain" -> grainFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
            "vignette" -> vignetteFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
            "fade" -> fadeFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
            "bloom" -> bloomFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
        }

        rebuildPipeline()
    }

    fun updateBeautyEffect(effectId: String, isEnabled: Boolean, intensity: Float) {
        beautyStates[effectId] = Pair(isEnabled, intensity)

        // Update pre-allocated beauty filter params directly
        if (isEnabled) {
            updateBeautyFilterParams(effectId, intensity)
        }
        if (effectId == "glow") {
            glowFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
        }
        rebuildPipeline()
    }

    /**
     * Update beauty filter parameters in-place on pre-allocated instances.
     */
    private fun updateBeautyFilterParams(id: String, intensity: Float) {
        when (id) {
            "smooth" -> smoothFilter?.setBlurSize(0.5f + intensity * 2.0f)
            "brighten" -> brightenFilter?.setBrightness(intensity * 0.35f)
            "sharpen" -> sharpenFilter?.setSharpness(intensity * 1.5f)
            "whiten" -> {
                whitenFilter?.setTemperature(5000f - (intensity * 1200f))
                whitenFilter?.setTint(intensity * 0.1f)
            }
            "contrast" -> contrastFilter?.setContrast(1.0f + (intensity * 0.5f))
        }
    }

    fun setBypass(bypass: Boolean) {
        if (isBypassActive != bypass) {
            isBypassActive = bypass
            rebuildPipeline()
        }
    }

    /**
     * Returns the pre-allocated beauty filter instance (no allocation).
     */
    private fun getBeautyFilter(id: String, intensity: Float): GPUImageFilter? {
        updateBeautyFilterParams(id, intensity)
        return when (id) {
            "smooth" -> smoothFilter
            "brighten" -> brightenFilter
            "sharpen" -> sharpenFilter
            "whiten" -> whitenFilter
            "contrast" -> contrastFilter
            "glow" -> glowFilter
            else -> null
        }
    }

    /**
     * Creates fresh filter instances for photo/video capture (cannot reuse live pipeline filters).
     */
    private fun createBeautyFilterForCapture(id: String, intensity: Float): GPUImageFilter? {
        return when (id) {
            "smooth" -> {
                val blurFilter = GPUImageGaussianBlurFilter(0.5f + intensity * 2.0f)
                blurFilter
            }
            "brighten" -> {
                val brightness = GPUImageBrightnessFilter()
                brightness.setBrightness(intensity * 0.35f)
                brightness
            }
            "glow" -> {
                try {
                    GPUImageCustomShaderFilter.fromAsset(context, "shaders/glow.glsl", intensity)
                } catch (e: Exception) {
                    null
                }
            }
            "sharpen" -> {
                val sharpen = GPUImageSharpenFilter()
                sharpen.setSharpness(intensity * 1.5f)
                sharpen
            }
            "whiten" -> {
                val whiteBalance = GPUImageWhiteBalanceFilter()
                whiteBalance.setTemperature(5000f - (intensity * 1200f))
                whiteBalance.setTint(intensity * 0.1f)
                whiteBalance
            }
            "contrast" -> {
                val contrast = GPUImageContrastFilter()
                contrast.setContrast(1.0f + (intensity * 0.5f))
                contrast
            }
            else -> null
        }
    }

    fun createFilterForCapture(): GPUImageFilter {
        if (isBypassActive) {
            return GPUImageFilter()
        }

        val filterList = mutableListOf<GPUImageFilter>()

        // 1. Base Preset Filter (HD Enhance or LUT)
        if (currentPreset.isHDEnhance) {
            filterList.add(HDEnhanceFilter(currentPreset.intensity, currentHdParameters))
        } else if (currentLutBitmap != null && currentPreset.lutAssetPath != null) {
            val lookup = GPUImageLookupFilter(currentPreset.intensity)
            lookup.bitmap = currentLutBitmap
            filterList.add(lookup)
        }

        // 2. Beauty Filters (fresh instances for capture)
        listOf("smooth", "brighten", "glow", "sharpen", "whiten", "contrast").forEach { id ->
            val state = beautyStates[id]
            if (state?.first == true && state.second > 0f) {
                createBeautyFilterForCapture(id, state.second)?.let { filterList.add(it) }
            }
        }

        // 3. Shader Filters (fresh instances for capture)
        val grainState = effectStates["grain"]
        if (grainState?.first == true) {
            try {
                filterList.add(GPUImageCustomShaderFilter.fromAsset(context, "shaders/grain.glsl", grainState.second))
            } catch (e: Exception) { e.printStackTrace() }
        }

        val vigState = effectStates["vignette"]
        if (vigState?.first == true) {
            try {
                filterList.add(GPUImageCustomShaderFilter.fromAsset(context, "shaders/vignette.glsl", vigState.second))
            } catch (e: Exception) { e.printStackTrace() }
        }

        val fadeState = effectStates["fade"]
        if (fadeState?.first == true) {
            try {
                filterList.add(GPUImageCustomShaderFilter.fromAsset(context, "shaders/fade.glsl", fadeState.second))
            } catch (e: Exception) { e.printStackTrace() }
        }

        val bloomState = effectStates["bloom"]
        if (bloomState?.first == true) {
            try {
                filterList.add(GPUImageCustomShaderFilter.fromAsset(context, "shaders/bloom.glsl", bloomState.second))
            } catch (e: Exception) { e.printStackTrace() }
        }

        return when {
            filterList.isEmpty() -> GPUImageFilter()
            filterList.size == 1 -> filterList.first()
            else -> GPUImageFilterGroup(filterList)
        }
    }

    fun applyToBitmap(sourceBitmap: Bitmap): Bitmap {
        val filter = createFilterForCapture()
        val gpuImage = jp.co.cyberagent.android.gpuimage.GPUImage(context)
        gpuImage.setImage(sourceBitmap)
        gpuImage.setFilter(filter)
        return gpuImage.bitmapWithFilterApplied
    }

    private fun hasActiveStackedEffects(): Boolean {
        val hasBeauty = beautyStates.any { it.value.first && it.value.second > 0f }
        val hasShader = effectStates.any { it.value.first }
        return hasBeauty || hasShader
    }

    fun rebuildPipeline() {
        if (isBypassActive) {
            activeFilter = passthroughFilter
            onPipelineChanged(activeFilter)
            return
        }

        val filterList = mutableListOf<GPUImageFilter>()

        // 1. Base Filter (HD Enhance or LUT)
        if (currentHDEnhanceFilter != null) {
            filterList.add(currentHDEnhanceFilter!!)
        } else if (currentLutFilter != null) {
            filterList.add(currentLutFilter!!)
        }

        // 2. Beauty Filters (reuse pre-allocated instances)
        listOf("smooth", "brighten", "glow", "sharpen", "whiten", "contrast").forEach { id ->
            val state = beautyStates[id]
            if (state?.first == true && state.second > 0f) {
                getBeautyFilter(id, state.second)?.let { filterList.add(it) }
            }
        }

        // 3. Shader Effects (reuse pre-allocated instances)
        val grainState = effectStates["grain"]
        if (grainState?.first == true && grainFilter != null) {
            grainFilter?.setIntensity(grainState.second)
            filterList.add(grainFilter!!)
        }

        val vigState = effectStates["vignette"]
        if (vigState?.first == true && vignetteFilter != null) {
            vignetteFilter?.setIntensity(vigState.second)
            filterList.add(vignetteFilter!!)
        }

        val fadeState = effectStates["fade"]
        if (fadeState?.first == true && fadeFilter != null) {
            fadeFilter?.setIntensity(fadeState.second)
            filterList.add(fadeFilter!!)
        }

        val bloomState = effectStates["bloom"]
        if (bloomState?.first == true && bloomFilter != null) {
            bloomFilter?.setIntensity(bloomState.second)
            filterList.add(bloomFilter!!)
        }

        val resultFilter = when {
            filterList.isEmpty() -> passthroughFilter
            filterList.size == 1 -> filterList.first()
            else -> GPUImageFilterGroup(filterList)
        }

        activeFilter = resultFilter
        onPipelineChanged(resultFilter)
    }
}
