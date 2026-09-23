package com.tepmex.hanziinfogf14.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkParserTest {
    @Test
    fun queryParameter() {
        assertEquals("请", DeepLinkParser.parse("hanziinfogf14://x-callback-url?q=请"))
    }

    @Test
    fun percentEncodedQuery() {
        assertEquals("请", DeepLinkParser.parse("hanziinfogf14://x-callback-url?q=%E8%AF%B7"))
    }

    @Test
    fun keepsXCallbackExtras() {
        val uri = "hanziinfogf14://x-callback-url/open?q=%E8%AF%B7&x-source=anki&x-success=app%3A%2F%2Fdone"
        assertEquals("请", DeepLinkParser.parse(uri))
    }

    @Test
    fun pathEqualsForm() {
        assertEquals("好", DeepLinkParser.parse("hanziinfogf14://x-callback-url/q=好"))
    }

    @Test
    fun pathSegmentForm() {
        assertEquals("明", DeepLinkParser.parse("hanziinfogf14://x-callback-url/q/明"))
    }

    @Test
    fun firstIdeographInQuery() {
        assertEquals("你", DeepLinkParser.parse("hanziinfogf14://x-callback-url?q=你好"))
    }

    @Test
    fun schemeAndHostAreCaseInsensitive() {
        assertEquals("请", DeepLinkParser.parse("HanziInfoGF14://X-Callback-Url?q=请"))
    }

    @Test
    fun rejectsWrongSchemeOrHost() {
        assertNull(DeepLinkParser.parse("https://x-callback-url?q=请"))
        assertNull(DeepLinkParser.parse("hanziinfogf14://example?q=请"))
        assertNull(DeepLinkParser.parse("hanziinfogf14://x-callback-url"))
        assertNull(DeepLinkParser.parse("hanziinfogf14://x-callback-url?q=nihao"))
        assertNull(DeepLinkParser.parse(null))
    }

    @Test
    fun formatRoundTrip() {
        val uri = DeepLinkParser.format("请")
        assertEquals("hanziinfogf14://x-callback-url?q=%E8%AF%B7", uri)
        assertEquals("请", DeepLinkParser.parse(uri))
    }
}
