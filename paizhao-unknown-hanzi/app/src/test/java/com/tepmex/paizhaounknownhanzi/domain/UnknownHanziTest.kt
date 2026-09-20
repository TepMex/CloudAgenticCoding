package com.tepmex.paizhaounknownhanzi.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnknownHanziTest {
    @Test
    fun dropsKnownCharactersAndKeepsOrder() {
        val cards = UnknownHanzi.cards(
            ocrText = "你好朋友世界",
            knownText = "你好世界 extra",
            pinyinOf = { "[$it]" },
        )
        assertEquals(listOf("朋", "友"), cards.map { it.hanzi })
        assertEquals(listOf("[朋]", "[友]"), cards.map { it.pinyin })
    }

    @Test
    fun keepsKnownCharactersWhenIncludeKnown() {
        val cards = UnknownHanzi.cards(
            ocrText = "你好朋友世界",
            knownText = "你好世界 extra",
            pinyinOf = { "[$it]" },
            includeKnown = true,
        )
        assertEquals(listOf("你", "好", "朋", "友", "世", "界"), cards.map { it.hanzi })
    }

    @Test
    fun emptyWhenEverythingIsKnown() {
        val cards = UnknownHanzi.cards("你好", "你好朋友", { it })
        assertEquals(emptyList<HanziCard>(), cards)
    }

    @Test
    fun allCardsWhenEverythingIsKnownAndIncludeKnown() {
        val cards = UnknownHanzi.cards("你好", "你好朋友", { it }, includeKnown = true)
        assertEquals(listOf("你", "好"), cards.map { it.hanzi })
    }

    @Test
    fun allUnknownWhenKnownTextHasNoHanzi() {
        val cards = UnknownHanzi.cards("汉字", "abc 123", { it })
        assertEquals(listOf("汉", "字"), cards.map { it.hanzi })
    }

    @Test
    fun shareTextIsUniqueRecognizedHanziInOrder() {
        assertEquals("你好朋友", UnknownHanzi.shareText("你好, 你好朋友! hello"))
        assertEquals("", UnknownHanzi.shareText("abc 123"))
    }
}
