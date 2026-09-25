package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownReadingTest {
    private val known = KnownReading.knownSet("你好世界 extra")

    @Test
    fun knownTextKeepsOnlyHanzi() {
        assertEquals(setOf("你", "好", "世", "界"), known)
    }

    @Test
    fun knownCharacterLosesPinyinAndStaysTappable() {
        val label = PinyinLabel("你", "nǐ", PxBox(0f, 40f, 20f, 80f), PxBox(0f, 0f, 20f, 12f), 12f)
        val muted = KnownReading.mutePinyin(label, known)
        assertEquals("", muted.pinyin)
        assertTrue(muted.hit(muted.glyph.centerX, muted.glyph.centerY))
        assertFalse(muted.hit(muted.pill.centerX, muted.pill.centerY))
    }

    @Test
    fun unknownCharacterKeepsPinyin() {
        val label = PinyinLabel("朋", "péng", PxBox(0f, 0f, 10f, 10f), PxBox(0f, 0f, 10f, 10f), 12f)
        assertEquals("péng", KnownReading.mutePinyin(label, known).pinyin)
    }

    @Test
    fun fullyKnownWordDropsTheGloss() {
        assertFalse(KnownReading.keepGloss("你好", known))
        assertTrue(KnownReading.keepGloss("朋友", known))
        assertTrue(KnownReading.keepGloss("你朋", known))
    }

    @Test
    fun onlyKnownDropsUnknownGlyphs() {
        val glyphs = listOf(
            ReadingGlyph("你", "nǐ", PxBox(0f, 0f, 10f, 10f)),
            ReadingGlyph("朋", "péng", PxBox(10f, 0f, 20f, 10f)),
        )
        assertEquals(listOf("你"), KnownReading.visibleGlyphs(glyphs, known, onlyKnown = true).map { it.hanzi })
        assertEquals(listOf("你", "朋"), KnownReading.visibleGlyphs(glyphs, known, onlyKnown = false).map { it.hanzi })
    }

    @Test
    fun stillHidesPinyinPerCharacterAndGlossOnlyWhenEveryHanziIsKnown() {
        val lines = listOf(
            listOf(
                TextToken("你好", listOf("nǐ", "hǎo"), "привет", word = true),
                TextToken("，", emptyList(), "", word = false),
                TextToken("朋友", listOf("péng", "you"), "друг", word = true),
                TextToken("你", listOf("nǐ"), "ты", word = false),
            ),
        )
        val shown = KnownReading.present(lines, known, onlyKnown = false).single()
        assertEquals(listOf("你好", "，", "朋友", "你"), shown.map { it.surface })
        assertEquals(listOf("", ""), shown[0].chars.map { it.pinyin })
        assertNull(shown[0].russian)
        assertNull(shown[0].glossQuery)
        assertEquals(listOf("péng", "you"), shown[2].chars.map { it.pinyin })
        assertEquals("друг", shown[2].russian)
        assertEquals("", shown[3].chars.single().pinyin)
        assertNull(shown[3].russian)
    }

    @Test
    fun mixedWordHidesOnlyTheKnownSyllable() {
        val token = TextToken("你朋", listOf("nǐ", "péng"), "друг", word = true)
        val shown = KnownReading.present(listOf(listOf(token)), setOf("你"), onlyKnown = false).single().single()
        assertEquals(listOf("", "péng"), shown.chars.map { it.pinyin })
        assertEquals("друг", shown.russian)
    }

    @Test
    fun onlyKnownDropsUnknownCharactersAndKeepsPunctuation() {
        val lines = listOf(
            listOf(
                TextToken("你好", listOf("nǐ", "hǎo"), "привет", word = true),
                TextToken("，", emptyList(), "", word = false),
                TextToken("朋友", listOf("péng", "you"), "друг", word = true),
            ),
        )
        val shown = KnownReading.present(lines, known, onlyKnown = true).single()
        assertEquals(listOf("你好", "，"), shown.map { it.surface })
        assertNull(shown[0].russian)
    }
}
