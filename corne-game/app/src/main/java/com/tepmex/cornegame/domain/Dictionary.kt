package com.tepmex.cornegame.domain

import kotlin.random.Random

const val SESSION_WORD_COUNT = 20

/**
 * Частотные слова. В подсказку попадают только те, что состоят из букв базового слоя
 * и имеют длину 3–10. Фильтр в [usableWords] — защита, если список когда-нибудь разъедется с раскладкой.
 */
private val EN_WORDS = listOf(
    "the", "and", "you", "that", "was", "for", "are", "with", "his", "they",
    "this", "have", "from", "one", "had", "word", "but", "not", "what", "all",
    "were", "when", "your", "can", "said", "there", "use", "each", "which", "she",
    "how", "their", "will", "other", "about", "many", "then", "them", "these", "some",
    "her", "would", "make", "like", "him", "into", "time", "has", "look", "two",
    "more", "write", "see", "number", "way", "could", "people", "than", "first", "water",
    "been", "call", "who", "its", "now", "find", "long", "down", "day", "did",
    "get", "come", "made", "may", "part", "over", "new", "only", "year", "take",
    "work", "know", "even", "most", "give", "just", "also", "back", "after", "good",
    "want", "because", "think", "where", "those", "before",
)

private val RU_WORDS = listOf(
    "что", "как", "это", "все", "она", "так", "его", "для", "они", "уже",
    "вас", "нет", "если", "или", "где", "кто", "чем", "год", "там", "мой",
    "под", "над", "без", "про", "еще", "два", "три", "раз", "дом", "мир",
    "день", "рука", "глаз", "дело", "слово", "город", "можно", "нужно", "очень", "после",
    "перед", "между", "через", "такой", "этот", "один", "тоже", "себя", "быть", "знать",
    "жить", "иметь", "время", "место", "жизнь", "работа", "страна", "человек", "сказать",
    "видеть", "думать", "хотеть", "только", "потому", "сейчас", "всегда", "никогда",
    "почему", "тогда", "когда", "много", "мало", "надо", "буду", "борщ", "объект",
    "съезд", "эхо", "юла", "яма", "цыган",
)

fun usableWords(language: Language): List<String> {
    val allowed = baseLetters(language)
    return when (language) {
        Language.EN -> EN_WORDS
        Language.RU -> RU_WORDS
    }.filter { word ->
        word.length in 3..10 && word.all { it in allowed }
    }
}

fun generatePrompt(
    language: Language,
    random: Random,
    wordCount: Int = SESSION_WORD_COUNT,
): String {
    val pool = usableWords(language)
    check(pool.size >= 40) { "Словарь $language слишком короткий: ${pool.size}" }
    return pool.shuffled(random).take(wordCount).joinToString(" ")
}
