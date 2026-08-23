package com.techquantum.livefiltercamera.filter

import android.content.Context
import android.graphics.Bitmap
import com.techquantum.livefiltercamera.model.FilterPreset
import com.techquantum.livefiltercamera.model.ShaderEffect
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLookupFilter

class FilterPipelineManager(
    private val context: Context,
    private val onPipelineChanged: (GPUImageFilter) -> Unit
) {
    private var currentPreset: FilterPreset = FilterPreset("normal", "Original", null, 1.0f)
    private var currentLutFilter: GPUImageLookupFilter? = null
    
    // Shader filter instances
    private var grainFilter: GPUImageCustomShaderFilter? = null
    private var vignetteFilter: GPUImageCustomShaderFilter? = null
    private var fadeFilter: GPUImageCustomShaderFilter? = null
    private var bloomFilter: GPUImageCustomShaderFilter? = null

    // Track active shader effects
    private val effectStates = mutableMapOf<String, Pair<Boolean, Float>>()

    init {
        // Initialize shader filters
        try {
            grainFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/grain.glsl", 0.4f)
            vignetteFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/vignette.glsl", 0.5f)
            fadeFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/fade.glsl", 0.5f)
            bloomFilter = GPUImageCustomShaderFilter.fromAsset(context, "shaders/bloom.glsl", 0.6f)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var currentLutBitmap: Bitmap? = null
    private var activeFilter: GPUImageFilter = GPUImageFilter()

    fun getCurrentFilter(): GPUImageFilter = activeFilter

    fun setLutPreset(preset: FilterPreset, lutBitmap: Bitmap?) {
        currentPreset = preset
        currentLutBitmap = lutBitmap
        if (lutBitmap != null && preset.lutAssetPath != null) {
            val lookup = GPUImageLookupFilter(preset.intensity)
            lookup.bitmap = lutBitmap
            currentLutFilter = lookup
        } else {
            currentLutFilter = null
        }
        rebuildPipeline()
    }

    fun setLutIntensity(intensity: Float) {
        currentPreset = currentPreset.copy(intensity = intensity)
        currentLutFilter?.setIntensity(intensity)
        rebuildPipeline()
    }

    fun updateShaderEffect(effectId: String, isEnabled: Boolean, intensity: Float) {
        effectStates[effectId] = Pair(isEnabled, intensity)
        
        when (effectId) {
            "grain" -> grainFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
            "vignette" -> vignetteFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
            "fade" -> fadeFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
            "bloom" -> bloomFilter?.setIntensity(if (isEnabled) intensity else 0.0f)
        }
        
        rebuildPipeline()
    }

    fun createFilterForCapture(): GPUImageFilter {
        val filterList = mutableListOf<GPUImageFilter>()

        // 1. Base LUT Filter
        if (currentLutBitmap != null && currentPreset.lutAssetPath != null) {
            val lookup = GPUImageLookupFilter(currentPreset.intensity)
            lookup.bitmap = currentLutBitmap
            filterList.add(lookup)
        }

        // 2. Grain Filter
        val grainState = effectStates["grain"]
        if (grainState?.first == true) {
            try {
                val gf = GPUImageCustomShaderFilter.fromAsset(context, "shaders/grain.glsl", grainState.second)
                filterList.add(gf)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Vignette Filter
        val vigState = effectStates["vignette"]
        if (vigState?.first == true) {
            try {
                val vf = GPUImageCustomShaderFilter.fromAsset(context, "shaders/vignette.glsl", vigState.second)
                filterList.add(vf)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 4. Fade Filter
        val fadeState = effectStates["fade"]
        if (fadeState?.first == true) {
            try {
                val ff = GPUImageCustomShaderFilter.fromAsset(context, "shaders/fade.glsl", fadeState.second)
                filterList.add(ff)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 5. Bloom Filter
        val bloomState = effectStates["bloom"]
        if (bloomState?.first == true) {
            try {
                val bf = GPUImageCustomShaderFilter.fromAsset(context, "shaders/bloom.glsl", bloomState.second)
                filterList.add(bf)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    fun rebuildPipeline() {
        val filterList = mutableListOf<GPUImageFilter>()

        // 1. Base LUT Filter
        if (currentLutFilter != null) {
            filterList.add(currentLutFilter!!)
        }

        // 2. Grain Filter (if enabled)
        val grainState = effectStates["grain"]
        if (grainState?.first == true && grainFilter != null) {
            grainFilter?.setIntensity(grainState.second)
            filterList.add(grainFilter!!)
        }

        // 3. Vignette Filter (if enabled)
        val vigState = effectStates["vignette"]
        if (vigState?.first == true && vignetteFilter != null) {
            vignetteFilter?.setIntensity(vigState.second)
            filterList.add(vignetteFilter!!)
        }

        // 4. Fade Filter (if enabled)
        val fadeState = effectStates["fade"]
        if (fadeState?.first == true && fadeFilter != null) {
            fadeFilter?.setIntensity(fadeState.second)
            filterList.add(fadeFilter!!)
        }

        // 5. Bloom Filter (if enabled)
        val bloomState = effectStates["bloom"]
        if (bloomState?.first == true && bloomFilter != null) {
            bloomFilter?.setIntensity(bloomState.second)
            filterList.add(bloomFilter!!)
        }

        val resultFilter = when {
            filterList.isEmpty() -> GPUImageFilter() // Passthrough
            filterList.size == 1 -> filterList.first()
            else -> GPUImageFilterGroup(filterList)
        }

        activeFilter = resultFilter
        onPipelineChanged(resultFilter)
    }
}
