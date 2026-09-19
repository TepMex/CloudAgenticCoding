package com.tepmex.paizhaounknownhanzi.domain

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object PlecoLinks {
    const val SCHEME = "plecoapi"
    const val HOST = "x-callback-url"
    const val SEARCH_PATH = "/s"
    const val SOURCE = "paizhao-unknown-hanzi"

    fun searchUri(hanzi: String, source: String = SOURCE): String {
        val query = encode(hanzi.trim())
        val src = encode(source)
        return "$SCHEME://$HOST$SEARCH_PATH?q=$query&x-source=$src"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
}
