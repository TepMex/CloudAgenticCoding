package com.tepmex.instantpinyin.domain

import com.tepmex.instantpinyin.ocr.OcrLine
import com.tepmex.instantpinyin.ocr.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingGlyphsTest {
    @Test
    fun horizontalLineSplitsLeftToRight() {
        val glyphs = ReadingGlyphs.slice(line("你好", 0, 0, 100, 40))
        assertEquals(listOf("你", "好"), glyphs.map { it.hanzi })
        assertEquals(0f, glyphs[0].box.left, 0.01f)
        assertEquals(50f, glyphs[0].box.right, 0.01f)
        assertEquals(50f, glyphs[1].box.left, 0.01f)
        assertEquals(100f, glyphs[1].box.right, 0.01f)
        assertTrue(glyphs[0].pinyin.contains("ǐ") || glyphs[0].pinyin.contains("nǐ"))
        assertFalse(glyphs[0].vertical)
    }

    @Test
    fun punctuationKeepsItsSlotSoHanziStayAligned() {
        val glyphs = ReadingGlyphs.slice(line("你，好", 0, 0, 90, 30))
        assertEquals(listOf("你", "好"), glyphs.map { it.hanzi })
        assertEquals(0f, glyphs[0].box.left, 0.01f)
        assertEquals(30f, glyphs[0].box.right, 0.01f)
        assertEquals(60f, glyphs[1].box.left, 0.01f)
        assertEquals(90f, glyphs[1].box.right, 0.01f)
    }

    @Test
    fun whitespaceDoesNotTakeASlot() {
        val glyphs = ReadingGlyphs.slice(line("你 好", 0, 0, 80, 20))
        assertEquals(2, glyphs.size)
        assertEquals(40f, glyphs[0].box.right, 0.01f)
        assertEquals(40f, glyphs[1].box.left, 0.01f)
    }

    @Test
    fun verticalLineSplitsTopToBottom() {
        val glyphs = ReadingGlyphs.slice(line("你好", 10, 20, 20, 100))
        assertEquals(listOf("你", "好"), glyphs.map { it.hanzi })
        assertEquals(20f, glyphs[0].box.top, 0.01f)
        assertEquals(70f, glyphs[0].box.bottom, 0.01f)
        assertEquals(70f, glyphs[1].box.top, 0.01f)
        assertEquals(120f, glyphs[1].box.bottom, 0.01f)
        assertTrue(glyphs[0].vertical)
        assertTrue(glyphs[1].vertical)
    }

    @Test
    fun latinOnlyProducesNoGlyphs() {
        assertTrue(ReadingGlyphs.slice(line("ABC", 0, 0, 40, 20)).isEmpty())
    }

    private fun line(text: String, x: Int, y: Int, w: Int, h: Int) =
        OcrLine(text, TextBox(x, y, w, h, 0.9f))
}
