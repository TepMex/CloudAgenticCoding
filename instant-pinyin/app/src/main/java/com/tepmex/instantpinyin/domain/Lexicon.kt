package com.tepmex.instantpinyin.domain

data class Lexeme(
    val pinyin: String,
    val russian: String,
)

/**
 * Simplified-word lexicon. The first column is the word, the second is toned
 * pinyin, the third is a short Russian gloss (may be empty).
 */
class Lexicon(private val words: Map<String, Lexeme>) {
    val maxLength: Int = words.keys.maxOfOrNull { it.codePointCount(0, it.length) } ?: 1

    fun lookup(word: String): Lexeme? = words[word]

    companion object {
        fun parse(text: String): Lexicon {
            val map = HashMap<String, Lexeme>()
            for (line in text.lineSequence()) {
                if (line.isBlank()) continue
                val parts = line.split('\t', limit = 3)
                if (parts.size < 2) continue
                val word = parts[0]
                val pinyin = parts[1]
                if (word.isEmpty() || pinyin.isEmpty()) continue
                map[word] = Lexeme(pinyin, parts.getOrElse(2) { "" })
            }
            return Lexicon(map)
        }
    }
}
