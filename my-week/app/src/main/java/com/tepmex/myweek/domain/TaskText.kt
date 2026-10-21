package com.tepmex.myweek.domain

object TaskText {
    const val DEFAULT_EMOJI = "•"
    private const val TITLE_LIMIT = 80
    private const val TASK_LIMIT = 200
    private const val EMOJI_LIMIT = 16

    fun blockTitle(raw: String): String? =
        raw.trim().take(TITLE_LIMIT).takeIf { it.isNotEmpty() }

    fun task(emojiRaw: String, titleRaw: String): TaskDraft? {
        val title = titleRaw.trim().take(TASK_LIMIT)
        if (title.isEmpty()) return null
        val emoji = emojiRaw.trim().take(EMOJI_LIMIT).ifEmpty { DEFAULT_EMOJI }
        return TaskDraft(emoji, title)
    }
}
