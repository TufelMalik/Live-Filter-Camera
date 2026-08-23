package com.techquantum.livefiltercamera.model

data class HdAdjustmentItem(
    val id: String,
    val name: String,
    val value: Float, // 0.0 to 1.0 normalized
    val defaultValue: Float = 0.5f,
    val displayFormat: (Float) -> String = { "${(it * 100).toInt()}%" }
)

data class HdParameters(
    val masterIntensity: Float = 1.0f,
    val brightness: Float = 0.5f,      // 0.5 = 0.0 offset (-0.35 .. +0.35)
    val contrast: Float = 0.60f,       // 0.5 = 1.0 neutral (0.75 .. 1.45)
    val sharpness: Float = 0.55f,      // 0.0 .. 1.0 (0.0 .. 0.8)
    val vibrance: Float = 0.55f,       // 0.0 .. 1.0 (0.0 .. 0.6)
    val warmth: Float = 0.55f,         // 0.5 = 5000K (4200K .. 6000K)
    val shadows: Float = 0.50f,        // 0.0 .. 1.0 (0.0 .. 0.45)
    val highlights: Float = 0.50f,     // 0.5 = 0.90 (0.75 .. 1.0)
    val smoothness: Float = 0.40f      // Bilateral noise reduction (0.0 .. 1.0)
) {
    fun toAdjustmentList(): List<HdAdjustmentItem> = listOf(
        HdAdjustmentItem("master", "Overall Effect", masterIntensity, 1.0f),
        HdAdjustmentItem("brightness", "Brightness", brightness, 0.5f) {
            val offset = ((it - 0.5f) * 200).toInt()
            if (offset > 0) "+$offset" else "$offset"
        },
        HdAdjustmentItem("contrast", "Contrast", contrast, 0.60f) {
            val offset = ((it - 0.5f) * 200).toInt()
            if (offset > 0) "+$offset" else "$offset"
        },
        HdAdjustmentItem("sharpness", "Sharpness", sharpness, 0.55f),
        HdAdjustmentItem("vibrance", "Vibrance", vibrance, 0.55f),
        HdAdjustmentItem("warmth", "Warmth", warmth, 0.55f) {
            val kelvin = (4200 + it * 1800).toInt()
            "${kelvin}K"
        },
        HdAdjustmentItem("shadows", "Shadow Lift", shadows, 0.50f),
        HdAdjustmentItem("highlights", "Highlights", highlights, 0.50f),
        HdAdjustmentItem("smoothness", "Noise Reduction", smoothness, 0.40f)
    )

    fun withUpdatedValue(id: String, value: Float): HdParameters = when (id) {
        "master" -> copy(masterIntensity = value.coerceIn(0f, 1f))
        "brightness" -> copy(brightness = value.coerceIn(0f, 1f))
        "contrast" -> copy(contrast = value.coerceIn(0f, 1f))
        "sharpness" -> copy(sharpness = value.coerceIn(0f, 1f))
        "vibrance" -> copy(vibrance = value.coerceIn(0f, 1f))
        "warmth" -> copy(warmth = value.coerceIn(0f, 1f))
        "shadows" -> copy(shadows = value.coerceIn(0f, 1f))
        "highlights" -> copy(highlights = value.coerceIn(0f, 1f))
        "smoothness" -> copy(smoothness = value.coerceIn(0f, 1f))
        else -> this
    }

    fun getValue(id: String): Float = when (id) {
        "master" -> masterIntensity
        "brightness" -> brightness
        "contrast" -> contrast
        "sharpness" -> sharpness
        "vibrance" -> vibrance
        "warmth" -> warmth
        "shadows" -> shadows
        "highlights" -> highlights
        "smoothness" -> smoothness
        else -> 0.5f
    }
}
