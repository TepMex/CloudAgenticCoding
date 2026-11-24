package com.tepmex.hanziinfogf14.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GlyphEmbeddingIndexTest {
    @Test
    fun float16RoundTrip() {
        assertEquals(1f, GlyphEmbeddingIndex.float16ToFloat(0x3C00), 0f)
        assertEquals(0.5f, GlyphEmbeddingIndex.float16ToFloat(0x3800), 0f)
        assertEquals(-2f, GlyphEmbeddingIndex.float16ToFloat(0xC000), 0f)
        assertEquals(0f, GlyphEmbeddingIndex.float16ToFloat(0x0000), 0f)
    }

    @Test
    fun nearestBreaksTiesByCatalogOrderAndSkipsExcluded() {
        val index = GlyphEmbeddingIndex.read(
            file(
                characters = listOf("一", "二", "三"),
                rows = listOf(
                    intArrayOf(0x3C00, 0x0000),
                    intArrayOf(0x0000, 0x3C00),
                    intArrayOf(0x3C00, 0x3C00),
                ),
            ),
        )
        assertEquals(listOf("三", "二"), index.nearest("一", limit = 2, exclude = emptySet()).map { it.hanzi })
        assertEquals(listOf("二"), index.nearest("一", limit = 2, exclude = setOf("三")).map { it.hanzi })
        assertEquals(true, index.nearest("一", limit = 3, exclude = emptySet()).none { it.hanzi == "一" })
        val distances = index.nearest("一", limit = 2, exclude = emptySet()).map { it.distance }
        assertEquals(true, distances[0] < distances[1])
    }

    @Test
    fun rejectsAShortFile() {
        assertThrows(IllegalArgumentException::class.java) {
            GlyphEmbeddingIndex.read(byteArrayOf('H'.code.toByte(), 'Z'.code.toByte()))
        }
    }

    private fun file(characters: List<String>, rows: List<IntArray>): ByteArray {
        val dim = rows.first().size
        val buffer = ByteBuffer.allocate(20 + characters.size * (4 + dim * 2)).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("HZE1".toByteArray(Charsets.US_ASCII))
        buffer.putShort(1)
        buffer.putShort(dim.toShort())
        buffer.putInt(characters.size)
        buffer.putShort(70)
        buffer.putShort(224)
        buffer.putShort(156)
        buffer.putShort(0)
        characters.forEachIndexed { index, character ->
            buffer.putInt(character.codePointAt(0))
            for (bits in rows[index]) buffer.putShort(bits.toShort())
        }
        return buffer.array()
    }
}
