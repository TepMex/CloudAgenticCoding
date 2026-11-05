package com.tepmex.extensivereadingtutor.ui

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

object Hanzi {
    fun isHanzi(cp: Int): Boolean =
        cp in 0x3400..0x4DBF ||
            cp in 0x4E00..0x9FFF ||
            cp in 0xF900..0xFAFF ||
            cp in 0x20000..0x2A6DF ||
            cp in 0x2A700..0x2B73F ||
            cp in 0x2B740..0x2B81F ||
            cp in 0x2B820..0x2CEAF ||
            cp in 0x2CEB0..0x2EBEF ||
            cp in 0x30000..0x3134F
}

object PinyinSyllable {
    private val format = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    fun of(glyph: String): String {
        if (glyph.isEmpty()) return ""
        val cp = glyph.codePointAt(0)
        if (!Hanzi.isHanzi(cp) || !Character.isBmpCodePoint(cp)) return ""
        val readings = try {
            PinyinHelper.toHanyuPinyinStringArray(cp.toChar(), format)
        } catch (_: Exception) {
            null
        }
        val first = readings?.firstOrNull() ?: return ""
        return toneMarks(first)
    }

    fun hanRunAt(text: String, utf16: Int): String? {
        if (text.isEmpty()) return null
        val index = utf16.coerceIn(0, text.length - 1)
        val snapped = if (Character.isLowSurrogate(text[index]) && index > 0) index - 1 else index
        if (!Hanzi.isHanzi(text.codePointAt(snapped))) return null
        var start = snapped
        while (start > 0) {
            val previous = start - 1
            val at = if (Character.isLowSurrogate(text[previous]) && previous > 0) previous - 1 else previous
            if (!Hanzi.isHanzi(text.codePointAt(at))) break
            start = at
        }
        var end = start
        while (end < text.length && Hanzi.isHanzi(text.codePointAt(end))) {
            end += Character.charCount(text.codePointAt(end))
        }
        return text.substring(start, end).takeIf { it.isNotEmpty() }
    }

    /** pinyin4j writes third tone with a breve; Hanyu pinyin uses a caron. */
    fun toneMarks(reading: String): String = reading
        .replace('ă', 'ǎ')
        .replace('ĕ', 'ě')
        .replace('ĭ', 'ǐ')
        .replace('ŏ', 'ǒ')
        .replace('ŭ', 'ǔ')
}
