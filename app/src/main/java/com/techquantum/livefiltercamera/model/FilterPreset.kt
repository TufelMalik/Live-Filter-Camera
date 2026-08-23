package com.techquantum.livefiltercamera.model

enum class FilterCategory(val displayName: String) {
    ALL("All"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    FILM("Film"),
    MOODY("Moody"),
    WARM("Warm"),
    COOL("Cool"),
    TRENDY("Trendy"),
    BRIGHT("Bright"),
    BEAUTY("Beauty")
}

data class FilterPreset(
    val id: String,
    val name: String,
    val category: FilterCategory = FilterCategory.ALL,
    val lutAssetPath: String? = null,
    val intensity: Float = 1.0f,
    val gradientColors: List<Long> = listOf(0xFF888888, 0xFF444444),
    val isHDEnhance: Boolean = false
)
