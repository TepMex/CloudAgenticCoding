package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PinyinLabelsTest {
    @Test
    fun horizontalPinyinSitsAboveTheGlyph() {
        val label = layout(PxBox(20f, 80f, 60f, 120f), viewWidth = 200f, viewHeight = 400f).single()
        assertEquals("你", label.hanzi)
        assertTrue(label.pill.bottom <= label.glyph.top + 0.5f)
        assertTrue(abs(label.pill.centerX - label.glyph.centerX) < 1f)
    }

    @Test
    fun glyphTouchingTheTopPinsTheLabelToTheScreenEdge() {
        val label = layout(PxBox(20f, 0f, 60f, 40f), viewWidth = 200f, viewHeight = 400f).single()
        assertEquals(0f, label.pill.top, 0.01f)
    }

    @Test
    fun verticalGlyphPutsPinyinBesideIt() {
        val label = layout(PxBox(10f, 30f, 40f, 150f), viewWidth = 400f, viewHeight = 400f, vertical = true).single()
        assertTrue(label.pill.left >= label.glyph.right - 0.5f)
        assertTrue(abs(label.pill.centerY - label.glyph.centerY) < 1f)
    }

    @Test
    fun aTallSliceOfAHorizontalLineKeepsPinyinAbove() {
        val label = layout(PxBox(10f, 30f, 40f, 150f), viewWidth = 400f, viewHeight = 400f, vertical = false).single()
        assertTrue(label.pill.bottom <= label.glyph.top + 0.5f)
    }

    @Test
    fun landscapeHoldTurnsThePillUprightOnAPortraitWindow() {
        val label = layout(
            PxBox(20f, 80f, 60f, 120f),
            viewWidth = 200f,
            viewHeight = 400f,
            textRotation = 270,
        ).single()
        assertEquals(270, label.textRotation)
        assertTrue(label.pill.height > label.pill.width)
    }

    @Test
    fun tapOnTheGlyphHitsThatHanzi() {
        val label = layout(PxBox(20f, 80f, 60f, 120f), viewWidth = 200f, viewHeight = 400f).single()
        assertTrue(label.hit(label.glyph.centerX, label.glyph.centerY))
        assertTrue(!label.hit(0f, 0f))
    }

    private fun layout(
        box: PxBox,
        viewWidth: Float,
        viewHeight: Float,
        vertical: Boolean = false,
        textRotation: Int = 0,
    ) = PinyinLabels.layout(
        glyphs = listOf(ReadingGlyph("你", "nǐ", box, vertical)),
        imageWidth = viewWidth.toInt(),
        imageHeight = viewHeight.toInt(),
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        textRotation = textRotation,
    )
}
