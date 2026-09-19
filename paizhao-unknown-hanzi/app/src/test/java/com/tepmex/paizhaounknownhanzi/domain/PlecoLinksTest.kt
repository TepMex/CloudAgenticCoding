package com.tepmex.paizhaounknownhanzi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlecoLinksTest {
    @Test
    fun searchUriUsesXCallbackHostAndEncodedHanzi() {
        assertEquals(
            "plecoapi://x-callback-url/s?q=%E6%B1%89&x-source=paizhao-unknown-hanzi",
            PlecoLinks.searchUri("汉"),
        )
    }

    @Test
    fun spacesBecomePercentTwenty() {
        val uri = PlecoLinks.searchUri("一张票")
        assertTrue(uri.startsWith("plecoapi://x-callback-url/s?q="))
        assertFalse(uri.contains("+"))
    }
}
