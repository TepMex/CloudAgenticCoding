package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSegmenterTest {
    private val lexicon = RuGlossLexicon(
        words = mapOf(
            "你好" to "привет",
            "朋友" to "друг",
            "研究生" to "аспирант",
            "生命" to "жизнь",
            "研究" to "исследовать",
            "你好世界人" to "не должно склеиться",
        ),
        chars = mapOf("你" to "ты"),
    )

    @Test
    fun groupsDictionaryWordsAndKeepsRussian() {
        val tokens = WordSegmenter.segment("你好朋友", lexicon) { "x" }
        assertEquals(listOf("你好", "朋友"), tokens.map { it.surface })
        assertTrue(tokens.all { it.word })
        assertEquals(listOf("x", "x"), tokens[0].readings)
        assertEquals("привет", tokens[0].russian)
        assertEquals("друг", tokens[1].russian)
        assertEquals("你好", tokens[0].glossQuery)
        assertEquals("你", tokens[0].characterQuery(0))
        assertEquals("好", tokens[0].characterQuery(1))
    }

    @Test
    fun unmatchedHanziStaySingleCharacters() {
        val tokens = WordSegmenter.segment("你X好", lexicon) { char -> "py:$char" }
        assertEquals(listOf("你", "X", "好"), tokens.map { it.surface })
        assertFalse(tokens[0].word)
        assertEquals("py:你", tokens[0].pinyin)
        assertEquals("ты", tokens[0].russian)
        assertNull(tokens[1].glossQuery)
        assertEquals("好", tokens[2].glossQuery)
        assertEquals("好", tokens[2].characterQuery(0))
        assertFalse(tokens[2].word)
    }

    @Test
    fun punctuationSeparatesWordsWithoutACellQuery() {
        val tokens = WordSegmenter.segment("你好，朋友", lexicon) { "x" }
        assertEquals(listOf("你好", "，", "朋友"), tokens.map { it.surface })
        assertNull(tokens[1].glossQuery)
        assertNull(tokens[1].characterQuery(0))
        assertEquals("你好", tokens[0].glossQuery)
    }

    @Test
    fun forwardMaximumMatchPrefersTheLongerWordUpToFour() {
        val tokens = WordSegmenter.segment("研究生命", lexicon) { "x" }
        assertEquals(listOf("研究生", "命"), tokens.map { it.surface })
        assertTrue(tokens[0].word)
        assertFalse(tokens[1].word)
        assertEquals("аспирант", tokens[0].russian)
    }

    @Test
    fun aWordLongerThanFourStaysSplit() {
        val tokens = WordSegmenter.segment("你好世界人", lexicon) { "x" }
        assertEquals(listOf("你好", "世", "界", "人"), tokens.map { it.surface })
        assertEquals("привет", tokens[0].russian)
        assertTrue(tokens.drop(1).all { !it.word })
    }

    @Test
    fun glossOpensTheWordAndACharacterOpensThatCharacter() {
        val word = WordSegmenter.segment("你好", lexicon) { "x" }.single()
        assertEquals(
            "plecoapi://x-callback-url/s?q=%E4%BD%A0%E5%A5%BD&x-source=instant-pinyin",
            PlecoLinks.searchUri(word.glossQuery!!),
        )
        assertEquals(
            "plecoapi://x-callback-url/s?q=%E4%BD%A0&x-source=instant-pinyin",
            PlecoLinks.searchUri(word.characterQuery(0)!!),
        )
    }
}
