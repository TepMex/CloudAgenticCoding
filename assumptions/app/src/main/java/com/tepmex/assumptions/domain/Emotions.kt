package com.tepmex.assumptions.domain

/**
 * Eight Plutchik families, clockwise from the top (joy), plus the primary dyad
 * that sits clockwise of each family. Mild is depth 0, intense is depth 2.
 */
data class Emotion(
    val id: String,
    val name: String,
    val colorArgb: Long,
)

data class EmotionFamily(
    val index: Int,
    val key: String,
    val bands: List<Emotion>,
) {
    init {
        require(bands.size == EmotionFamily.DEPTHS) { key }
    }

    val mild: Emotion get() = bands[0]
    val medium: Emotion get() = bands[1]
    val intense: Emotion get() = bands[2]

    companion object {
        const val DEPTHS = 3
    }
}

data class EmotionDyad(
    val emotion: Emotion,
    val leftIndex: Int,
    val rightIndex: Int,
)

object Emotions {
    val families: List<EmotionFamily> = listOf(
        family(
            index = 0,
            key = "joy",
            "Спокойствие" to 0xFFF7E29A,
            "Радость" to 0xFFF5C400,
            "Экстаз" to 0xFFFFDE45,
        ),
        family(
            index = 1,
            key = "trust",
            "Признание" to 0xFFD3E48C,
            "Доверие" to 0xFF8FCB45,
            "Восхищение" to 0xFFC5D24A,
        ),
        family(
            index = 2,
            key = "fear",
            "Опасение" to 0xFF9AD4B8,
            "Страх" to 0xFF2E9A52,
            "Ужас" to 0xFF146B38,
        ),
        family(
            index = 3,
            key = "surprise",
            "Отвлечение" to 0xFFA6E3D4,
            "Удивление" to 0xFF3CB89A,
            "Изумление" to 0xFF178F78,
        ),
        family(
            index = 4,
            key = "sadness",
            "Задумчивость" to 0xFFC9D8F6,
            "Печаль" to 0xFF5B82E6,
            "Горе" to 0xFF2A4C9E,
        ),
        family(
            index = 5,
            key = "disgust",
            "Скука" to 0xFFCBC6F3,
            "Брезгливость" to 0xFF7A74D8,
            "Отвращение" to 0xFF4A42A8,
        ),
        family(
            index = 6,
            key = "anger",
            "Досада" to 0xFFF7B7BB,
            "Гнев" to 0xFFE25562,
            "Ярость" to 0xFFC62828,
        ),
        family(
            index = 7,
            key = "anticipation",
            "Интерес" to 0xFFF8D19A,
            "Ожидание" to 0xFFF0922A,
            "Бдительность" to 0xFFE25A14,
        ),
    )

    val dyads: List<EmotionDyad> = listOf(
        dyad("love", "Любовь", 0, 1),
        dyad("submission", "Подчинение", 1, 2),
        dyad("awe", "Трепет", 2, 3),
        dyad("disapproval", "Неодобрение", 3, 4),
        dyad("remorse", "Сожаление", 4, 5),
        dyad("contempt", "Презрение", 5, 6),
        dyad("aggression", "Агрессия", 6, 7),
        dyad("optimism", "Оптимизм", 7, 0),
    )

    private val byId: Map<String, Emotion> = buildMap {
        families.forEach { family -> family.bands.forEach { put(it.id, it) } }
        dyads.forEach { put(it.emotion.id, it.emotion) }
    }

    fun find(id: String): Emotion? = byId[id]

    fun band(familyIndex: Int, depth: Int): Emotion = families[familyIndex].bands[depth]

    /** Dyad clockwise of [familyIndex], between it and the next family. */
    fun dyadAfter(familyIndex: Int): EmotionDyad = dyads[familyIndex]

    /** Dyad counterclockwise of [familyIndex]. */
    fun dyadBefore(familyIndex: Int): EmotionDyad = dyads[(familyIndex + families.size - 1) % families.size]

    private fun family(
        index: Int,
        key: String,
        mild: Pair<String, Long>,
        medium: Pair<String, Long>,
        intense: Pair<String, Long>,
    ): EmotionFamily = EmotionFamily(
        index = index,
        key = key,
        bands = listOf(mild, medium, intense).mapIndexed { depth, (name, color) ->
            Emotion(id = "$key-$depth", name = name, colorArgb = color)
        },
    )

    private fun dyad(key: String, name: String, left: Int, right: Int): EmotionDyad {
        val mixed = lightenOpaque(
            mixOpaque(families[left].medium.colorArgb, families[right].medium.colorArgb),
            whitePortion = 0.5f,
        )
        return EmotionDyad(
            emotion = Emotion(id = "dyad-$key", name = name, colorArgb = mixed),
            leftIndex = left,
            rightIndex = right,
        )
    }
}

internal fun mixOpaque(a: Long, b: Long): Long {
    fun channel(shift: Int): Int {
        val left = ((a shr shift) and 0xFF).toInt()
        val right = ((b shr shift) and 0xFF).toInt()
        return (left + right) / 2
    }
    return packOpaque(channel(16), channel(8), channel(0))
}

internal fun lightenOpaque(color: Long, whitePortion: Float): Long {
    fun channel(shift: Int): Int {
        val value = ((color shr shift) and 0xFF).toInt()
        return (255f * whitePortion + value * (1f - whitePortion)).toInt().coerceIn(0, 255)
    }
    return packOpaque(channel(16), channel(8), channel(0))
}

private fun packOpaque(red: Int, green: Int, blue: Int): Long =
    (0xFFL shl 24) or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()

/** Dark ink on light petals, paper on dark petals. */
fun labelArgb(background: Long): Long {
    val red = ((background shr 16) and 0xFF) / 255.0
    val green = ((background shr 8) and 0xFF) / 255.0
    val blue = (background and 0xFF) / 255.0
    fun linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)
    val luminance = 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue)
    return if (luminance > 0.45) 0xFF1C1915 else 0xFFFFFBF7
}
