package com.tepmex.wodeluyou.data

import java.text.Normalizer

object TextFold {
    fun fold(value: String): String {
        val nfd = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        return buildString(nfd.length) {
            nfd.forEach { ch ->
                if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) {
                    append(
                        when (ch) {
                            'ü', 'ű', 'ū', 'ú', 'ǔ', 'ù' -> 'u'
                            'v' -> 'u'
                            else -> ch
                        },
                    )
                }
            }
        }
    }
}
