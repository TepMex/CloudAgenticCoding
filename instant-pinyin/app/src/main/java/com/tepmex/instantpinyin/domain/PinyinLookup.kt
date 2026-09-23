package com.tepmex.instantpinyin.domain

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

object PinyinLookup {
    private val format: HanyuPinyinOutputFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    fun of(hanzi: String): String {
        if (hanzi.isEmpty()) return "—"
        val parts = ArrayList<String>(hanzi.length)
        var i = 0
        while (i < hanzi.length) {
            val cp = hanzi.codePointAt(i)
            val ch = cp.toChar()
            val readings = try {
                PinyinHelper.toHanyuPinyinStringArray(ch, format)
            } catch (_: Exception) {
                null
            }
            if (readings.isNullOrEmpty()) {
                parts.add("—")
            } else {
                parts.add(readings.map(::toneMarks).distinct().joinToString("/"))
            }
            i += Character.charCount(cp)
        }
        return parts.joinToString(" ")
    }

    /**
     * pinyin4j writes third-tone vowels with a breve (ă ĕ ĭ ŏ ŭ).
     * Hanyu pinyin uses a caron (ǎ ě ǐ ǒ ǔ).
     */
    private fun toneMarks(reading: String): String = reading
        .replace('ă', 'ǎ')
        .replace('ĕ', 'ě')
        .replace('ĭ', 'ǐ')
        .replace('ŏ', 'ǒ')
        .replace('ŭ', 'ǔ')
}
