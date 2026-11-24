package com.tepmex.hanziinfogf14.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class ScoredHanzi(
    val hanzi: String,
    val distance: Float,
)

/**
 * Image embeddings of the catalog, one row per character in catalog order.
 *
 * File layout, little-endian:
 * `HZE1`, version u16, dim u16, count u32, vision tokens u16, image size u16,
 * font px u16, reserved u16, then count records of code point u32 plus dim float16 values.
 * Vectors are L2-normalized again after the float16 values are widened.
 */
class GlyphEmbeddingIndex(
    val hanzi: List<String>,
    val dim: Int,
    val matrix: FloatArray,
    val visionTokens: Int,
    val imageSize: Int,
    val fontPx: Int,
) {
    private val rowOf: Map<String, Int> = hanzi.withIndex().associate { it.value to it.index }

    fun contains(character: String): Boolean = character in rowOf

    fun nearest(character: String, limit: Int, exclude: Set<String>): List<ScoredHanzi> {
        val row = rowOf[character] ?: return emptyList()
        return nearest(matrix, row * dim, limit, exclude + character)
    }

    fun nearest(vector: FloatArray, limit: Int, exclude: Set<String>): List<ScoredHanzi> {
        require(vector.size == dim) { "dim ${vector.size}" }
        return nearest(vector, 0, limit, exclude)
    }

    private fun nearest(query: FloatArray, queryOffset: Int, limit: Int, exclude: Set<String>): List<ScoredHanzi> {
        if (limit <= 0) return emptyList()
        val scored = ArrayList<ScoredHanzi>(hanzi.size)
        val order = ArrayList<Int>(hanzi.size)
        for (index in hanzi.indices) {
            val character = hanzi[index]
            if (character in exclude) continue
            val dot = dot(matrix, index * dim, query, queryOffset, dim).coerceIn(-1f, 1f)
            scored.add(ScoredHanzi(character, 1f - dot))
            order.add(index)
        }
        val ranked = scored.indices.sortedWith(compareBy<Int> { scored[it].distance }.thenBy { order[it] })
        return ranked.take(limit).map { scored[it] }
    }

    companion object {
        const val ASSET = "glyph_embeddings.bin"
        private val MAGIC = byteArrayOf('H'.code.toByte(), 'Z'.code.toByte(), 'E'.code.toByte(), '1'.code.toByte())
        private const val HEADER_BYTES = 20

        fun read(bytes: ByteArray): GlyphEmbeddingIndex {
            require(bytes.size >= HEADER_BYTES) { "glyph embedding length ${bytes.size}" }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4)
            buffer.get(magic)
            require(magic.contentEquals(MAGIC)) { "glyph embedding magic" }
            val version = buffer.short.toInt() and 0xFFFF
            val dim = buffer.short.toInt() and 0xFFFF
            val count = buffer.int
            val visionTokens = buffer.short.toInt() and 0xFFFF
            val imageSize = buffer.short.toInt() and 0xFFFF
            val fontPx = buffer.short.toInt() and 0xFFFF
            buffer.short // reserved
            require(version == 1) { "glyph embedding version $version" }
            require(dim > 0 && count > 0) { "glyph embedding shape $count x $dim" }
            val expected = HEADER_BYTES + count * (4 + dim * 2)
            require(bytes.size == expected) { "glyph embedding length ${bytes.size}, expected $expected" }
            val characters = ArrayList<String>(count)
            val matrix = FloatArray(count * dim)
            val seen = HashSet<String>(count)
            for (row in 0 until count) {
                val codePoint = buffer.int
                require(codePoint in 0..0x10FFFF) { "code point $codePoint" }
                val character = String(Character.toChars(codePoint))
                require(seen.add(character)) { "duplicate $character" }
                characters.add(character)
                var norm = 0.0
                val offset = row * dim
                for (column in 0 until dim) {
                    val value = float16ToFloat(buffer.short.toInt() and 0xFFFF).toDouble()
                    matrix[offset + column] = value.toFloat()
                    norm += value * value
                }
                if (norm > 0.0) {
                    val inverse = 1.0 / sqrt(norm)
                    for (column in 0 until dim) {
                        matrix[offset + column] = (matrix[offset + column] * inverse).toFloat()
                    }
                }
            }
            return GlyphEmbeddingIndex(characters, dim, matrix, visionTokens, imageSize, fontPx)
        }

        fun float16ToFloat(bits: Int): Float {
            val sign = (bits and 0x8000) shl 16
            val exponent = (bits and 0x7C00) ushr 10
            val fraction = bits and 0x03FF
            val decoded = when (exponent) {
                0 -> if (fraction == 0) {
                    sign
                } else {
                    var mantissa = fraction
                    var shift = -1
                    while (mantissa and 0x400 == 0) {
                        mantissa = mantissa shl 1
                        shift--
                    }
                    val exp32 = 127 - 15 + 1 + shift
                    sign or (exp32 shl 23) or ((mantissa and 0x3FF) shl 13)
                }
                0x1F -> sign or 0x7F800000 or (fraction shl 13)
                else -> sign or ((exponent - 15 + 127) shl 23) or (fraction shl 13)
            }
            return Float.fromBits(decoded)
        }

        private fun dot(left: FloatArray, leftOffset: Int, right: FloatArray, rightOffset: Int, dim: Int): Float {
            var sum = 0.0
            for (index in 0 until dim) {
                sum += left[leftOffset + index].toDouble() * right[rightOffset + index].toDouble()
            }
            return sum.toFloat()
        }
    }
}
