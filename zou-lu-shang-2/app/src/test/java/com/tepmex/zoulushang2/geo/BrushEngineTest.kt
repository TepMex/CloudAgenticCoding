package com.tepmex.zoulushang2.geo

import com.tepmex.zoulushang2.data.PaintStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushEngineTest {
    @Test
    fun firstLocationDrawsDot() {
        val strokes = mutableListOf<PaintStroke>()
        BrushEngine.applyLocation(
            latitude = 39.9,
            longitude = 116.4,
            lastLatitude = null,
            lastLongitude = null,
            colorArgb = 0xFF0000FF.toInt(),
            thicknessMeters = 8f,
        ) { stroke ->
            strokes.add(stroke)
        }
        assertEquals(1, strokes.size)
        assertEquals(39.9, strokes[0].latStart, 0.0)
        assertEquals(39.9, strokes[0].latEnd, 0.0)
    }

    @Test
    fun stayingAtSameSpotDrawsAnotherDot() {
        val strokes = mutableListOf<PaintStroke>()
        BrushEngine.applyLocation(39.9, 116.4, null, null, 0xFF0000FF.toInt(), 8f) { strokes.add(it) }
        BrushEngine.applyLocation(39.90001, 116.40001, 39.9, 116.4, 0xFF0000FF.toInt(), 8f) {
            strokes.add(it)
        }
        assertEquals(2, strokes.size)
    }

    @Test
    fun movementDrawsLineStroke() {
        val strokes = mutableListOf<PaintStroke>()
        BrushEngine.applyLocation(39.90, 116.40, null, null, 0xFF0000FF.toInt(), 8f) { strokes.add(it) }
        BrushEngine.applyLocation(39.905, 116.405, 39.90, 116.40, 0xFF0000FF.toInt(), 8f) { strokes.add(it) }
        assertEquals(2, strokes.size)
        val line = strokes[1]
        assertTrue(line.latStart != line.latEnd || line.lngStart != line.lngEnd)
    }
}
