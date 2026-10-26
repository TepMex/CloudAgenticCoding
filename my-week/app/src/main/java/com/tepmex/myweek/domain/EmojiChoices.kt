package com.tepmex.myweek.domain

/**
 * Built-in emoji stay available. Emoji the user typed are added beside them.
 * Higher task counts come first. Equal counts keep built-in order, then the
 * order the extra emoji was remembered, then any other emoji already stored on tasks.
 */
object EmojiChoices {
    val builtins: List<String> = listOf(
        "•", "📚", "📖", "✍️", "🎮", "🏃", "💪", "🏊", "🚶",
        "🧹", "👨‍🍳", "🥗", "😴", "⏰", "💼", "🏠", "🎯", "🧠",
    )

    private val builtinSet = builtins.toSet()

    fun order(usage: Map<String, Int>, remembered: List<String>): List<String> {
        val rememberedExtras = LinkedHashSet<String>()
        for (raw in remembered) {
            val emoji = raw.trim()
            if (emoji.isNotEmpty() && emoji !in builtinSet) rememberedExtras.add(emoji)
        }
        val usageExtras = usage.keys
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in builtinSet && it !in rememberedExtras }
            .distinct()
            .sorted()
            .toList()
        val extras = rememberedExtras.toList() + usageExtras
        val rank = HashMap<String, Int>(builtins.size + extras.size)
        builtins.forEachIndexed { index, emoji -> rank[emoji] = index }
        extras.forEachIndexed { index, emoji -> rank[emoji] = builtins.size + index }
        return (builtins + extras).sortedWith(
            compareByDescending<String> { usage[it] ?: 0 }
                .thenBy { rank[it] ?: Int.MAX_VALUE },
        )
    }

    fun isBuiltin(emoji: String): Boolean = emoji in builtinSet
}
