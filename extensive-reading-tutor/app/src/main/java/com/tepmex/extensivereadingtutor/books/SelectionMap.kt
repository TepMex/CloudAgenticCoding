package com.tepmex.extensivereadingtutor.books

import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.SourceSpan

data class MappedSelection(
    val startCodePoint: Int,
    val endCodePoint: Int,
    val quotedText: String,
    val anchors: List<SourceSpan>,
    val contextBefore: String,
    val contextAfter: String,
)

fun utf16ToCodePointRange(text: String, startUtf16: Int, endUtf16: Int): Pair<Int, Int>? {
    if (text.isEmpty()) return null
    val rawStart = startUtf16.coerceIn(0, text.length)
    val rawEnd = endUtf16.coerceIn(0, text.length)
    val lo = minOf(rawStart, rawEnd)
    val hi = maxOf(rawStart, rawEnd)
    if (lo == hi) return null
    val start = snapCodePointBoundary(text, lo, preferEnd = false)
    val end = snapCodePointBoundary(text, hi, preferEnd = true)
    if (start >= end) return null
    return text.codePointCount(0, start) to text.codePointCount(0, end)
}

fun snapCodePointBoundary(text: String, utf16: Int, preferEnd: Boolean): Int {
    if (utf16 <= 0) return 0
    if (utf16 >= text.length) return text.length
    if (Character.isLowSurrogate(text[utf16]) && Character.isHighSurrogate(text[utf16 - 1])) {
        return if (preferEnd) utf16 + 1 else utf16 - 1
    }
    return utf16
}

fun mapSelection(fragment: BookFragment, startCodePoint: Int, endCodePoint: Int, contextCodePoints: Int = 48): MappedSelection? {
    val text = fragment.originalText
    val total = text.codePointLength()
    if (startCodePoint < 0 || endCodePoint > total || startCodePoint >= endCodePoint) return null
    val quoted = text.sliceCodePoints(startCodePoint, endCodePoint)
    val anchors = anchorsForRange(fragment, startCodePoint, endCodePoint)
    if (anchors.isEmpty()) return null
    val beforeStart = (startCodePoint - contextCodePoints).coerceAtLeast(0)
    val afterEnd = (endCodePoint + contextCodePoints).coerceAtMost(total)
    return MappedSelection(
        startCodePoint = startCodePoint,
        endCodePoint = endCodePoint,
        quotedText = quoted,
        anchors = anchors,
        contextBefore = text.sliceCodePoints(beforeStart, startCodePoint),
        contextAfter = text.sliceCodePoints(endCodePoint, afterEnd),
    )
}

fun anchorsForRange(fragment: BookFragment, startCodePoint: Int, endCodePoint: Int): List<SourceSpan> {
    var cursor = 0
    val spans = mutableListOf<SourceSpan>()
    fragment.anchors.forEachIndexed { index, anchor ->
        if (index > 0 && fragment.anchors[index - 1].blockId != anchor.blockId) {
            cursor += 1
        }
        val anchorStart = cursor
        val anchorLength = anchor.quote.codePointLength()
        val anchorEnd = cursor + anchorLength
        val overlapStart = maxOf(startCodePoint, anchorStart)
        val overlapEnd = minOf(endCodePoint, anchorEnd)
        if (overlapStart < overlapEnd) {
            val localStart = overlapStart - anchorStart
            val localEnd = overlapEnd - anchorStart
            spans += SourceSpan(
                quote = anchor.quote.sliceCodePoints(localStart, localEnd),
                blockId = anchor.blockId,
                startCodePoint = anchor.startCodePoint + localStart,
                endCodePoint = anchor.startCodePoint + localEnd,
            )
        }
        cursor = anchorEnd
    }
    return spans
}

fun selectionBounds(fragment: BookFragment, spans: List<SourceSpan>): Pair<Int, Int>? {
    if (spans.isEmpty()) return null
    var cursor = 0
    var start: Int? = null
    var end: Int? = null
    fragment.anchors.forEachIndexed { index, anchor ->
        if (index > 0 && fragment.anchors[index - 1].blockId != anchor.blockId) cursor += 1
        val anchorStart = cursor
        val anchorEnd = cursor + anchor.quote.codePointLength()
        val hit = spans.firstOrNull { span ->
            val startPoint = span.startCodePoint
            val endPoint = span.endCodePoint
            span.blockId == anchor.blockId && startPoint != null && endPoint != null &&
                startPoint >= anchor.startCodePoint && endPoint <= anchor.endCodePoint && startPoint < endPoint
        }
        val hitStart = hit?.startCodePoint
        val hitEnd = hit?.endCodePoint
        if (hit != null && hitStart != null && hitEnd != null && hit.blockId == anchor.blockId &&
            hitStart >= anchor.startCodePoint && hitEnd <= anchor.endCodePoint
        ) {
            val localStart = anchorStart + (hitStart - anchor.startCodePoint)
            val localEnd = anchorStart + (hitEnd - anchor.startCodePoint)
            start = minOf(start ?: localStart, localStart)
            end = maxOf(end ?: localEnd, localEnd)
        }
        cursor = anchorEnd
    }
    val from = start ?: return null
    val to = end ?: return null
    if (from >= to) return null
    return from to to
}

enum class QuoteLocate { UNIQUE, MISSING, AMBIGUOUS }

data class LocatedQuote(val kind: QuoteLocate, val exact: String? = null, val span: SourceSpan? = null)

fun locateQuote(fragment: BookFragment, quote: String): LocatedQuote {
    val normalizedFragment = nfc(fragment.originalText)
    val needle = nfc(quote).trim()
    if (needle.isEmpty()) return LocatedQuote(QuoteLocate.MISSING)
    val hits = mutableListOf<Int>()
    var from = 0
    while (from <= normalizedFragment.length) {
        val at = normalizedFragment.indexOf(needle, from)
        if (at < 0) break
        hits += at
        from = at + needle.length
    }
    if (hits.isEmpty()) return LocatedQuote(QuoteLocate.MISSING)
    if (hits.size > 1) return LocatedQuote(QuoteLocate.AMBIGUOUS)
    val exact = normalizedFragment.substring(hits.single(), hits.single() + needle.length)
    val span = spanAtCodePoints(fragment, normalizedFragment.codePointCount(0, hits.single()), exact)
    return LocatedQuote(QuoteLocate.UNIQUE, exact, span)
}

private fun spanAtCodePoints(fragment: BookFragment, start: Int, exact: String): SourceSpan {
    val end = start + exact.codePointLength()
    val mapped = anchorsForRange(fragment, start, end)
    return if (mapped.size == 1) mapped.single() else SourceSpan(exact, mapped.firstOrNull()?.blockId, mapped.firstOrNull()?.startCodePoint, mapped.lastOrNull()?.endCodePoint)
}
