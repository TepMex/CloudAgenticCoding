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
    ): List<HanziCard> {
        val known = Hanzi.uniqueSet(knownText)
        return Hanzi.extractUniqueInOrder(ocrText)
            .filter { it !in known }
            .map { HanziCard(hanzi = it, pinyin = pinyinOf(it)) }
    }
}
