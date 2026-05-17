package com.tepmex.chesswatch

import android.graphics.Color
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.abs

object PastelTileColors {

    /** Calming neutral tile for the built-in idle activity. */
    fun idleArgb(): Int = Color.argb(255, 245, 243, 250)

    fun randomArgb(): Int {
        val rng = ThreadLocalRandom.current()
        val h = rng.nextFloat() * 360f
        val s = 0.16f + rng.nextFloat() * 0.26f
        val v = 0.95f + rng.nextFloat() * 0.05f
        return Color.HSVToColor(255, floatArrayOf(h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))
    }

    /** Stable pastel derived from a string (used for migration and defaults). */
    fun fromSeed(seed: String): Int {
        var h = 2166136261L
        for (ch in seed) {
            h = (h xor ch.code.toLong()) * 16777619L
        }
        val hue = abs((h xor (h ushr 32))).toFloat() % 360f
        val s = 0.16f + ((h ushr 8) and 0xFFL).toFloat() / 255f * 0.26f
        val v = 0.94f + ((h ushr 16) and 0xFFL).toFloat() / 255f * 0.06f
        return Color.HSVToColor(255, floatArrayOf(hue, s.coerceIn(0.16f, 0.42f), v.coerceIn(0.94f, 1f)))
    }
}
