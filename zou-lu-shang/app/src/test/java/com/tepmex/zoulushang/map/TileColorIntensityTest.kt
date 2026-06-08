package com.tepmex.zoulushang.map

import org.junit.Assert.assertEquals
import org.junit.Test

class TileColorIntensityTest {
    @Test
    fun intensityFraction_usesPointCountBuckets() {
        assertEquals(0.25f, TileColorIntensity.intensityFraction(1))
        assertEquals(0.25f, TileColorIntensity.intensityFraction(5))
        assertEquals(0.50f, TileColorIntensity.intensityFraction(6))
        assertEquals(0.50f, TileColorIntensity.intensityFraction(15))
        assertEquals(0.75f, TileColorIntensity.intensityFraction(16))
        assertEquals(0.75f, TileColorIntensity.intensityFraction(25))
        assertEquals(1.0f, TileColorIntensity.intensityFraction(26))
        assertEquals(1.0f, TileColorIntensity.intensityFraction(100))
    }

    @Test
    fun fillColor_scalesAlphaFromBaseColor() {
        assertEquals(0x2600C853.toInt(), TileColorIntensity.fillColor(1))
        assertEquals(0x4C00C853.toInt(), TileColorIntensity.fillColor(10))
        assertEquals(0x7200C853.toInt(), TileColorIntensity.fillColor(20))
        assertEquals(0x9900C853.toInt(), TileColorIntensity.fillColor(26))
    }

    @Test
    fun blendColors_mixesGreenAndRed() {
        val green = TileColorIntensity.takeoutFillColor(10)
        val red = TileColorIntensity.liveFillColor(10)
        val mixed = TileColorIntensity.blendColors(green, red)
        val r = (mixed shr 16) and 0xFF
        val g = (mixed shr 8) and 0xFF
        val b = mixed and 0xFF
        assert(r in 1..254)
        assert(g in 1..254)
        assert(b in 1..254)
    }
}
