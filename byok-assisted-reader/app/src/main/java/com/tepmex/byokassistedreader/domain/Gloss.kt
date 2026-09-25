package com.tepmex.byokassistedreader.domain

data class GlossEntry(val word: String, val explanation: String)

data class GlossPage(val words: List<GlossEntry>, val chengyu: List<GlossEntry>)

fun visibleGloss(pageText: String, knownWords: Set<String>, parsed: GlossPage): GlossPage {
    fun keep(entry: GlossEntry, allowKnown: Boolean): GlossEntry? {
        val word = entry.word.trim()
        val explanation = entry.explanation.trim()
        if (word.isEmpty() || explanation.isEmpty()) return null
        if (!allowKnown && word in knownWords) return null
        if (!pageText.contains(word)) return null
        return GlossEntry(word, explanation)
    }
    val chengyu = parsed.chengyu.mapNotNull { keep(it, allowKnown = true) }.distinctBy { it.word }
    val chengyuWords = chengyu.map { it.word }.toSet()
    val words = parsed.words
        .mapNotNull { keep(it, allowKnown = false) }
        .distinctBy { it.word }
        .filter { it.word !in chengyuWords }
    return GlossPage(words, chengyu)
}
