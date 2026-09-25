package com.tepmex.byokassistedreader.domain

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

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

    /** pinyin4j writes third tone with a breve; Hanyu pinyin uses a caron. */
    fun toneMarks(reading: String): String = reading
        .replace('ă', 'ǎ')
        .replace('ĕ', 'ě')
        .replace('ĭ', 'ǐ')
        .replace('ŏ', 'ǒ')
        .replace('ŭ', 'ǔ')
}
