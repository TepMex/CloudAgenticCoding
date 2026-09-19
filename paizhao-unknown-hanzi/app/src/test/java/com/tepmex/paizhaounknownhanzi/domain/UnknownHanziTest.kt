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
    fun emptyWhenEverythingIsKnown() {
        val cards = UnknownHanzi.cards("你好", "你好朋友", { it })
        assertEquals(emptyList<HanziCard>(), cards)
    }

    @Test
    fun allUnknownWhenKnownTextHasNoHanzi() {
        val cards = UnknownHanzi.cards("汉字", "abc 123", { it })
        assertEquals(listOf("汉", "字"), cards.map { it.hanzi })
    }
}
