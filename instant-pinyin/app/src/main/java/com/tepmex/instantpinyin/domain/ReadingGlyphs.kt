package com.tepmex.instantpinyin.domain

import com.tepmex.instantpinyin.ocr.OcrLine

data class ReadingGlyph(
    val hanzi: String,
    val pinyin: String,
    val box: PxBox,
)

object ReadingGlyphs {
    fun fromLines(
        lines: List<OcrLine>,
        pinyinOf: (String) -> String = PinyinLookup::of,
    ): List<ReadingGlyph> {
        val glyphs = ArrayList<ReadingGlyph>()
        for (line in lines) {
            glyphs += slice(line, pinyinOf)
        }
        return glyphs
    }

    fun slice(line: OcrLine, pinyinOf: (String) -> String = PinyinLookup::of): List<ReadingGlyph> {
        val slots = ArrayList<Int>()
        var i = 0
        while (i < line.text.length) {
            val cp = line.text.codePointAt(i)
            if (!Character.isWhitespace(cp)) slots.add(cp)
            i += Character.charCount(cp)
        }
        if (slots.isEmpty() || line.box.width <= 0 || line.box.height <= 0) return emptyList()
        val vertical = line.box.height > line.box.width * 1.2f
        val out = ArrayList<ReadingGlyph>()
        val n = slots.size.toFloat()
        for (index in slots.indices) {
            val cp = slots[index]
            if (!Hanzi.isHanzi(cp)) continue
            val hanzi = String(Character.toChars(cp))
            val pinyin = pinyinOf(hanzi)
            if (pinyin.isBlank() || pinyin == "—") continue
            val t0 = index / n
            val t1 = (index + 1) / n
            val box = if (vertical) {
                PxBox(
                    left = line.box.x.toFloat(),
                    top = line.box.y + line.box.height * t0,
                    right = (line.box.x + line.box.width).toFloat(),
                    bottom = line.box.y + line.box.height * t1,
                )
            } else {
                PxBox(
                    left = line.box.x + line.box.width * t0,
                    top = line.box.y.toFloat(),
                    right = line.box.x + line.box.width * t1,
                    bottom = (line.box.y + line.box.height).toFloat(),
                )
            }
            out.add(ReadingGlyph(hanzi, pinyin, box))
        }
        return out
    }
}
