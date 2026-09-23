package com.tepmex.instantpinyin.domain

import com.tepmex.instantpinyin.ocr.OcrLine
import com.tepmex.instantpinyin.ocr.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayTrackerTest {
    @Test
    fun matchedGlyphMovesPartwayTowardTheNewBox() {
        val previous = listOf(
            OverlayTracker.Track("你", "nǐ", PxBox(0f, 0f, 10f, 10f)),
        )
        val next = OverlayTracker.step(
            previous,
            listOf(ReadingGlyph("你", "nǐ", PxBox(10f, 0f, 20f, 10f))),
            smooth = 0.5f,
        )
        assertEquals(1, next.size)
        assertEquals(5f, next[0].box.left, 0.01f)
        assertEquals(15f, next[0].box.right, 0.01f)
        assertEquals(0, next[0].misses)
    }

    @Test
    fun aMissedFrameKeepsTheLabelThenDropsIt() {
        val held = OverlayTracker.step(
            listOf(OverlayTracker.Track("好", "hǎo", PxBox(0f, 0f, 12f, 12f))),
            emptyList(),
        )
        assertEquals(1, held.size)
        assertEquals(1, held[0].misses)
        assertTrue(OverlayTracker.step(held, emptyList()).isEmpty())
    }
}

class OverlaySessionTest {
    @Test
    fun aBlankFrameKeepsThePreviousGlyphOnce() {
        val session = OverlaySession()
        val first = session.observe(listOf(OcrLine("汉", TextBox(0, 0, 20, 20, 1f))), 100, 80)
        assertEquals(listOf("汉"), first.map { it.hanzi })
        val held = session.observe(emptyList(), 100, 80)
        assertEquals(listOf("汉"), held.map { it.hanzi })
        assertTrue(session.observe(emptyList(), 100, 80).isEmpty())
    }

    @Test
    fun aNewFrameSizeDropsStaleGlyphs() {
        val session = OverlaySession()
        session.observe(listOf(OcrLine("汉", TextBox(0, 0, 20, 20, 1f))), 100, 80)
        assertTrue(session.observe(emptyList(), 90, 80).isEmpty())
    }
}
