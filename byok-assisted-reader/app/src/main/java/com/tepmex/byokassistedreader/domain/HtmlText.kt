package com.tepmex.byokassistedreader.domain

private val hiddenBlock = Regex("(?is)<(script|style|rt|rp)\\b[^>]*>.*?</\\1>")
private val comment = Regex("(?s)<!--.*?-->")
private val breakTag = Regex("(?i)<br\\s*/?>")
private val blockEnd = Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote|section|article)>")
private val tag = Regex("<[^>]+>")

fun htmlToParagraphs(html: String): List<String> {
    var s = comment.replace(html, "")
    s = hiddenBlock.replace(s, "")
    s = breakTag.replace(s, "\n")
    s = blockEnd.replace(s, "\n")
    s = tag.replace(s, "")
    s = decodeEntities(s)
    return s.split('\n')
        .map { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
        .filter { it.isNotEmpty() }
}

fun decodeEntities(raw: String): String {
    val named = raw
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
    val decimal = Regex("&#(\\d+);").replace(named) { match ->
        codePointChar(match.groupValues[1].toIntOrNull())
    }
    return Regex("&#x([0-9a-fA-F]+);").replace(decimal) { match ->
        codePointChar(match.groupValues[1].toIntOrNull(16))
    }
}

private fun codePointChar(cp: Int?): String {
    if (cp == null || cp < 0 || cp > 0x10FFFF) return ""
    return String(Character.toChars(cp))
}
