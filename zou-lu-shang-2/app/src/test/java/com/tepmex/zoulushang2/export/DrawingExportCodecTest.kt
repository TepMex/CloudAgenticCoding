package com.tepmex.zoulushang2.export

import com.tepmex.zoulushang2.data.PaintStroke
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingExportCodecTest {
    @Test
    fun roundTripPreservesStrokes() {
        val original = listOf(
            PaintStroke(
                latStart = 39.9,
                lngStart = 116.4,
                latEnd = 39.91,
                lngEnd = 116.41,
                colorArgb = 0xFF7B1FA2.toInt(),
                thicknessMeters = 8f,
            ),
        )
        val encoded = DrawingExportCodec.encode(original)
        val decoded = DrawingExportCodec.decode(encoded)
        assertEquals(original.size, decoded.size)
        assertEquals(original[0].latStart, decoded[0].latStart, 0.0)
        assertEquals(original[0].lngStart, decoded[0].lngStart, 0.0)
        assertEquals(original[0].latEnd, decoded[0].latEnd, 0.0)
        assertEquals(original[0].lngEnd, decoded[0].lngEnd, 0.0)
        assertEquals(original[0].colorArgb, decoded[0].colorArgb)
        assertEquals(original[0].thicknessMeters, decoded[0].thicknessMeters, 0.01f)
    }
}
