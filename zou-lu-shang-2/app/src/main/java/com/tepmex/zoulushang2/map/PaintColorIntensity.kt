package com.tepmex.zoulushang2.map

object PaintColorIntensity {
    private const val BRUSH_RGB = 0x7B1FA2
    private const val BASE_ALPHA = 0xAA

    fun fillColor(intensity: Int): Int {
        val fraction = (intensity / 1000f).coerceIn(0.08f, 1.0f)
        val alpha = (BASE_ALPHA * fraction).toInt().coerceIn(0x22, 0xEE)
        return (alpha shl 24) or (BRUSH_RGB and 0x00FFFFFF)
    }
}
