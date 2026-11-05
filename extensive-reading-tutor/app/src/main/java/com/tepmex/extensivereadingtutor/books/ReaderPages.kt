package com.tepmex.extensivereadingtutor.books

import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.TextCursor

data class ReaderPage(
    val start: TextCursor,
    val text: String,
    val chapter: String?,
    val fragmentId: FragmentId?,
)

fun ReaderPage.placeKey(): String = "${start.blockOrder}:${start.codePoint}"

fun parsePlace(place: String): TextCursor? {
    val parts = place.split(':')
    if (parts.size != 2) return null
    val order = parts[0].toIntOrNull() ?: return null
    val point = parts[1].toIntOrNull() ?: return null
    if (order < 0 || point < 0) return null
    return TextCursor(order, point)
}

fun BookFragment.startsAt(blocks: List<BookBlock>, cursor: TextCursor): Boolean {
    val first = anchors.firstOrNull() ?: return false
    val block = blocks.firstOrNull { it.id == first.blockId } ?: return false
    return block.orderInBook == cursor.blockOrder && first.startCodePoint == cursor.codePoint
}

/**
 * Pages cover the book once. A stored fragment that starts at the cursor is kept as-is,
 * so a later fragment size does not rewrite it. The unread tail is sliced with the current target.
 */
fun layoutReaderPages(
    blocks: List<BookBlock>,
    stored: List<BookFragment>,
    targetCodePoints: Int,
    hardMaxCodePoints: Int,
): List<ReaderPage> {
    val ordered = blocks.sortedBy { it.orderInBook }
    if (ordered.isEmpty()) return emptyList()
    var cursor = TextCursor(ordered.first().orderInBook, 0)
    val pages = mutableListOf<ReaderPage>()
    val limit = ordered.sumOf { it.text.codePointLength() } + 2
    var steps = 0
    while (steps < limit) {
        steps += 1
        val hit = stored.firstOrNull { it.startsAt(ordered, cursor) && it.originalText.isNotEmpty() }
        if (hit != null) {
            pages += ReaderPage(cursor, hit.originalText, hit.chapterTitle, hit.id)
            val next = FragmentSelector.advance(ordered, hit)
            if (next == cursor) break
            cursor = next
            continue
        }
        val slices = FragmentSelector.select(ordered, cursor, targetCodePoints, hardMaxCodePoints)
        if (slices.isEmpty()) break
        val virtual = FragmentSelector.materialize(ordered.first().bookId, FragmentId("page"), slices)
        if (virtual.originalText.isEmpty()) break
        pages += ReaderPage(cursor, virtual.originalText, virtual.chapterTitle, null)
        val next = FragmentSelector.advance(ordered, virtual)
        if (next == cursor) break
        cursor = next
    }
    return pages
}

fun pageIndexFor(pages: List<ReaderPage>, bookmark: String?, cursor: TextCursor): Int {
    if (pages.isEmpty()) return 0
    val saved = bookmark?.let(::parsePlace)
    if (saved != null) {
        val exact = pages.indexOfFirst { it.start == saved }
        if (exact >= 0) return exact
    }
    val atCursor = pages.indexOfFirst { it.start == cursor }
    if (atCursor >= 0) return atCursor
    val covered = pages.indexOfLast { it.start.blockOrder < cursor.blockOrder || (it.start.blockOrder == cursor.blockOrder && it.start.codePoint <= cursor.codePoint) }
    return covered.coerceAtLeast(0)
}
