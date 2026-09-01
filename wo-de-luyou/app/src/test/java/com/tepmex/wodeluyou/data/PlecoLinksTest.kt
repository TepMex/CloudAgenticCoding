package com.tepmex.wodeluyou.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlecoLinksTest {
    @Test
    fun searchUriUsesXCallbackHostAndEncodedHanzi() {
        val uri = PlecoLinks.searchUri("乌鲁木齐")
        assertEquals(
            "plecoapi://x-callback-url/s?q=%E4%B9%8C%E9%B2%81%E6%9C%A8%E9%BD%90&x-source=wo-de-luyou",
            uri,
        )
    }

    @Test
    fun firstVariantBeforeSlashIsUsedForLookup() {
        assertEquals("木赛来斯", PlecoLinks.firstLookupToken("木赛来斯 / 穆塞莱斯"))
        val uri = PlecoLinks.searchUri("木赛来斯 / 穆塞莱斯")
        assertEquals(true, uri.contains("q=%E6%9C%A8%E8%B5%9B%E6%9D%A5%E6%96%AF"))
        assertEquals(false, uri.contains("%E7%A9%86"))
    }

    @Test
    fun spacesBecomePercentTwenty() {
        val uri = PlecoLinks.searchUri("一张到……的票")
        assertEquals(true, uri.startsWith("plecoapi://x-callback-url/s?q="))
        assertEquals(false, uri.contains("+"))
    }
}
