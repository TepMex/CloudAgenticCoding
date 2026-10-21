package com.tepmex.extensivereadingtutor.books

import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.BookBlock
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.TextAnchor
import com.tepmex.extensivereadingtutor.domain.TextCursor
import java.security.MessageDigest
import java.text.Normalizer

fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun sha256(text: String): String = sha256(text.toByteArray(Charsets.UTF_8))

fun String.codePointLength(): Int = codePointCount(0, length)

fun String.sliceCodePoints(start: Int, end: Int): String {
    require(start in 0..end) { "bad range $start..$end" }
    val total = codePointLength()
    require(end <= total) { "end $end past $total" }
    val startUtf = offsetByCodePoints(0, start)
    val endUtf = offsetByCodePoints(0, end)
    return substring(startUtf, endUtf)
}

fun String.takeCodePoints(count: Int): String = sliceCodePoints(0, minOf(count, codePointLength()))

fun String.tailCodePoints(count: Int): String {
    val total = codePointLength()
    return sliceCodePoints((total - count).coerceAtLeast(0), total)
}

fun nfc(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

fun primaryLanguage(raw: String?): String? {
    val token = raw?.trim()?.replace('_', '-')?.lowercase().orEmpty()
    if (token.isEmpty() || token == "und") return null
    return token.substringBefore('-')
}

private val sentenceEnders = setOf('。', '！', '？', '.', '!', '?')
private val clauseEnders = setOf('，', ',', '、', '；', ';', '：', ':')

fun splitEnd(text: String, startCp: Int, budgetCp: Int): Int {
    val total = text.codePointLength()
    val endCp = minOf(startCp + budgetCp, total)
    if (endCp <= startCp) return minOf(startCp + 1, total)
    if (endCp == total) return total
    var cp = startCp
    var index = text.offsetByCodePoints(0, startCp)
    var lastSentence = -1
    var lastClause = -1
    var lastSpace = -1
    while (cp < endCp) {
        val code = text.codePointAt(index)
        val next = cp + 1
        if (code <= 0xFFFF) {
            val ch = code.toChar()
            if (ch in sentenceEnders) lastSentence = next
            if (ch in clauseEnders) lastClause = next
            if (ch == ' ' || ch == '\n' || ch == '\t') lastSpace = next
        }
        index += Character.charCount(code)
        cp = next
    }
    val cut = listOf(lastSentence, lastClause, lastSpace).firstOrNull { it > startCp } ?: endCp
    return cut
}

data class TextSlice(
    val block: BookBlock,
    val startCodePoint: Int,
    val endCodePoint: Int,
) {
    val text: String get() = block.text.sliceCodePoints(startCodePoint, endCodePoint)
}

object FragmentSelector {
    const val VERSION = "paragraph-sentence-v1"

    fun targetFor(base: Int, difficultyMultiplier: Double): Int =
        (base * difficultyMultiplier).toInt().coerceIn(40, 1200)

    fun hardMax(target: Int): Int = maxOf(target * 2, target + 40)

    fun select(
        blocks: List<BookBlock>,
        cursor: TextCursor,
        targetCodePoints: Int,
        hardMaxCodePoints: Int,
    ): List<TextSlice> {
        require(targetCodePoints >= 1)
        require(hardMaxCodePoints >= targetCodePoints)
        val ordered = blocks.sortedBy { it.orderInBook }
        var index = ordered.indexOfFirst { it.orderInBook >= cursor.blockOrder }
        if (index < 0) return emptyList()
        var cp = if (ordered[index].orderInBook == cursor.blockOrder) cursor.codePoint else 0
        val out = mutableListOf<TextSlice>()
        var taken = 0
        while (index < ordered.size) {
            val block = ordered[index]
            val length = block.text.codePointLength()
            if (cp >= length) {
                index += 1
                cp = 0
                continue
            }
            val rest = length - cp
            if (out.isNotEmpty() && taken >= targetCodePoints) break
            if (out.isNotEmpty() && taken + rest > targetCodePoints) break
            val room = hardMaxCodePoints - taken
            if (rest > room) {
                if (room < 1) break
                val end = splitEnd(block.text, cp, room)
                if (end <= cp) break
                out += TextSlice(block, cp, end)
                break
            }
            out += TextSlice(block, cp, cp + rest)
            taken += rest
            index += 1
            cp = 0
            if (taken >= targetCodePoints) break
        }
        return out
    }

    fun materialize(bookId: BookId, id: FragmentId, slices: List<TextSlice>): BookFragment {
        require(slices.isNotEmpty())
        val anchors = slices.map { slice ->
            val quote = slice.text
            TextAnchor(
                bookId = bookId,
                spineIndex = slice.block.spineIndex,
                spineHref = slice.block.spineHref,
                blockId = slice.block.id,
                startCodePoint = slice.startCodePoint,
                endCodePoint = slice.endCodePoint,
                quote = quote,
                quoteSha256 = sha256(quote),
            )
        }
        val text = render(anchors)
        val chapter = slices.firstNotNullOfOrNull { it.block.chapterTitle }
        return BookFragment(
            id = id,
            bookId = bookId,
            anchors = anchors,
            originalText = text,
            textHash = sha256(text),
            chapterTitle = chapter,
            chapterHref = slices.first().block.spineHref,
            codePointLength = text.codePointLength(),
            segmentationVersion = VERSION,
        )
    }

    fun render(anchors: List<TextAnchor>): String = buildString {
        anchors.forEachIndexed { index, anchor ->
            if (index > 0 && anchors[index - 1].blockId != anchor.blockId) append('\n')
            append(anchor.quote)
        }
    }

    fun advance(blocks: List<BookBlock>, fragment: BookFragment): TextCursor {
        val last = fragment.anchors.last()
        val block = blocks.first { it.id == last.blockId }
        return if (last.endCodePoint >= block.text.codePointLength()) {
            val next = blocks.filter { it.orderInBook > block.orderInBook }.minByOrNull { it.orderInBook }
            if (next == null) TextCursor(block.orderInBook + 1, 0) else TextCursor(next.orderInBook, 0)
        } else {
            TextCursor(block.orderInBook, last.endCodePoint)
        }
    }
}

fun locateInFragment(fragment: BookFragment, quote: String): SourceHit? {
    val normalizedFragment = nfc(fragment.originalText)
    val normalizedQuote = nfc(quote).trim()
    if (normalizedQuote.isEmpty()) return null
    val at = normalizedFragment.indexOf(normalizedQuote)
    if (at < 0) return null
    val exact = normalizedFragment.substring(at, at + normalizedQuote.length)
    val span = spanFor(fragment, exact)
    return SourceHit(exact, span)
}

data class SourceHit(val exact: String, val span: com.tepmex.extensivereadingtutor.domain.SourceSpan)

fun spanFor(fragment: BookFragment, exactQuote: String): com.tepmex.extensivereadingtutor.domain.SourceSpan {
    for (anchor in fragment.anchors) {
        val at = anchor.quote.indexOf(exactQuote)
        if (at >= 0) {
            val start = anchor.startCodePoint + anchor.quote.codePointCount(0, at)
            val end = start + exactQuote.codePointCount(0, exactQuote.length)
            return com.tepmex.extensivereadingtutor.domain.SourceSpan(
                quote = exactQuote,
                blockId = anchor.blockId,
                startCodePoint = start,
                endCodePoint = end,
            )
        }
    }
    return com.tepmex.extensivereadingtutor.domain.SourceSpan(exactQuote, null, null, null)
}

fun anchorMatchesBlock(anchor: TextAnchor, block: BookBlock): Boolean {
    if (anchor.blockId != block.id) return false
    val slice = block.text.sliceCodePoints(anchor.startCodePoint, anchor.endCodePoint)
    return slice == anchor.quote && sha256(slice) == anchor.quoteSha256
}

fun precedingText(blocks: List<BookBlock>, fragment: BookFragment, maxCodePoints: Int = 400): String {
    val first = fragment.anchors.first()
    val parts = mutableListOf<String>()
    val ordered = blocks.sortedBy { it.orderInBook }
    for (block in ordered) {
        if (block.orderInBook < ordered.first { it.id == first.blockId }.orderInBook) {
            parts += block.text
        } else if (block.id == first.blockId && first.startCodePoint > 0) {
            parts += block.text.sliceCodePoints(0, first.startCodePoint)
        }
    }
    return parts.joinToString("\n").tailCodePoints(maxCodePoints)
}

fun blockIdOf(bookId: BookId, order: Int): BlockId = BlockId("${bookId.value}-b$order")
