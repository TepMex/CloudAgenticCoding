package com.tepmex.hanziinfogf14.data

object HanziText {
    fun ideographs(text: String): List<String> {
        val out = ArrayList<String>()
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (isIdeograph(codePoint)) {
                out.add(String(Character.toChars(codePoint)))
            }
            index += Character.charCount(codePoint)
        }
        return out
    }

    fun firstIdeograph(text: String): String? = ideographs(text).firstOrNull()

    fun isIdeograph(codePoint: Int): Boolean = when (codePoint) {
        in 0x3400..0x4DBF -> true
        in 0x4E00..0x9FFF -> true
        in 0xF900..0xFAFF -> true
        in 0x20000..0x2A6DF -> true
        in 0x2A700..0x2B73F -> true
        in 0x2B740..0x2B81F -> true
        in 0x2B820..0x2CEAF -> true
        in 0x2CEB0..0x2EBEF -> true
        in 0x30000..0x323AF -> true
        else -> false
    }
}
