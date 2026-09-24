package com.tepmex.instantpinyin.domain

/**
 * Bundled Russian glosses. Words are 2–4 hanzi; characters are single-hanzi fallbacks.
 * Each line is `hanzi<TAB>gloss`.
 */
class RuGlossLexicon(
    private val words: Map<String, String>,
    private val chars: Map<String, String>,
) {
    fun word(text: String): String? = words[text]

    fun char(text: String): String? = chars[text]

    companion object {
        val EMPTY = RuGlossLexicon(emptyMap(), emptyMap())

        fun parse(wordsText: String, charsText: String): RuGlossLexicon =
            RuGlossLexicon(parseTable(wordsText), parseTable(charsText))

        private fun parseTable(text: String): Map<String, String> {
            val out = HashMap<String, String>()
            for (line in text.lineSequence()) {
                if (line.isEmpty()) continue
                val tab = line.indexOf('\t')
                if (tab <= 0 || tab == line.lastIndex) continue
                val key = line.substring(0, tab)
                val gloss = line.substring(tab + 1).trim()
                if (gloss.isNotEmpty()) out[key] = gloss
            }
            return out
        }
    }
}
