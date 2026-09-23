package com.tepmex.instantpinyin.ocr

object CtcDecoder {
    /**
     * CTC layout from PP-OCRv5 LiteRT: index 0 is blank, then dictionary lines,
     * then a trailing space. Greedy decode: argmax, drop blanks, collapse repeats.
     */
    fun charsetFromDictLines(lines: List<String>): List<String> =
        buildList(lines.size + 2) {
            add("")
            addAll(lines)
            add(" ")
        }

    fun greedy(ids: IntArray, charset: List<String>): String {
        if (ids.isEmpty()) return ""
        val out = StringBuilder()
        var prev = -1
        for (id in ids) {
            if (id != 0 && id != prev && id in charset.indices) {
                out.append(charset[id])
            }
            prev = id
        }
        return out.toString()
    }

    fun greedyFromLogits(logits: FloatArray, time: Int, classes: Int, charset: List<String>): String {
        if (time <= 0 || classes <= 0) return ""
        val ids = IntArray(time)
        for (t in 0 until time) {
            var best = 0
            var bestScore = Float.NEGATIVE_INFINITY
            val row = t * classes
            for (c in 0 until classes) {
                val v = logits[row + c]
                if (v > bestScore) {
                    bestScore = v
                    best = c
                }
            }
            ids[t] = best
        }
        return greedy(ids, charset)
    }
}
