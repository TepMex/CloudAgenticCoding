package com.tepmex.ankientertainer.data.hanzi

/**
 * Unicode-aware Han character extraction for metadata placeholders.
 * Iterates by code point (not UTF-16 Char) and keeps first-occurrence order.
 */
object HanziQuery {
    const val DEFAULT_MAX_UNIQUE = 20

    /** Max code points allowed for a compound mnemonic key (matches seed import). */
    const val MAX_MNEMONIC_KEY_CODE_POINTS = 20

    data class Extraction(
        val characters: List<String>,
        val truncated: Boolean,
    )

    fun extractUniqueHan(
        query: String,
        maxUnique: Int = DEFAULT_MAX_UNIQUE,
    ): Extraction {
        val ordered = LinkedHashSet<String>()
        var truncated = false
        var i = 0
        while (i < query.length) {
            val cp = query.codePointAt(i)
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                val ch = String(Character.toChars(cp))
                if (ch !in ordered) {
                    if (ordered.size >= maxUnique) {
                        truncated = true
                    } else {
                        ordered.add(ch)
                    }
                }
            }
            i += Character.charCount(cp)
        }
        return Extraction(characters = ordered.toList(), truncated = truncated)
    }

    /**
     * Keys for mnemonic lookup: contiguous Han runs (compounds / phrases) first,
     * then each unique individual Han character, first-occurrence order.
     *
     * Example: `"休息 OK 你好"` → `["休息", "你好", "休", "息", "你", "好"]`
     * (capped at [maxKeys]).
     */
    fun extractMnemonicLookupKeys(
        query: String,
        maxKeys: Int = DEFAULT_MAX_UNIQUE,
    ): Extraction {
        val ordered = LinkedHashSet<String>()
        var truncated = false

        fun tryAdd(key: String) {
            if (key.isEmpty() || key in ordered) return
            val codePoints = key.codePointCount(0, key.length)
            if (codePoints > MAX_MNEMONIC_KEY_CODE_POINTS) return
            if (ordered.size >= maxKeys) {
                truncated = true
            } else {
                ordered.add(key)
            }
        }

        // Pass 1: contiguous Han runs (includes single-char runs and compounds).
        val run = StringBuilder()
        var i = 0
        while (i < query.length) {
            val cp = query.codePointAt(i)
            if (Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN) {
                run.appendCodePoint(cp)
            } else if (run.isNotEmpty()) {
                tryAdd(run.toString())
                run.setLength(0)
            }
            i += Character.charCount(cp)
        }
        if (run.isNotEmpty()) {
            tryAdd(run.toString())
        }

        // Pass 2: unique individual characters (for per-character stories).
        val chars = extractUniqueHan(query, maxUnique = maxKeys)
        for (ch in chars.characters) {
            tryAdd(ch)
        }
        if (chars.truncated) truncated = true

        return Extraction(characters = ordered.toList(), truncated = truncated)
    }
}
