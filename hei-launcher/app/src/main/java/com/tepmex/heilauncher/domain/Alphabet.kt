package com.tepmex.heilauncher.domain

data class RailLetter(val key: String, val enabled: Boolean)

/** One control for every Cyrillic label. Jumps to the first such name. */
const val RAIL_CYRILLIC = "АБВ"

/** One control for every CJK label. Jumps to the first such name. */
const val RAIL_CJK = "中文"

fun sectionKey(label: String): String {
    val ch = label.trim().firstOrNull() ?: return "#"
    val upper = ch.uppercaseChar()
    if (upper in 'A'..'Z') return upper.toString()
    if (isCyrillic(upper)) return RAIL_CYRILLIC
    if (isCjk(ch)) return RAIL_CJK
    if (ch.isLetter()) return "•"
    return "#"
}

/**
 * Unfiltered list order: digits and other non-letters, then Latin, Cyrillic,
 * CJK, then any remaining script. Jump targets stay contiguous.
 */
fun scriptBucket(label: String): Int = when (val key = sectionKey(label)) {
    "#" -> 0
    RAIL_CYRILLIC -> 2
    RAIL_CJK -> 3
    "•" -> 4
    else -> if (key.length == 1 && key[0] in 'A'..'Z') 1 else 4
}

fun railLetters(apps: List<LaunchableApp>): List<RailLetter> {
    val present = apps.map { sectionKey(it.label) }.toSet()
    val letters = ArrayList<RailLetter>(29)
    letters.add(RailLetter("#", "#" in present))
    for (letter in 'A'..'Z') {
        val key = letter.toString()
        letters.add(RailLetter(key, key in present))
    }
    letters.add(RailLetter(RAIL_CYRILLIC, RAIL_CYRILLIC in present))
    letters.add(RailLetter(RAIL_CJK, RAIL_CJK in present))
    return letters
}

fun jumpIndex(apps: List<LaunchableApp>, letter: String): Int {
    if (apps.isEmpty()) return 0
    val exact = apps.indexOfFirst { sectionKey(it.label) == letter }
    if (exact >= 0) return exact
    val order = railLetters(apps).map { it.key }
    val start = order.indexOf(letter)
    if (start < 0) return 0
    for (i in start + 1 until order.size) {
        val idx = apps.indexOfFirst { sectionKey(it.label) == order[i] }
        if (idx >= 0) return idx
    }
    return apps.lastIndex
}

fun railIndexAt(y: Float, height: Float, count: Int): Int {
    if (count <= 0 || height <= 0f) return 0
    val fraction = (y / height).coerceIn(0f, 0.9999f)
    return (fraction * count).toInt().coerceIn(0, count - 1)
}

private fun isCyrillic(ch: Char): Boolean {
    val block = Character.UnicodeBlock.of(ch)
    return block == Character.UnicodeBlock.CYRILLIC ||
        block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
}

private fun isCjk(ch: Char): Boolean {
    val block = Character.UnicodeBlock.of(ch)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
}
