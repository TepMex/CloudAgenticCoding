package com.tepmex.paizhaounknownhanzi.domain

data class HanziCard(
    val hanzi: String,
    val pinyin: String,
)

object UnknownHanzi {
    fun cards(
        ocrText: String,
        knownText: String,
        pinyinOf: (String) -> String,
        includeKnown: Boolean = false,
    ): List<HanziCard> {
        val unique = Hanzi.extractUniqueInOrder(ocrText)
        val selected = if (includeKnown) {
            unique
        } else {
            val known = Hanzi.uniqueSet(knownText)
            unique.filter { it !in known }
        }
        return selected.map { HanziCard(hanzi = it, pinyin = pinyinOf(it)) }
    }

    fun shareText(ocrText: String): String = Hanzi.extractUniqueInOrder(ocrText).joinToString("")
}
