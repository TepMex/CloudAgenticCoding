package com.tepmex.ankientertainer.data.hanzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HanziQueryTest {
    @Test
    fun extractsFirstOccurrenceOrderAndDedupes() {
        val result = HanziQuery.extractUniqueHan("好好说hello!")
        assertEquals(listOf("好", "说"), result.characters)
        assertFalse(result.truncated)
    }

    @Test
    fun ignoresPunctuationLatinDigitsKanaWhitespace() {
        val result = HanziQuery.extractUniqueHan("A1 あ。你好、カタカナ")
        assertEquals(listOf("你", "好"), result.characters)
    }

    @Test
    fun supportsSupplementaryPlaneHan() {
        // U+20000 is a CJK Unified Ideograph Extension B character (surrogate pair in UTF-16).
        val rare = String(Character.toChars(0x20000))
        val result = HanziQuery.extractUniqueHan("x${rare}y好")
        assertEquals(listOf(rare, "好"), result.characters)
    }

    @Test
    fun truncatesAfterMaxUnique() {
        val query = (0 until 25).joinToString("") { Character.toString(0x4E00 + it) }
        val result = HanziQuery.extractUniqueHan(query, maxUnique = 20)
        assertEquals(20, result.characters.size)
        assertTrue(result.truncated)
    }

    @Test
    fun mnemonicLookupKeysPreferCompoundsThenCharacters() {
        val result = HanziQuery.extractMnemonicLookupKeys("休息 OK 你好")
        assertEquals(
            listOf("休息", "你好", "休", "息", "你", "好"),
            result.characters,
        )
        assertFalse(result.truncated)
    }

    @Test
    fun mnemonicLookupKeysSingleCharacterIsOnlyOnce() {
        val result = HanziQuery.extractMnemonicLookupKeys("休")
        assertEquals(listOf("休"), result.characters)
    }

    @Test
    fun mnemonicLookupKeysWholeCompoundRun() {
        val result = HanziQuery.extractMnemonicLookupKeys("休息")
        assertEquals(listOf("休息", "休", "息"), result.characters)
    }
}
