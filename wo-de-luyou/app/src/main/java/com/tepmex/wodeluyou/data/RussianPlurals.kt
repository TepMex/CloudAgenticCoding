package com.tepmex.wodeluyou.data

object RussianPlurals {
    fun words(count: Int): String {
        val mod10 = count % 10
        val mod100 = count % 100
        val noun = when {
            mod10 == 1 && mod100 != 11 -> "слово"
            mod10 in 2..4 && mod100 !in 12..14 -> "слова"
            else -> "слов"
        }
        return "$count $noun"
    }
}
