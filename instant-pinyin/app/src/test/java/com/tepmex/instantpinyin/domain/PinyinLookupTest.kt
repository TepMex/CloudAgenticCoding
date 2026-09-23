package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinLookupTest {
    @Test
    fun niUsesCaronNotBreve() {
        assertTrue(PinyinLookup.of("你").contains("nǐ"))
    }

    @Test
    fun haoJoinsReadingsWithToneMarks() {
        val pinyin = PinyinLookup.of("好")
        assertTrue(pinyin.contains("hǎo"))
        assertTrue(pinyin.contains("hào"))
    }

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
