package com.tepmex.localtts.tts

import java.io.File

/**
 * Minimal BertWordPieceTokenizer compatible with vosk-tts models.
 */
class BertWordPieceTokenizer(
    vocabFile: File,
    private val unkToken: String = "[UNK]",
    private val lowercase: Boolean = true,
) {
    private val vocab: Map<String, Int>
    private val unkId: Int
    private val clsId: Int
    private val sepId: Int

    init {
        val map = LinkedHashMap<String, Int>()
        vocabFile.forEachLine { line ->
            val token = line.trim()
            if (token.isNotEmpty()) {
                map[token] = map.size
            }
        }
        vocab = map
        unkId = vocab[unkToken] ?: 0
        clsId = vocab["[CLS]"] ?: 101
        sepId = vocab["[SEP]"] ?: 102
    }

    data class Encoding(
        val ids: LongArray,
        val tokens: List<String>,
        val attentionMask: LongArray,
        val typeIds: LongArray,
    )

    fun encode(text: String): Encoding {
        val normalized = if (lowercase) text.lowercase() else text
        val wordPieces = mutableListOf<String>()
        wordPieces.add("[CLS]")
        for (token in basicTokenize(normalized)) {
            wordPieces.addAll(wordPieceTokenize(token))
        }
        wordPieces.add("[SEP]")

        val ids = LongArray(wordPieces.size) { i -> vocab[wordPieces[i]]?.toLong() ?: unkId.toLong() }
        val attention = LongArray(wordPieces.size) { 1L }
        val typeIds = LongArray(wordPieces.size) { 0L }
        return Encoding(ids, wordPieces, attention, typeIds)
    }

    private fun basicTokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        for (match in BASIC_TOKEN_PATTERN.findAll(text)) {
            val piece = match.value
            if (piece.isNotBlank()) {
                tokens.add(piece)
            }
        }
        return tokens
    }

    private fun wordPieceTokenize(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        if (vocab.containsKey(word)) return listOf(word)

        val output = mutableListOf<String>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var cur: String? = null
            while (start < end) {
                var substr = word.substring(start, end)
                if (start > 0) {
                    substr = "##$substr"
                }
                if (vocab.containsKey(substr)) {
                    cur = substr
                    break
                }
                end--
            }
            if (cur == null) {
                output.add(unkToken)
                break
            }
            output.add(cur)
            start = end
        }
        return output
    }

    companion object {
        private val BASIC_TOKEN_PATTERN = Regex("""(?U)\p{L}+|\p{N}+|[^\p{L}\p{N}\s]+""")
    }
}
