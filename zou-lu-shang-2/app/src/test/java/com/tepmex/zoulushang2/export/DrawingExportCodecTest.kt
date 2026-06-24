package com.tepmex.zoulushang2.export

import com.tepmex.zoulushang2.data.PaintCell
import com.tepmex.zoulushang2.geo.CellMath
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawingExportCodecTest {
    @Test
    fun roundTripPreservesCells() {
        val key = CellMath.packCellKey(CellMath.PAINT_ZOOM, 123, 456)
        val original = listOf(PaintCell(cellKey = key, intensity = 42))
        val encoded = DrawingExportCodec.encode(original)
        val decoded = DrawingExportCodec.decode(encoded)
        assertEquals(original, decoded)
    }
}
