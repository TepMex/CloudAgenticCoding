package com.tepmex.ankientertainer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.net.Uri

@RunWith(RobolectricTestRunner::class)
class DeepLinkParserTest {

    @Test
    fun parseQueryParameter() {
        val uri = Uri.parse("ankientapi://x-callback-url?q=你好")
        assertEquals("你好", DeepLinkParser.parseVocab(uri))
    }

    @Test
    fun parsePathEqualsForm() {
        val uri = Uri.parse("ankientapi://x-callback-url/q=hello")
        assertEquals("hello", DeepLinkParser.parseVocab(uri))
    }

    @Test
    fun parsePathSegmentForm() {
        val uri = Uri.parse("ankientapi://x-callback-url/q/hello")
        assertEquals("hello", DeepLinkParser.parseVocab(uri))
    }

    @Test
    fun rejectsWrongScheme() {
        val uri = Uri.parse("https://example.com?q=hello")
        assertNull(DeepLinkParser.parseVocab(uri))
    }
}
