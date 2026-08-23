package com.techquantum.livefiltercamera.model

data class ShaderEffect(
    val id: String,
    val name: String,
    val shaderAssetPath: String,
    var intensity: Float = 0.5f,
    var isEnabled: Boolean = false
)
