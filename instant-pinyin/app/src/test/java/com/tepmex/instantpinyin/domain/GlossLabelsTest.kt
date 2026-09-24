package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GlossLabelsTest {
    private val lexicon = RuGlossLexicon(
        words = mapOf("你好" to "привет"),
        chars = mapOf("汉" to "хань", "你" to "ты", "好" to "хороший"),
    )

    @Test
    fun horizontalWordPutsTheGlossUnderTheSpan() {
        val labels = GlossLabels.layout(
            glyphs = listOf(
                glyph("你", PxBox(20f, 80f, 60f, 120f)),
                glyph("好", PxBox(60f, 80f, 100f, 120f)),
            ),
            lexicon = lexicon,
            imageWidth = 200,
            imageHeight = 400,
            viewWidth = 200f,
            viewHeight = 400f,
        )
        val label = labels.single()
        assertEquals("你好", label.text)
        assertEquals("привет", label.gloss)
        assertTrue(label.below)
        assertTrue(label.pill.top >= label.span.bottom - 0.5f)
        assertTrue(abs(label.pill.centerX - label.span.centerX) < 1f)
    }

    @Test
    fun aGapDoesNotJoinCharactersIntoAWord() {
        val labels = GlossLabels.layout(
            glyphs = listOf(
                glyph("你", PxBox(0f, 40f, 30f, 70f)),
                glyph("好", PxBox(70f, 40f, 100f, 70f)),
            ),
            lexicon = lexicon,
            imageWidth = 200,
            imageHeight = 200,
            viewWidth = 200f,
            viewHeight = 200f,
        )
        assertEquals(listOf("你", "好"), labels.map { it.text })
        assertTrue(labels.all { it.pill.top >= it.span.bottom - 0.5f })
    }

    @Test
    fun verticalColumnPutsTheGlossBesideTheWord() {
        val labels = GlossLabels.layout(
            glyphs = listOf(
                glyph("你", PxBox(180f, 20f, 210f, 80f)),
                glyph("好", PxBox(180f, 80f, 210f, 140f)),
            ),
            lexicon = lexicon,
            imageWidth = 400,
            imageHeight = 400,
            viewWidth = 400f,
            viewHeight = 400f,
        )
        val label = labels.single()
        assertTrue(!label.below)
        assertTrue(label.pill.right <= label.span.left + 0.5f)
    }

    @Test
    fun missingCharacterDrawsNoGloss() {
        val labels = GlossLabels.layout(
            glyphs = listOf(glyph("龙", PxBox(10f, 10f, 40f, 40f))),
            lexicon = lexicon,
            imageWidth = 100,
            imageHeight = 100,
            viewWidth = 100f,
            viewHeight = 100f,
        )
        assertTrue(labels.isEmpty())
    }

    private fun glyph(hanzi: String, box: PxBox) = ReadingGlyph(hanzi, hanzi, box)
}