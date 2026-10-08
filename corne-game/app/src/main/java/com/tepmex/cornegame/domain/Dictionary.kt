package com.tepmex.cornegame.domain

import kotlin.random.Random

/**
 * Частотные слова. В подсказку попадают только те, что состоят из букв базового тапа
 * и имеют длину 3–10. Фильтр в [usableWords] — защита, если список когда-нибудь разъедется с раскладкой.
 * х, ъ и ё на Cornedeon 2M — удержание P, M и T, в слова сессии они не входят.
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

/**
 * Строка закрывает слои Cornedeon 2M: Lower (`(цифра)`), строчные слова,
 * запятую, прописное слово, смену языка ⇄ и хвост на другом языке с точкой.
 * Слова по-прежнему только из букв базового тапа.
 */
fun generatePrompt(
    language: Language,
    random: Random,
): String {
    val primary = usableWords(language).shuffled(random)
    val secondary = usableWords(language.other()).shuffled(random)
    check(primary.size >= 8) { "Словарь $language слишком короткий: ${primary.size}" }
    check(secondary.size >= 4) { "Словарь ${language.other()} слишком короткий: ${secondary.size}" }
    val digit = ('0'.code + random.nextInt(10)).toChar()
    val head = primary.take(6).joinToString(" ")
    val capital = primary[6].replaceFirstChar { it.uppercaseChar() }
    val tailCapital = secondary[0].replaceFirstChar { it.uppercaseChar() }
    val tail = secondary.drop(1).take(3).joinToString(" ")
    return "($digit) $head, $capital $LANGUAGE_SWITCH $tailCapital $tail."
}
