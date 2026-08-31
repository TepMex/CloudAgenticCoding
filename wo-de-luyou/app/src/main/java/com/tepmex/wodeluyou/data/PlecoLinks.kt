package com.tepmex.wodeluyou.data

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PlecoLinks {
    const val SCHEME = "plecoapi"
    const val HOST = "x-callback-url"
    const val SEARCH_PATH = "/s"
    const val SOURCE = "wo-de-luyou"

    fun firstLookupToken(text: String): String {
        val first = text.split('/').first().trim()
        return first.ifEmpty { text.trim() }
    }

    fun searchUri(hanzi: String, source: String = SOURCE): String {
        val query = encode(firstLookupToken(hanzi))
        val src = encode(source)
        return "$SCHEME://$HOST$SEARCH_PATH?q=$query&x-source=$src"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
