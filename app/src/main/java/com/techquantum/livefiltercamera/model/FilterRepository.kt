package com.techquantum.livefiltercamera.model

object FilterRepository {

    val presets: List<FilterPreset> = listOf(
        FilterPreset(
            id = "normal",
            name = "Original",
            lutAssetPath = null,
            intensity = 1.0f
        ),
        FilterPreset(
            id = "film_warm",
            name = "Film Warm",
            lutAssetPath = "luts/film_warm.cube",
            intensity = 1.0f
        ),
        FilterPreset(
            id = "fade_cool",
            name = "Fade Cool",
            lutAssetPath = "luts/fade_cool.cube",
            intensity = 1.0f
        ),
        FilterPreset(
            id = "cinema",
            name = "Cinema",
            lutAssetPath = "luts/cinema.cube",
            intensity = 1.0f
        ),
        FilterPreset(
            id = "vintage",
            name = "Vintage",
            lutAssetPath = "luts/vintage.cube",
            intensity = 1.0f
        ),
        FilterPreset(
            id = "moody",
            name = "Moody",
            lutAssetPath = "luts/moody.cube",
            intensity = 1.0f
        )
    )

    fun getDefaultShaderEffects(): List<ShaderEffect> = listOf(
        ShaderEffect(
            id = "grain",
            name = "Film Grain",
            shaderAssetPath = "shaders/grain.glsl",
            intensity = 0.4f,
            isEnabled = false
        ),
        ShaderEffect(
            id = "vignette",
            name = "Vignette",
            shaderAssetPath = "shaders/vignette.glsl",
            intensity = 0.5f,
            isEnabled = false
        ),
        ShaderEffect(
            id = "fade",
            name = "Faded Blacks",
            shaderAssetPath = "shaders/fade.glsl",
            intensity = 0.5f,
            isEnabled = false
        ),
        ShaderEffect(
            id = "bloom",
            name = "Bloom Glow",
            shaderAssetPath = "shaders/bloom.glsl",
            intensity = 0.6f,
            isEnabled = false
        )
    )
}
