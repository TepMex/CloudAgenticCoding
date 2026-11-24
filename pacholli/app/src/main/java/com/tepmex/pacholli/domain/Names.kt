package com.tepmex.pacholli.domain

import java.util.Locale

object Names {
    val ACCOUNT = Regex("""[A-Z][A-Za-z0-9-]*(:[A-Z][A-Za-z0-9-]*)+""")
    val LEAF = Regex("""[A-Z][A-Za-z0-9-]*""")
    val COMMODITY = Regex("""[A-Z][A-Z0-9'._-]{0,22}""")

    private val letters = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    fun leafFromDisplay(display: String): String {
        val parts = display.trim().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        val joined = parts.joinToString("") { part ->
            val latin = buildString {
                for (ch in part) {
                    val lower = ch.lowercaseChar()
                    when {
                        letters.containsKey(lower) -> append(letters.getValue(lower))
                        lower in 'a'..'z' || lower in '0'..'9' -> append(lower)
                        else -> Unit
                    }
                }
            }
            if (latin.isEmpty()) "" else latin.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        var leaf = joined.ifEmpty { "Account" }
        leaf = leaf.replace(Regex("[^A-Za-z0-9-]"), "")
        if (leaf.isEmpty()) leaf = "Account"
        if (!leaf[0].isLetter()) leaf = "A$leaf"
        if (leaf[0].isLowerCase()) leaf = leaf.replaceFirstChar { it.titlecase(Locale.ROOT) }
        return leaf
    }

    fun fullName(section: AccountSection, parent: Account?, leaf: String): String {
        return if (parent == null) "${section.root}:$leaf" else "${parent.beancountName}:$leaf"
    }

    fun leafOf(beancountName: String): String = beancountName.substringAfterLast(':')

    fun sectionOfRoot(beancountName: String): AccountSection? = when (beancountName.substringBefore(':')) {
        "Assets" -> AccountSection.ASSET
        "Liabilities" -> AccountSection.LIABILITY
        "Equity" -> AccountSection.EQUITY
        "Income" -> AccountSection.INCOME
        "Expenses" -> AccountSection.EXPENSE
        else -> null
    }
}
