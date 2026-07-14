package com.tepmex.ankientertainer.data

import android.net.Uri

object DeepLinkParser {
    /**
     * Parses vocabulary from ankientapi://x-callback-url?q={VOCAB}
     * or ankientapi://x-callback-url/q={VOCAB}
     */
    fun parseVocab(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme?.equals("ankientapi", ignoreCase = true) != true) return null
        if (uri.host?.equals("x-callback-url", ignoreCase = true) != true) return null

        uri.getQueryParameter("q")?.takeIf { it.isNotBlank() }?.let { return it }

        val segments = uri.pathSegments
        if (segments.size >= 2 && segments[0].equals("q", ignoreCase = true)) {
            return segments[1].takeIf { it.isNotBlank() }
        }
        if (segments.size == 1) {
            val segment = segments[0]
            if (segment.startsWith("q=", ignoreCase = true)) {
                return segment.substring(2).takeIf { it.isNotBlank() }
            }
        }

        return null
    }
}
