package com.techquantum.livefiltercamera.model

object FilterRepository {

    val presets: List<FilterPreset> = listOf(
        FilterPreset(
            id = "normal",
            name = "Original",
            category = FilterCategory.ALL,
            lutAssetPath = null,
            intensity = 1.0f,
            gradientColors = listOf(0xFFB0BEC5, 0xFF455A64)
        ),
        // --- FILM (5) ---
        FilterPreset(
            id = "kodak_gold",
            name = "Kodak Gold",
            category = FilterCategory.FILM,
            lutAssetPath = "luts/kodak_gold.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFB300, 0xFFE65100)
        ),
        FilterPreset(
            id = "fuji_superia",
            name = "Fuji Superia",
            category = FilterCategory.FILM,
            lutAssetPath = "luts/fuji_superia.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF00BFA5, 0xFFC2185B)
        ),
        FilterPreset(
            id = "portra_400",
            name = "Portra 400",
            category = FilterCategory.FILM,
            lutAssetPath = "luts/portra_400.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFCCBC, 0xFF8D6E63)
        ),
        FilterPreset(
            id = "cinestill_800t",
            name = "Cinestill 800T",
            category = FilterCategory.FILM,
            lutAssetPath = "luts/cinestill_800t.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF00E5FF, 0xFFFF1744)
        ),
        FilterPreset(
            id = "ektar_100",
            name = "Ektar 100",
            category = FilterCategory.FILM,
            lutAssetPath = "luts/ektar_100.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFF3D00, 0xFF2979FF)
        ),

        // --- MOODY (5) ---
        FilterPreset(
            id = "noir",
            name = "Noir",
            category = FilterCategory.MOODY,
            lutAssetPath = "luts/noir.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFECEFF1, 0xFF212121)
        ),
        FilterPreset(
            id = "shadow",
            name = "Shadow",
            category = FilterCategory.MOODY,
            lutAssetPath = "luts/shadow.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF424242, 0xFF000000)
        ),
        FilterPreset(
            id = "matte_black",
            name = "Matte Black",
            category = FilterCategory.MOODY,
            lutAssetPath = "luts/matte_black.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF757575, 0xFF263238)
        ),
        FilterPreset(
            id = "underexposed",
            name = "Underexposed",
            category = FilterCategory.MOODY,
            lutAssetPath = "luts/underexposed.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF37474F, 0xFF102027)
        ),
        FilterPreset(
            id = "dusk",
            name = "Dusk",
            category = FilterCategory.MOODY,
            lutAssetPath = "luts/dusk.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF7E57C2, 0xFF1A237E)
        ),

        // --- WARM (5) ---
        FilterPreset(
            id = "golden_hour",
            name = "Golden Hour",
            category = FilterCategory.WARM,
            lutAssetPath = "luts/golden_hour.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFD54F, 0xFFFF6F00)
        ),
        FilterPreset(
            id = "sunset",
            name = "Sunset",
            category = FilterCategory.WARM,
            lutAssetPath = "luts/sunset.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFF4081, 0xFFFF6E40)
        ),
        FilterPreset(
            id = "retro_70s",
            name = "Retro 70s",
            category = FilterCategory.WARM,
            lutAssetPath = "luts/retro_70s.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFBC02D, 0xFF5D4037)
        ),
        FilterPreset(
            id = "faded_warm",
            name = "Faded Warm",
            category = FilterCategory.WARM,
            lutAssetPath = "luts/faded_warm.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFAB91, 0xFFBCAAA4)
        ),
        FilterPreset(
            id = "amber",
            name = "Amber",
            category = FilterCategory.WARM,
            lutAssetPath = "luts/amber.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFB74D, 0xFFE65100)
        ),

        // --- COOL (5) ---
        FilterPreset(
            id = "arctic",
            name = "Arctic",
            category = FilterCategory.COOL,
            lutAssetPath = "luts/arctic.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFE0F7FA, 0xFF0288D1)
        ),
        FilterPreset(
            id = "morning_mist",
            name = "Morning Mist",
            category = FilterCategory.COOL,
            lutAssetPath = "luts/morning_mist.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFCFD8DC, 0xFF78909C)
        ),
        FilterPreset(
            id = "ice_blue",
            name = "Ice Blue",
            category = FilterCategory.COOL,
            lutAssetPath = "luts/ice_blue.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFF80D8FF, 0xFF0091EA)
        ),
        FilterPreset(
            id = "fade_cool",
            name = "Fade Cool",
            category = FilterCategory.COOL,
            lutAssetPath = "luts/fade_cool.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFB0BEC5, 0xFF37474F)
        ),
        FilterPreset(
            id = "dreamy",
            name = "Dreamy",
            category = FilterCategory.COOL,
            lutAssetPath = "luts/dreamy.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFF8BBD0, 0xFF80DEEA)
        ),

        // --- TRENDY (5) ---
        FilterPreset(
            id = "y2k",
            name = "Y2K",
            category = FilterCategory.TRENDY,
            lutAssetPath = "luts/y2k.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFF007F, 0xFF00F0FF)
        ),
        FilterPreset(
            id = "disposable",
            name = "Disposable",
            category = FilterCategory.TRENDY,
            lutAssetPath = "luts/disposable.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFF5252, 0xFFFFD740)
        ),
        FilterPreset(
            id = "lomo",
            name = "Lomo",
            category = FilterCategory.TRENDY,
            lutAssetPath = "luts/lomo.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFF1744, 0xFF00E676)
        ),
        FilterPreset(
            id = "washed_out",
            name = "Washed Out",
            category = FilterCategory.TRENDY,
            lutAssetPath = "luts/washed_out.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFF9C4, 0xFFB2DFDB)
        ),
        FilterPreset(
            id = "velvia_pop",
            name = "Velvia Pop",
            category = FilterCategory.TRENDY,
            lutAssetPath = "luts/velvia_pop.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFD500F9, 0xFF00E676)
        ),

        // --- BRIGHT (5) ---
        FilterPreset(
            id = "clarity",
            name = "Clarity",
            category = FilterCategory.BRIGHT,
            lutAssetPath = "luts/clarity.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFFFFF, 0xFF42A5F5)
        ),
        FilterPreset(
            id = "natural",
            name = "Natural",
            category = FilterCategory.BRIGHT,
            lutAssetPath = "luts/natural.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFF59D, 0xFF81C784)
        ),
        FilterPreset(
            id = "vivid",
            name = "Vivid",
            category = FilterCategory.BRIGHT,
            lutAssetPath = "luts/vivid.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFF9100, 0xFF00E5FF)
        ),
        FilterPreset(
            id = "summer_pop",
            name = "Summer Pop",
            category = FilterCategory.BRIGHT,
            lutAssetPath = "luts/summer_pop.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFEA00, 0xFF00B0FF)
        ),
        FilterPreset(
            id = "airy_white",
            name = "Airy White",
            category = FilterCategory.BRIGHT,
            lutAssetPath = "luts/airy_white.cube",
            intensity = 1.0f,
            gradientColors = listOf(0xFFFFFFFF, 0xFFE1BEE7)
        )
    )

    fun getPresetsByCategory(category: FilterCategory): List<FilterPreset> {
        return if (category == FilterCategory.ALL) {
            presets
        } else {
            presets.filter { it.category == category }
        }
    }

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
