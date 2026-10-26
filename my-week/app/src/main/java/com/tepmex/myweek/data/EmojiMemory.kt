package com.tepmex.myweek.data

import android.content.Context
import com.tepmex.myweek.domain.EmojiChoices
import com.tepmex.myweek.domain.TaskText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray

/** Custom emoji typed by the user. Built-in choices are not stored here. */
class EmojiMemory(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()
    private var generation = 0
    private val _remembered = MutableStateFlow(decode(prefs.getString(KEY, null)))

    val remembered: StateFlow<List<String>> = _remembered

    fun generation(): Int = synchronized(lock) { generation }

    fun remember(emoji: String) {
        synchronized(lock) { addLocked(listOf(emoji)) }
    }

    /** Ignores the write when [clear] ran after [seenGeneration] was read. */
    fun rememberAll(emojis: Collection<String>, seenGeneration: Int) {
        synchronized(lock) {
            if (seenGeneration != generation) return
            addLocked(emojis)
        }
    }

    fun clear() {
        synchronized(lock) {
            generation += 1
            persistLocked(emptyList())
        }
    }

    private fun addLocked(raws: Collection<String>) {
        var next = _remembered.value
        var changed = false
        for (raw in raws) {
            val emoji = raw.trim().take(TaskText.EMOJI_LIMIT)
            if (emoji.isEmpty() || EmojiChoices.isBuiltin(emoji) || emoji in next) continue
            next = next + emoji
            changed = true
        }
        if (!changed) return
        if (next.size > MAX_REMEMBERED) next = next.takeLast(MAX_REMEMBERED)
        persistLocked(next)
    }

    private fun persistLocked(items: List<String>) {
        prefs.edit().putString(KEY, encode(items)).apply()
        _remembered.value = items
    }

    private fun encode(items: List<String>): String =
        JSONArray().apply { items.forEach { put(it) } }.toString()

    private fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index).trim().take(TaskText.EMOJI_LIMIT)
                    if (value.isNotEmpty() && !EmojiChoices.isBuiltin(value)) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val FILE = "my_week_settings"
        private const val KEY = "remembered_emoji"
        private const val MAX_REMEMBERED = 64
    }
}
