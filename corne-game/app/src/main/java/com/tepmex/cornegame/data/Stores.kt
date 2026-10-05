package com.tepmex.cornegame.data

import com.tepmex.cornegame.domain.Language
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Настройки и история — плоские JSON-файлы, не Room.
 * Записей мало, связей нет, схема не мигрирует. Файл легко проверить в юнит-тесте.
 */

data class AppSettings(
    val highlight: Boolean = true,
    val fingerColors: Boolean = false,
    val vibrate: Boolean = true,
    val keyScale: Float = 1f,
    val language: Language = Language.EN,
)

data class SessionRecord(
    val finishedAtEpochMs: Long,
    val language: Language,
    val wpm: Double,
    val accuracy: Double,
    val errors: Int,
    val durationMs: Long,
)

interface SettingsStore {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

interface SessionHistoryStore {
    fun load(): List<SessionRecord>
    fun append(record: SessionRecord)
}

class FileSettingsStore(private val file: File) : SettingsStore {
    override fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        return try {
            val json = JSONObject(file.readText())
            val language = runCatching {
                Language.valueOf(json.optString("language", Language.EN.name))
            }.getOrDefault(Language.EN)
            AppSettings(
                highlight = json.optBoolean("highlight", true),
                fingerColors = json.optBoolean("fingerColors", false),
                vibrate = json.optBoolean("vibrate", true),
                keyScale = json.optDouble("keyScale", 1.0).toFloat().coerceIn(0.65f, 1f),
                language = language,
            )
        } catch (_: Exception) {
            AppSettings()
        }
    }

    override fun save(settings: AppSettings) {
        val json = JSONObject()
            .put("highlight", settings.highlight)
            .put("fingerColors", settings.fingerColors)
            .put("vibrate", settings.vibrate)
            .put("keyScale", settings.keyScale.toDouble())
            .put("language", settings.language.name)
        file.parentFile?.mkdirs()
        file.writeText(json.toString())
    }
}

class FileSessionHistory(private val file: File) : SessionHistoryStore {
    @Synchronized
    override fun load(): List<SessionRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONObject(file.readText()).optJSONArray("sessions") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val language = runCatching {
                        Language.valueOf(item.getString("language"))
                    }.getOrNull() ?: continue
                    add(
                        SessionRecord(
                            finishedAtEpochMs = item.getLong("finishedAtEpochMs"),
                            language = language,
                            wpm = item.getDouble("wpm"),
                            accuracy = item.getDouble("accuracy"),
                            errors = item.getInt("errors"),
                            durationMs = item.getLong("durationMs"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    override fun append(record: SessionRecord) {
        val kept = (load() + record).takeLast(MAX_SESSIONS)
        val array = JSONArray()
        kept.forEach { item ->
            array.put(
                JSONObject()
                    .put("finishedAtEpochMs", item.finishedAtEpochMs)
                    .put("language", item.language.name)
                    .put("wpm", item.wpm)
                    .put("accuracy", item.accuracy)
                    .put("errors", item.errors)
                    .put("durationMs", item.durationMs),
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(JSONObject().put("sessions", array).toString())
    }

    private companion object {
        const val MAX_SESSIONS = 200
    }
}
