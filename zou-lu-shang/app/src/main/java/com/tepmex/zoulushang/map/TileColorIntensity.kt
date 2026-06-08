package com.tepmex.zoulushang.map

object TileColorIntensity {
    private const val TAKEOUT_FILL_RGB = 0x00C853
    private const val TAKEOUT_STROKE_RGB = 0x00A040
    private const val LIVE_FILL_RGB = 0xD50000
    private const val LIVE_STROKE_RGB = 0xB71C1C
    private const val BASE_FILL_ALPHA = 0x99
    private const val BASE_STROKE_ALPHA = 0xCC

    fun intensityFraction(pointCount: Int): Float = when {
        pointCount <= 5 -> 0.25f
        pointCount <= 15 -> 0.50f
        pointCount <= 25 -> 0.75f
        else -> 1.0f
    }

    fun takeoutFillColor(pointCount: Int): Int =
        colorWithIntensity(TAKEOUT_FILL_RGB, BASE_FILL_ALPHA, pointCount)

    fun takeoutStrokeColor(pointCount: Int): Int =
        colorWithIntensity(TAKEOUT_STROKE_RGB, BASE_STROKE_ALPHA, pointCount)

    fun liveFillColor(pointCount: Int): Int =
        colorWithIntensity(LIVE_FILL_RGB, BASE_FILL_ALPHA, pointCount)

    fun liveStrokeColor(pointCount: Int): Int =
        colorWithIntensity(LIVE_STROKE_RGB, BASE_STROKE_ALPHA, pointCount)

    fun mixedFillColor(takeoutCount: Int, liveCount: Int): Int =
        blendColors(takeoutFillColor(takeoutCount), liveFillColor(liveCount))

    fun mixedStrokeColor(takeoutCount: Int, liveCount: Int): Int =
        blendColors(takeoutStrokeColor(takeoutCount), liveStrokeColor(liveCount))

    fun fillColor(pointCount: Int): Int = takeoutFillColor(pointCount)

    fun strokeColor(pointCount: Int): Int = takeoutStrokeColor(pointCount)

    private fun colorWithIntensity(rgb: Int, baseAlpha: Int, pointCount: Int): Int {
        val alpha = (baseAlpha * intensityFraction(pointCount)).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (rgb and 0x00FFFFFF)
    }

    internal fun blendColors(bottomColor: Int, topColor: Int): Int {
        val bottomAlpha = (bottomColor ushr 24) and 0xFF
        val topAlpha = (topColor ushr 24) and 0xFF
        if (bottomAlpha == 0) return topColor
        if (topAlpha == 0) return bottomColor

        val bottomR = (bottomColor shr 16) and 0xFF
        val bottomG = (bottomColor shr 8) and 0xFF
        val bottomB = bottomColor and 0xFF
        val topR = (topColor shr 16) and 0xFF
        val topG = (topColor shr 8) and 0xFF
        val topB = topColor and 0xFF

        val topFraction = topAlpha / 255f
        val bottomFraction = 1f - topFraction
        val r = (bottomR * bottomFraction + topR * topFraction).toInt().coerceIn(0, 255)
        val g = (bottomG * bottomFraction + topG * topFraction).toInt().coerceIn(0, 255)
        val b = (bottomB * bottomFraction + topB * topFraction).toInt().coerceIn(0, 255)
        val alpha = (bottomAlpha * bottomFraction + topAlpha * topFraction).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
}
