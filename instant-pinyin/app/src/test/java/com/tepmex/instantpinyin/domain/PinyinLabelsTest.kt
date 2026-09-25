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
    fun neighborsAlternateTiersSoTheyDoNotOverlap() {
        val labels = PinyinLabels.layout(
            glyphs = listOf(
                ReadingGlyph("你", "nǐ", PxBox(20f, 180f, 70f, 230f)),
                ReadingGlyph("好", "hǎo", PxBox(70f, 180f, 120f, 230f)),
                ReadingGlyph("吗", "ma", PxBox(120f, 180f, 170f, 230f)),
            ),
            imageWidth = 400,
            imageHeight = 400,
            viewWidth = 400f,
            viewHeight = 400f,
        )
        assertEquals(listOf("你", "好", "吗"), labels.map { it.hanzi })
        val first = labels[0]
        val second = labels[1]
        val third = labels[2]
        assertTrue(second.pill.bottom <= first.pill.top + 0.5f)
        assertTrue(second.pill.bottom <= third.pill.top + 0.5f)
        assertTrue(first.pill.bottom <= first.glyph.top + 0.5f)
        assertTrue(!overlaps(first.pill, second.pill))
        assertTrue(!overlaps(first.pill, third.pill))
        assertTrue(!overlaps(second.pill, third.pill))
    }

    @Test
    fun longPinyinOnTheSameTierShrinksInsteadOfOverlapping() {
        val labels = PinyinLabels.layout(
            glyphs = listOf(
                ReadingGlyph("装", "zhuāng", PxBox(10f, 200f, 50f, 240f)),
                ReadingGlyph("中", "zhōng", PxBox(50f, 200f, 90f, 240f)),
                ReadingGlyph("状", "zhuàng", PxBox(90f, 200f, 130f, 240f)),
            ),
            imageWidth = 400,
            imageHeight = 400,
            viewWidth = 400f,
            viewHeight = 400f,
        )
        assertTrue(!overlaps(labels[0].pill, labels[2].pill))
        assertTrue(labels[0].pill.right <= labels[2].pill.left + 0.5f)
    }

    @Test
    fun verticalNeighborsUseTwoColumns() {
        val labels = PinyinLabels.layout(
            glyphs = listOf(
                ReadingGlyph("你", "nǐ", PxBox(40f, 20f, 80f, 70f), vertical = true),
                ReadingGlyph("好", "hǎo", PxBox(40f, 70f, 80f, 120f), vertical = true),
                ReadingGlyph("吗", "ma", PxBox(40f, 120f, 80f, 170f), vertical = true),
            ),
            imageWidth = 400,
            imageHeight = 400,
            viewWidth = 400f,
            viewHeight = 400f,
        )
        assertTrue(labels[1].pill.left >= labels[0].pill.right - 0.5f)
        assertTrue(!overlaps(labels[0].pill, labels[1].pill))
        assertTrue(!overlaps(labels[0].pill, labels[2].pill))
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

    private fun overlaps(a: PxBox, b: PxBox): Boolean =
        a.left < b.right - 0.5f && b.left < a.right - 0.5f && a.top < b.bottom - 0.5f && b.top < a.bottom - 0.5f
}
