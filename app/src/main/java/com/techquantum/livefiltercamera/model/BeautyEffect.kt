package com.techquantum.livefiltercamera.model

data class BeautyEffect(
    val id: String,
    val name: String,
    val intensity: Float = 0.0f,
    val isEnabled: Boolean = false,
    val minVal: Float = 0.0f,
    val maxVal: Float = 1.0f
)

object BeautyRepository {
    fun getDefaultBeautyEffects(): List<BeautyEffect> = listOf(
        BeautyEffect(
            id = "smooth",
            name = "Skin Smooth",
            intensity = 0.4f,
            isEnabled = false
        ),
        BeautyEffect(
            id = "brighten",
            name = "Skin Brighten",
            intensity = 0.15f,
            isEnabled = false
        ),
        BeautyEffect(
            id = "glow",
            name = "Soft Glow",
            intensity = 0.35f,
            isEnabled = false
        ),
        BeautyEffect(
            id = "sharpen",
            name = "HD Sharpen",
            intensity = 0.3f,
            isEnabled = false
        ),
        BeautyEffect(
            id = "whiten",
            name = "Whiten",
            intensity = 0.25f,
            isEnabled = false
        ),
        BeautyEffect(
            id = "contrast",
            name = "Contrast Boost",
            intensity = 0.2f,
            isEnabled = false
        )
    )
}
