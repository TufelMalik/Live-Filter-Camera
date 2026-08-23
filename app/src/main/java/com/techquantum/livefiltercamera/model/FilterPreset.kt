package com.techquantum.livefiltercamera.model

data class FilterPreset(
    val id: String,
    val name: String,
    val lutAssetPath: String? = null,
    val intensity: Float = 1.0f
)
