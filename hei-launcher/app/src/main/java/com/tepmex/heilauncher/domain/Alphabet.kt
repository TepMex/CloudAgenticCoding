package com.tepmex.heilauncher.domain

data class RailLetter(val key: String, val enabled: Boolean)

private val CYRILLIC = listOf(
    "А", "Б", "В", "Г", "Д", "Е", "Ё", "Ж", "З", "И", "Й", "К", "Л", "М", "Н", "О", "П",
    "Р", "С", "Т", "У", "Ф", "Х", "Ц", "Ч", "Ш", "Щ", "Ъ", "Ы", "Ь", "Э", "Ю", "Я",
)

fun sectionKey(label: String): String {
    val ch = label.trim().firstOrNull() ?: return "#"
    val upper = ch.uppercaseChar()
    if (upper in 'A'..'Z') return upper.toString()
    if (upper.toString() in CYRILLIC) return upper.toString()
    if (ch.isLetter()) return "•"
    return "#"
}

fun railLetters(apps: List<LaunchableApp>): List<RailLetter> {
    val present = apps.map { sectionKey(it.label) }.toSet()
    val latin = ('A'..'Z').map { letter ->
        val key = letter.toString()
        RailLetter(key, key in present)
    }
    val head = listOf(RailLetter("#", "#" in present)) + latin
    val other = if ("•" in present) listOf(RailLetter("•", true)) else emptyList()
    val cyrillic = CYRILLIC.filter { it in present }.map { RailLetter(it, true) }
    return head + other + cyrillic
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
