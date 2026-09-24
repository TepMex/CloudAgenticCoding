package com.tepmex.instantpinyin.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlossSegmenterTest {
    private val lexicon = RuGlossLexicon(
        words = mapOf(
            "你好世界" to "привет, мир",
            "中国人" to "китаец",
            "中国" to "Китай",
            "我们" to "мы",
        ),
        chars = mapOf(
            "人" to "человек",
            "好" to "хороший",
            "你" to "ты",
        ),
    )

    @Test
    fun fourCharacterWordBeatsShorterOverlaps() {
        val pieces = GlossSegmenter.segment(listOf("你", "好", "世", "界"), lexicon)
        assertEquals(listOf(GlossPiece("你好世界", "привет, мир")), pieces)
    }

    @Test
    fun threeCharacterWordBeatsTheTwoCharacterPrefix() {
        val pieces = GlossSegmenter.segment(listOf("中", "国", "人"), lexicon)
        assertEquals(listOf(GlossPiece("中国人", "китаец")), pieces)
    }

    @Test
    fun twoCharacterWordThenASingleCharacterGloss() {
        val pieces = GlossSegmenter.segment(listOf("我", "们", "人"), lexicon)
        assertEquals(
            listOf(
                GlossPiece("我们", "мы"),
                GlossPiece("人", "человек"),
            ),
            pieces,
        )
    }

    @Test
    fun unknownCharacterHasNoGloss() {
        val pieces = GlossSegmenter.segment(listOf("好", "龙"), lexicon)
        assertEquals("好", pieces[0].text)
        assertEquals("хороший", pieces[0].gloss)
        assertEquals("龙", pieces[1].text)
        assertNull(pieces[1].gloss)
    }

    @Test
    fun bundledDictionaryKeepsGreedyOrder() {
        val file = File("src/main/assets/dict/words.txt")
        assertTrue(file.isFile)
        val words = RuGlossLexicon.parse(file.readText(), "")
        assertEquals("мы; наш", words.word("我们"))
        assertEquals("Китай; китайский", words.word("中国"))
        val chars = RuGlossLexicon.parse("", File("src/main/assets/dict/chars.txt").readText())
        assertEquals("один", chars.char("一"))
        val combined = RuGlossLexicon.parse(file.readText(), File("src/main/assets/dict/chars.txt").readText())
        val pieces = GlossSegmenter.segment(listOf("我", "们", "中", "国", "一"), combined)
        assertEquals(listOf("我们", "中国", "一"), pieces.map { it.text })
        assertTrue(pieces.all { !it.gloss.isNullOrBlank() })
    }
}
