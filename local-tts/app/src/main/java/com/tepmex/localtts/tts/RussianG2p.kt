package com.tepmex.localtts.tts

/**
 * Russian grapheme-to-phoneme rules from vosk-tts g2p.py.
 */
object RussianG2p {
    private val softLetters = setOf('я', 'ё', 'ю', 'и', 'ь', 'е')
    private val startSyl = setOf('#', 'ъ', 'ь', 'а', 'я', 'о', 'ё', 'у', 'ю', 'э', 'е', 'и', 'ы', '-')
    private val others = setOf("#", "+", "-", "ь", "ъ")

    private val softHardCons = mapOf(
        'б' to "b", 'в' to "v", 'г' to "g", 'Г' to "g", 'д' to "d",
        'з' to "z", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'ф' to "f", 'х' to "h",
    )

    private val otherCons = mapOf(
        'ж' to "zh", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'й' to "j",
    )

    private val vowels = mapOf(
        'а' to "a", 'я' to "a", 'у' to "u", 'ю' to "u", 'о' to "o", 'ё' to "o",
        'э' to "e", 'е' to "e", 'и' to "i", 'ы' to "y",
    )

    fun convert(stressWord: String): String {
        val chars = ("#$stressWord#").toList()
        val stressPhones = mutableListOf<Pair<String, Int>>()
        var stress = 0
        for (ch in chars) {
            if (ch == '+') {
                stress = 1
            } else {
                stressPhones.add(ch.toString() to stress)
                stress = 0
            }
        }
        pallatize(stressPhones)
        val converted = convertVowels(stressPhones)
        return converted.filter { it !in others }.joinToString(" ")
    }

    private fun pallatize(phones: MutableList<Pair<String, Int>>) {
        for (i in 0 until phones.size - 1) {
            val phone = phones[i].first
            if (phone.length == 1 && phone[0] in softHardCons) {
                phones[i] = if (phones[i + 1].first[0] in softLetters) {
                    softHardCons[phone[0]]!! + "j" to 0
                } else {
                    softHardCons[phone[0]]!! to 0
                }
            }
            if (phone.length == 1 && phone[0] in otherCons) {
                phones[i] = otherCons[phone[0]]!! to 0
            }
        }
    }

    private fun convertVowels(phones: List<Pair<String, Int>>): List<String> {
        val newPhones = mutableListOf<String>()
        var prev = ""
        for (phone in phones) {
            val ch = phone.first
            if (prev in startSyl.map { it.toString() }) {
                if (ch.length == 1 && ch[0] in setOf('я', 'ю', 'е', 'ё')) {
                    newPhones.add("j")
                }
            }
            when {
                ch.length == 1 && ch[0] in vowels ->
                    newPhones.add(vowels[ch[0]]!! + phone.second)
                ch.length == 1 && ch[0] in latinVowels ->
                    newPhones.add(latinVowels[ch[0]]!! + phone.second)
                else ->
                    newPhones.add(ch)
            }
            prev = if (ch.isNotEmpty()) ch[0].toString() else ""
        }
        return newPhones
    }
}
