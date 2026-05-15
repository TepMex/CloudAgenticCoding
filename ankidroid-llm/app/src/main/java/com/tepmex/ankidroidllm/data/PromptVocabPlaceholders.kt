package com.tepmex.ankidroidllm.data

/**
 * System prompt may include `{{VOCAB:n}}` to inject the first [n] vocabulary strings
 * (one bullet per line) from the study queue, in card order.
 */
object PromptVocabPlaceholders {

    private val vocabRegex = Regex("\\{\\{VOCAB:(\\d+)\\}\\}")

    fun maxRequestedCount(prompt: String): Int =
        vocabRegex.findAll(prompt).map { m -> m.groupValues[1].toInt() }.maxOrNull() ?: 0

    fun containsAny(prompt: String): Boolean = vocabRegex.containsMatchIn(prompt)

    fun formatBulletList(words: List<String>): String =
        words.joinToString("\n") { w -> "- $w" }

    fun expand(prompt: String, orderedWords: List<String>): String =
        vocabRegex.replace(prompt) { match ->
            val n = match.groupValues[1].toInt().coerceAtLeast(0)
            formatBulletList(orderedWords.take(n))
        }
}
