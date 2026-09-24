package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSegmenterTest {
    private val lexicon = Lexicon.parse(
        """
        你好	nǐ hǎo	привет
        朋友	péng you	друг
        研究生	yán jiū shēng	аспирант
        生命	shēng mìng	жизнь
        研究	yán jiū	исследовать
        你	nǐ	ты
        """.trimIndent(),
    )

    @Test
    fun groupsDictionaryWordsAndKeepsRussian() {
        val tokens = WordSegmenter.segment("你好朋友", lexicon) { "x" }
        assertEquals(listOf("你好", "朋友"), tokens.map { it.surface })
        assertTrue(tokens.all { it.word })
        assertEquals("nǐ hǎo", tokens[0].pinyin)
        assertEquals("привет", tokens[0].russian)
        assertEquals("друг", tokens[1].russian)
        assertEquals(listOf("你好", "朋友"), tokens.map { it.plecoQuery })
    }

    @Test
    fun unmatchedHanziStaySingleCharacters() {
        val tokens = WordSegmenter.segment("你X好", lexicon) { char -> "py:$char" }
        assertEquals(listOf("你", "X", "好"), tokens.map { it.surface })
        assertFalse(tokens[0].word)
        assertEquals("py:你", tokens[0].pinyin)
        assertEquals("ты", tokens[0].russian)
        assertNull(tokens[1].plecoQuery)
        assertEquals("好", tokens[2].plecoQuery)
        assertFalse(tokens[2].word)
    }

    @Test
    fun punctuationSeparatesWordsWithoutACellQuery() {
        val tokens = WordSegmenter.segment("你好，朋友", lexicon) { "x" }
        assertEquals(listOf("你好", "，", "朋友"), tokens.map { it.surface })
        assertNull(tokens[1].plecoQuery)
        assertEquals("你好", tokens[0].plecoQuery)
    }

    @Test
    fun forwardMaximumMatchPrefersTheLongerWord() {
        val tokens = WordSegmenter.segment("研究生命", lexicon) { "x" }
        assertEquals(listOf("研究生", "命"), tokens.map { it.surface })
        assertTrue(tokens[0].word)
        assertFalse(tokens[1].word)
        assertEquals("аспирант", tokens[0].russian)
    }

    @Test
    fun wordQueryOpensPlecoOnTheWholeWord() {
        val word = WordSegmenter.segment("你好", lexicon) { "x" }.single()
        assertEquals(
            "plecoapi://x-callback-url/s?q=%E4%BD%A0%E5%A5%BD&x-source=instant-pinyin",
            PlecoLinks.searchUri(word.plecoQuery!!),
        )
    }
}
