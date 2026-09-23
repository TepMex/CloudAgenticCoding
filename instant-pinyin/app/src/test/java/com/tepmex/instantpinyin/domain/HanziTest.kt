package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HanziTest {
    @Test
    fun extractsUniqueHanziInFirstSeenOrder() {
        assertEquals(
            listOf("你", "好", "世", "界"),
            Hanzi.extractUniqueInOrder("你好, 你好世界! hello 123"),
        )
    }

    @Test
    fun ignoresPunctuationAndLatin() {
        assertEquals(emptyList<String>(), Hanzi.extractUniqueInOrder("Hello, world! 42."))
    }

    @Test
    fun treatsRepeatedCharacterAsOne() {
        assertEquals(listOf("人"), Hanzi.extractUniqueInOrder("人人人人"))
    }

    @Test
    fun acceptsExtensionA() {
        assertTrue(Hanzi.isHanzi(0x3400))
        assertFalse(Hanzi.isHanzi('A'.code))
        assertFalse(Hanzi.isHanzi('。'.code))
    }
}
