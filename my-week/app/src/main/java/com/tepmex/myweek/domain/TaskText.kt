package com.tepmex.myweek.domain

object TaskText {
    const val DEFAULT_EMOJI = "•"
    const val EMOJI_LIMIT = 16
    private const val TITLE_LIMIT = 80
    private const val TASK_LIMIT = 200

    fun blockTitle(raw: String): String? =
        raw.trim().take(TITLE_LIMIT).takeIf { it.isNotEmpty() }

    fun emoji(raw: String): String =
        raw.trim().take(EMOJI_LIMIT).ifEmpty { DEFAULT_EMOJI }

    fun task(emojiRaw: String, titleRaw: String): TaskDraft? {
        val title = titleRaw.trim().take(TASK_LIMIT)
        if (title.isEmpty()) return null
        return TaskDraft(emoji(emojiRaw), title)
    }
}
