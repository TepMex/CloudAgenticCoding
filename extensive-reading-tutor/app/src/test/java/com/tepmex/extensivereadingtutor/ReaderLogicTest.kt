package com.tepmex.extensivereadingtutor

import com.tepmex.extensivereadingtutor.books.FragmentSelector
import com.tepmex.extensivereadingtutor.books.layoutReaderPages
import com.tepmex.extensivereadingtutor.books.pageIndexFor
import com.tepmex.extensivereadingtutor.books.placeKey
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.TextCursor
import com.tepmex.extensivereadingtutor.books.normalizedUtf16Selection
import com.tepmex.extensivereadingtutor.ui.PinyinSyllable
import com.tepmex.extensivereadingtutor.ui.pullUpRelease
import com.tepmex.extensivereadingtutor.ui.selectionFromPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLogicTest {
    @Test
    fun pagesCoverTheBookAndKeepAStoredFragment() {
        val blocks = listOf(
            block(0, "雷打不动。他每天五点起床读书。"),
            block(1, "朋友问他为什么如此坚持。"),
            block(2, "他说这是原则，不是勇气。"),
        )
        val keptSlices = FragmentSelector.select(blocks, TextCursor(0, 0), 8, 20)
        val stored = FragmentSelector.materialize(BookId("b"), FragmentId("kept"), keptSlices)
        val pages = layoutReaderPages(blocks, listOf(stored), targetCodePoints = 40, hardMaxCodePoints = 80)
        assertTrue(pages.size >= 2)
        assertEquals(stored.originalText, pages.first().text)
        assertEquals(FragmentId("kept"), pages.first().fragmentId)
        val nextCursor = FragmentSelector.advance(blocks, stored)
        val next = FragmentSelector.materialize(
            BookId("b"),
            FragmentId("next"),
            FragmentSelector.select(blocks, nextCursor, 40, 80),
        )
        assertEquals(next.originalText, pages[1].text)
        assertEquals(null, pages[1].fragmentId)
        assertEquals(1, pageIndexFor(pages, pages[1].placeKey(), TextCursor(0, 0)))
        assertEquals(1, pageIndexFor(pages, null, pages[1].start))
    }

    @Test
    fun pullLoadsOnlyAfterTheHoldAtTheEnd() {
        assertFalse(pullUpRelease(atEnd = false, pullPx = 200f, armed = true, thresholdPx = 72f))
        assertFalse(pullUpRelease(atEnd = true, pullPx = 40f, armed = true, thresholdPx = 72f))
        assertFalse(pullUpRelease(atEnd = true, pullPx = 200f, armed = false, thresholdPx = 72f))
        assertFalse(pullUpRelease(atEnd = true, pullPx = Float.NaN, armed = true, thresholdPx = 72f))
        assertTrue(pullUpRelease(atEnd = true, pullPx = 72f, armed = true, thresholdPx = 72f))
    }

    @Test
    fun pressSelectionKeepsASupplementaryCharacterWhole() {
        val text = "甲\uD840\uDC00乙丙"
        val first = selectionFromPress(text, null, 1)
        assertEquals(1 to 3, first)
        assertEquals("\uD840\uDC00", text.substring(first!!.first, first.second))
        val grown = selectionFromPress(text, first, 3)
        assertEquals("\uD840\uDC00乙", text.substring(grown!!.first, grown.second))
        val caretOnLowSurrogate = normalizedUtf16Selection(text, 2, 2)
        assertEquals(1 to 3, caretOnLowSurrogate)
        assertFalse(Character.isHighSurrogate(text[grown.second - 1]) && grown.second < text.length && Character.isLowSurrogate(text[grown.second]))
    }

    @Test
    fun rubyReadingUsesToneMarks() {
        assertEquals("nǐ", PinyinSyllable.of("你"))
        assertEquals("léi", PinyinSyllable.of("雷"))
        assertEquals("", PinyinSyllable.of("。"))
    }

    private fun block(order: Int, text: String) = BookBlock(
        BlockId("block-$order"), BookId("b"), 0, "c.xhtml", order, text, "h$order", null,
    )
}
