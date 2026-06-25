package com.tepmex.zoulushang2.geo

import com.tepmex.zoulushang2.data.PaintStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEngineTest {
    @Test
    fun firstLocationDrawsDot() {
        val strokes = mutableListOf<PaintStroke>()
        val result = BrushEngine.applyLocation(
            latitude = 39.9,
            longitude = 116.4,
            lastLatitude = null,
            lastLongitude = null,
            lastTimestampMillis = null,
            timestampMillis = 1_000L,
            maxSpeedKmh = 10f,
            colorArgb = 0xFF0000FF.toInt(),
            thicknessMeters = 8f,
        ) { stroke ->
            strokes.add(stroke)
        }
        assertTrue(result.accepted)
        assertEquals(1, strokes.size)
        assertEquals(39.9, strokes[0].latStart, 0.0)
        assertEquals(39.9, strokes[0].latEnd, 0.0)
    }

    @Test
    fun stayingAtSameSpotDrawsAnotherDot() {
        val strokes = mutableListOf<PaintStroke>()
        apply(39.9, 116.4, null, null, null, 1_000L, strokes)
        apply(39.90001, 116.40001, 39.9, 116.4, 1_000L, 4_000L, strokes)
        assertEquals(2, strokes.size)
    }

    @Test
    fun movementDrawsLineStroke() {
        val strokes = mutableListOf<PaintStroke>()
        apply(39.90, 116.40, null, null, null, 1_000L, strokes)
        apply(39.905, 116.405, 39.90, 116.40, 1_000L, 4_000L, strokes)
        assertEquals(2, strokes.size)
        val line = strokes[1]
        assertTrue(line.latStart != line.latEnd || line.lngStart != line.lngEnd)
    }

    @Test
    fun unrealisticSpeedIsRejected() {
        val strokes = mutableListOf<PaintStroke>()
        apply(39.90, 116.40, null, null, null, 1_000L, strokes)
        val result = apply(39.95, 116.45, 39.90, 116.40, 1_000L, 2_000L, strokes)
        assertFalse(result.accepted)
        assertEquals(1, strokes.size)
    }

    @Test
    fun realisticSpeedIsAccepted() {
        val strokes = mutableListOf<PaintStroke>()
        apply(39.90, 116.40, null, null, null, 1_000L, strokes)
        val result = apply(39.90005, 116.40005, 39.90, 116.40, 1_000L, 4_000L, strokes)
        assertTrue(result.accepted)
        assertEquals(2, strokes.size)
    }

    private fun apply(
        latitude: Double,
        longitude: Double,
        lastLatitude: Double?,
        lastLongitude: Double?,
        lastTimestampMillis: Long?,
        timestampMillis: Long,
        strokes: MutableList<PaintStroke>,
    ): LocationApplyResult = BrushEngine.applyLocation(
        latitude = latitude,
        longitude = longitude,
        lastLatitude = lastLatitude,
        lastLongitude = lastLongitude,
        lastTimestampMillis = lastTimestampMillis,
        timestampMillis = timestampMillis,
        maxSpeedKmh = 10f,
        colorArgb = 0xFF0000FF.toInt(),
        thicknessMeters = 8f,
    ) { stroke ->
        strokes.add(stroke)
    }
}
