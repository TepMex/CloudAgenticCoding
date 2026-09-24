package com.tepmex.instantpinyin

import android.app.Application
import com.tepmex.instantpinyin.domain.RuGlossLexicon
import com.tepmex.instantpinyin.ocr.LiteRtHanziOcr

class InstantPinyinApp : Application() {
    lateinit var ocr: LiteRtHanziOcr
        private set

    lateinit var lexicon: RuGlossLexicon
        private set

    override fun onCreate() {
        super.onCreate()
        ocr = LiteRtHanziOcr(this)
        lexicon = loadLexicon()
    }

    private fun loadLexicon(): RuGlossLexicon = runCatching {
        val words = assets.open("dict/words.txt").bufferedReader().use { it.readText() }
        val chars = assets.open("dict/chars.txt").bufferedReader().use { it.readText() }
        RuGlossLexicon.parse(words, chars)
    }.getOrDefault(RuGlossLexicon.EMPTY)
}
