package com.tepmex.paizhaounknownhanzi.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinLookupTest {
    @Test
    fun hanHasToneMark() {
        val pinyin = PinyinLookup.of("汉")
        assertTrue("expected tone mark in $pinyin", pinyin.contains("àn") || pinyin.contains("hàn"))
        assertTrue(pinyin.any { it in "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜ" })
    }

    @Test
    fun unknownGlyphIsPlaceholder() {
        assertTrue(PinyinLookup.of("A").contains("—"))
    }
}
