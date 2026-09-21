package com.tepmex.duoshaoqian.data

private val DIGITS = charArrayOf('零', '一', '二', '三', '四', '五', '六', '七', '八', '九')

object ChineseMoney {
    fun toHanzi(value: Int): String {
        require(value in 1..9999) { "Chinese money amounts are 1..9999, got $value" }
        val units = arrayOf("", "十", "百", "千")
        val output = StringBuilder()
        var zeroNeeded = false
        for (place in 3 downTo 0) {
            val divisor = pow10(place)
            val digit = (value / divisor) % 10
            if (digit == 0) {
                if (output.isNotEmpty() && value % divisor != 0) zeroNeeded = true
                continue
            }
            if (zeroNeeded) {
                output.append(DIGITS[0])
                zeroNeeded = false
            }
            if (!(place == 1 && digit == 1 && output.isEmpty())) {
                output.append(DIGITS[digit])
            }
            output.append(units[place])
        }
        return output.toString()
    }

    fun toSpokenKuai(value: Int): String {
        if (value == 2) return "两块"
        return "${toHanzi(value)}块"
    }

    private fun pow10(place: Int): Int {
        var n = 1
        repeat(place) { n *= 10 }
        return n
    }
}
