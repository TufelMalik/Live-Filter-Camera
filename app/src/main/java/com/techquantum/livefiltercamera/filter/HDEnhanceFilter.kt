package com.techquantum.livefiltercamera.filter

import android.graphics.PointF
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBilateralBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHighlightShadowFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVibranceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter

/**
 * Phase 9B — HD Enhance Filter (iPhone-Like Output)
 *
 * 7-stage GPU image processing pipeline:
 * 1. Bilateral Blur (Noise reduction without edge degradation)
 * 2. Highlight / Shadow (Lift dark shadow details, recover bright highlights)
 * 3. Tone Curve (S-curve punchy midtones)
 * 4. Sharpening (Deep Fusion-style crisp edge definition)
 * 5. Vibrance (Natural skin-friendly color pop)
 * 6. White Balance (Warm neutral 5200K daylight tone)
 * 7. Contrast (Midtone clarity boost)
 */
class HDEnhanceFilter(
    initialIntensity: Float = 1.0f
) : GPUImageFilterGroup() {

    private val bilateralFilter = GPUImageBilateralBlurFilter()
    private val highlightShadowFilter = GPUImageHighlightShadowFilter()
    private val toneCurveFilter = GPUImageToneCurveFilter()
    private val sharpenFilter = GPUImageSharpenFilter()
    private val vibranceFilter = GPUImageVibranceFilter()
    private val whiteBalanceFilter = GPUImageWhiteBalanceFilter()
    private val contrastFilter = GPUImageContrastFilter()

    private var intensity: Float = initialIntensity

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

        applyTunedParameters(initialIntensity)

        // Register in exact pipeline order
        addFilter(bilateralFilter)
        addFilter(highlightShadowFilter)
        addFilter(toneCurveFilter)
        addFilter(sharpenFilter)
        addFilter(vibranceFilter)
        addFilter(whiteBalanceFilter)
        addFilter(contrastFilter)
    }

    /**
     * Dynamically adjusts all 7 filter stage parameters scaled by intensity (0.0 to 1.0).
     */
    fun setIntensity(intensityRatio: Float) {
        this.intensity = intensityRatio.coerceIn(0f, 1f)
        applyTunedParameters(this.intensity)
    }

    fun getIntensity(): Float = intensity

    private fun applyTunedParameters(ratio: Float) {
        // 1. Bilateral Blur (noise reduction: 0.0 -> neutral, 1.0 -> 8.0f)
        bilateralFilter.setDistanceNormalizationFactor(0.5f + (ratio * 7.5f))

        // 2. Highlight Shadow (lift shadows: 0.0 -> 0.0f, 1.0 -> 0.30f; highlights: 0.0 -> 1.0f, 1.0 -> 0.90f)
        highlightShadowFilter.setShadows(ratio * 0.30f)
        highlightShadowFilter.setHighlights(1.0f - (ratio * 0.10f))

        // 4. Sharpen (0.0 -> 0.0f, 1.0 -> 0.60f)
        sharpenFilter.setSharpness(ratio * 0.60f)

        // 5. Vibrance (0.0 -> 0.0f, 1.0 -> 0.40f)
        vibranceFilter.setVibrance(ratio * 0.40f)

        // 6. White Balance (0.0 -> 5000K neutral, 1.0 -> 5200K warm neutral)
        whiteBalanceFilter.setTemperature(5000f + (ratio * 200f))
        whiteBalanceFilter.setTint(0.0f)

        // 7. Contrast (0.0 -> 1.0f neutral, 1.0 -> 1.15f punch)
        contrastFilter.setContrast(1.0f + (ratio * 0.15f))
    }
}
