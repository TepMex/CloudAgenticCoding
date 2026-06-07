package com.tepmex.zoulushang.map

object TileColorIntensity {
    private const val BASE_FILL_RGB = 0x00C853
    private const val BASE_STROKE_RGB = 0x00A040
    private const val BASE_FILL_ALPHA = 0x99
    private const val BASE_STROKE_ALPHA = 0xCC

    fun intensityFraction(pointCount: Int): Float = when {
        pointCount <= 5 -> 0.25f
        pointCount <= 15 -> 0.50f
        pointCount <= 25 -> 0.75f
        else -> 1.0f
    }

    fun fillColor(pointCount: Int): Int = colorWithIntensity(BASE_FILL_RGB, BASE_FILL_ALPHA, pointCount)

    fun strokeColor(pointCount: Int): Int = colorWithIntensity(BASE_STROKE_RGB, BASE_STROKE_ALPHA, pointCount)

    private fun colorWithIntensity(rgb: Int, baseAlpha: Int, pointCount: Int): Int {
        val alpha = (baseAlpha * intensityFraction(pointCount)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (rgb and 0x00FFFFFF)
    }
}
