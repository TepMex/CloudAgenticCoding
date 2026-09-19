package com.tepmex.paizhaounknownhanzi.domain

object Hanzi {
    fun isHanzi(codePoint: Int): Boolean =
        codePoint in 0x4E00..0x9FFF ||
            codePoint in 0x3400..0x4DBF ||
            codePoint in 0xF900..0xFAFF

    fun extractUniqueInOrder(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val seen = LinkedHashSet<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isHanzi(cp)) {
                seen.add(String(Character.toChars(cp)))
            }
            i += Character.charCount(cp)
        }
        return seen.toList()
    }

    fun uniqueSet(text: String): Set<String> = extractUniqueInOrder(text).toSet()
}
