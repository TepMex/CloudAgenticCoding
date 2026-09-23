package com.tepmex.hanziinfogf14.data

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Incoming x-callback-url:
 * hanziinfogf14://x-callback-url?q={hanzi}
 * hanziinfogf14://x-callback-url/open?q={hanzi}
 * hanziinfogf14://x-callback-url/q={hanzi}
 * hanziinfogf14://x-callback-url/q/{hanzi}
 *
 * x-source, x-success, x-error, and x-cancel are accepted and ignored.
 * Opening the character is the whole action, so the app does not invoke x-success.
 */
object DeepLinkParser {
    const val SCHEME = "hanziinfogf14"
    const val HOST = "x-callback-url"

    fun parse(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val uri = raw.trim()
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = uri.substring(0, schemeEnd)
        if (!scheme.equals(SCHEME, ignoreCase = true)) return null
        val rest = uri.substring(schemeEnd + 3)
        val hostEnd = rest.indexOfAny(charArrayOf('/', '?'))
        val host = if (hostEnd < 0) rest else rest.substring(0, hostEnd)
        if (!host.equals(HOST, ignoreCase = true)) return null
        val afterHost = if (hostEnd < 0) "" else rest.substring(hostEnd)

        queryParam(afterHost, "q")?.let { value ->
            HanziText.firstIdeograph(decode(value))?.let { return it }
        }

        val path = afterHost.substringBefore('?').substringBefore('#')
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.size >= 2 && segments[0].equals("q", ignoreCase = true)) {
            return HanziText.firstIdeograph(decode(segments[1]))
        }
        if (segments.size == 1 && segments[0].startsWith("q=", ignoreCase = true)) {
            return HanziText.firstIdeograph(decode(segments[0].substring(2)))
        }
        return null
    }

    fun format(hanzi: String): String {
        val encoded = URLEncoder.encode(hanzi, StandardCharsets.UTF_8)
        return "$SCHEME://$HOST?q=$encoded"
    }

    private fun queryParam(afterHost: String, name: String): String? {
        val queryStart = afterHost.indexOf('?')
        if (queryStart < 0) return null
        val query = afterHost.substring(queryStart + 1).substringBefore('#')
        for (part in query.split('&')) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            val key = if (eq < 0) part else part.substring(0, eq)
            if (key.equals(name, ignoreCase = true)) {
                return if (eq < 0) "" else part.substring(eq + 1)
            }
        }
        return null
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
