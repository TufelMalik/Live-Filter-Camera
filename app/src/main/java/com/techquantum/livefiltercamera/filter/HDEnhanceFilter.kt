package com.techquantum.livefiltercamera.filter

import android.graphics.PointF
import com.techquantum.livefiltercamera.model.HdParameters
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBilateralBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBrightnessFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVibranceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter

/**
 * HD Enhance Filter (iPhone-Like HDR Output with customizable parameters)
 *
 * 8-stage GPU image processing pipeline:
 * 1. Bilateral Blur (Noise reduction without edge degradation)
 * 2. Brightness (Exposure balance & lift)
 * 3. Highlight / Shadow (Lift dark shadow details, recover bright highlights)
 * 4. Tone Curve (S-curve punchy midtones)
 * 5. Sharpening (Deep Fusion-style crisp edge definition)
 * 6. Vibrance (Natural skin-friendly color pop)
 * 7. White Balance (Warm neutral 5200K daylight tone)
 * 8. Contrast (Midtone clarity boost)
 */
class HDEnhanceFilter(
    initialIntensity: Float = 1.0f,
    private var hdParameters: HdParameters = HdParameters(masterIntensity = initialIntensity)
) : GPUImageFilterGroup() {

    private val bilateralFilter = GPUImageBilateralBlurFilter()
    private val brightnessFilter = GPUImageBrightnessFilter()
    private val highlightShadowFilter = GPUImageHighlightShadowFilter()
    private val toneCurveFilter = GPUImageToneCurveFilter()
    private val sharpenFilter = GPUImageSharpenFilter()
    private val vibranceFilter = GPUImageVibranceFilter()
    private val whiteBalanceFilter = GPUImageWhiteBalanceFilter()
    private val contrastFilter = GPUImageContrastFilter()

    init {
        // Configure default iPhone-tuned tone curve control points (S-curve)
        toneCurveFilter.setRgbCompositeControlPoints(
            arrayOf(
                PointF(0.0f, 0.0f),
                PointF(0.25f, 0.20f),  // Deepen shadows slightly
                PointF(0.50f, 0.55f),  // Boost midtone contrast
                PointF(0.75f, 0.80f),  // Smooth highlight rolloff
                PointF(1.0f, 1.0f)
            )
        )

        // Register in exact pipeline order
        addFilter(bilateralFilter)
        addFilter(brightnessFilter)
        addFilter(highlightShadowFilter)
        addFilter(toneCurveFilter)
        addFilter(sharpenFilter)
        addFilter(vibranceFilter)
        addFilter(whiteBalanceFilter)
        addFilter(contrastFilter)

        applyParameters(hdParameters)
    }

    /**
     * Dynamically adjusts master intensity (0.0 to 1.0).
     */
    fun setIntensity(intensityRatio: Float) {
        this.hdParameters = this.hdParameters.copy(masterIntensity = intensityRatio.coerceIn(0f, 1f))
        applyParameters(this.hdParameters)
    }

    fun getIntensity(): Float = hdParameters.masterIntensity

    fun setHdParameters(parameters: HdParameters) {
        this.hdParameters = parameters
        applyParameters(parameters)
    }

    fun getHdParameters(): HdParameters = hdParameters

    fun setOption(optionId: String, value: Float) {
        this.hdParameters = this.hdParameters.withUpdatedValue(optionId, value)
        applyParameters(this.hdParameters)
    }

    private fun applyParameters(params: HdParameters) {
        val master = params.masterIntensity

        // 1. Bilateral Blur (noise reduction: 0.0 -> 0.5f, 1.0 -> 8.0f)
        bilateralFilter.setDistanceNormalizationFactor(0.5f + (params.smoothness * 7.5f * master))

        // 2. Brightness (-0.35 to +0.35)
        val bVal = (params.brightness - 0.5f) * 0.70f * master
        brightnessFilter.setBrightness(bVal)

        // 3. Highlight / Shadow
        val shadowVal = (params.shadows * 0.60f) * master
        val highlightVal = 1.0f - ((1.0f - params.highlights) * 0.30f * master)
        highlightShadowFilter.setShadows(shadowVal)
        highlightShadowFilter.setHighlights(highlightVal)

        // 4. Sharpen (0.0 to 1.2f)
        sharpenFilter.setSharpness(params.sharpness * 1.20f * master)

        // 5. Vibrance (0.0 to 0.80f)
        vibranceFilter.setVibrance(params.vibrance * 0.80f * master)

        // 6. White Balance (4200K to 6200K)
        val targetTemp = 4200f + (params.warmth * 2000f)
        val blendedTemp = 5000f + ((targetTemp - 5000f) * master)
        whiteBalanceFilter.setTemperature(blendedTemp)
        whiteBalanceFilter.setTint(0.0f)

        // 7. Contrast (0.75 to 1.50)
        val targetContrast = 0.75f + (params.contrast * 0.75f)
        val blendedContrast = 1.0f + ((targetContrast - 1.0f) * master)
        contrastFilter.setContrast(blendedContrast)
    }
}

