package com.tepmex.ankidroidllm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ankidroid_llm")

private val KEY_REMOTE = booleanPreferencesKey("use_remote_llm")
private val KEY_BASE_URL = stringPreferencesKey("llm_base_url")
private val KEY_TOKEN = stringPreferencesKey("llm_token")
private val KEY_REMOTE_MODEL = stringPreferencesKey("remote_model")
private val KEY_PROMPT = stringPreferencesKey("system_prompt")
private val KEY_MODEL_URL = stringPreferencesKey("litert_model_url")
private val KEY_DECK_FIELD_ROWS_JSON = stringPreferencesKey("deck_field_rows_json")
private val KEY_DECKS_LEGACY = stringPreferencesKey("deck_names_csv")
private val KEY_FIELD_LEGACY = stringPreferencesKey("vocab_field")

data class StoryDeckFieldRow(
    val deckName: String,
    val fieldName: String,
)

data class StorySettings(
    val useRemoteLlm: Boolean,
    val llmBaseUrl: String,
    val llmToken: String,
    val remoteModelName: String,
    val systemPrompt: String,
    val litertModelDownloadUrl: String,
    /** Each row selects a deck (empty = all decks) and which note field supplies vocabulary. */
    val deckFieldRows: List<StoryDeckFieldRow>,
)

class AppPreferences(private val context: Context) {

    val settings: Flow<StorySettings> = context.dataStore.data.map { p ->
        StorySettings(
            useRemoteLlm = p[KEY_REMOTE] ?: false,
            llmBaseUrl = p[KEY_BASE_URL] ?: "",
            llmToken = p[KEY_TOKEN] ?: "",
            remoteModelName = p[KEY_REMOTE_MODEL] ?: "",
            systemPrompt = p[KEY_PROMPT] ?: DEFAULT_PROMPT,
            litertModelDownloadUrl = p[KEY_MODEL_URL] ?: DEFAULT_MODEL_URL,
            deckFieldRows = readDeckFieldRows(p),
        )
    }

    suspend fun update(transform: (StorySettings) -> StorySettings) {
        context.dataStore.edit { prefs ->
            val cur = StorySettings(
                useRemoteLlm = prefs[KEY_REMOTE] ?: false,
                llmBaseUrl = prefs[KEY_BASE_URL] ?: "",
                llmToken = prefs[KEY_TOKEN] ?: "",
                remoteModelName = prefs[KEY_REMOTE_MODEL] ?: "",
                systemPrompt = prefs[KEY_PROMPT] ?: DEFAULT_PROMPT,
                litertModelDownloadUrl = prefs[KEY_MODEL_URL] ?: DEFAULT_MODEL_URL,
                deckFieldRows = readDeckFieldRows(prefs),
            )
            val next = transform(cur)
            prefs[KEY_REMOTE] = next.useRemoteLlm
            prefs[KEY_BASE_URL] = next.llmBaseUrl
            prefs[KEY_TOKEN] = next.llmToken
            prefs[KEY_REMOTE_MODEL] = next.remoteModelName
            prefs[KEY_PROMPT] = next.systemPrompt
            prefs[KEY_MODEL_URL] = next.litertModelDownloadUrl
            prefs[KEY_DECK_FIELD_ROWS_JSON] = encodeDeckFieldRowsJson(next.deckFieldRows)
            prefs -= KEY_DECKS_LEGACY
            prefs -= KEY_FIELD_LEGACY
        }
    }

    companion object {
        /** QAT mobile-transformers weights (wNa8o8) published by Google on Hugging Face. */
        const val DEFAULT_MODEL_HF_REPO = "google/gemma-4-E2B-it-qat-mobile-transformers"

        /**
         * LiteRT-LM runtime bundle (.litertlm) for [DEFAULT_MODEL_HF_REPO].
         * Hosted on litert-community until Google publishes a .litertlm on the QAT repo.
         */
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        const val DEFAULT_PROMPT =
            "You write vivid, concise stories for language learners. Use the learner's vocabulary naturally. " +
                "Prefer one continuous narrative; keep it under about 400 words unless the user asks otherwise.\n\n" +
                "Vocabulary from the learner's Anki study queue (due / learning / new), in card order, one item per line:\n" +
                "{{VOCAB:25}}"
    }
}

private fun readDeckFieldRows(p: Preferences): List<StoryDeckFieldRow> {
    val json = p[KEY_DECK_FIELD_ROWS_JSON]
    if (!json.isNullOrBlank()) {
        return decodeDeckFieldRowsJson(json)
    }
    return migrateLegacyDeckFieldRows(p[KEY_DECKS_LEGACY] ?: "", p[KEY_FIELD_LEGACY] ?: "")
}

private fun migrateLegacyDeckFieldRows(decksCsv: String, field: String): List<StoryDeckFieldRow> {
    val decks = decksCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val f = field.trim()
    return when {
        decks.isEmpty() && f.isEmpty() -> emptyList()
        decks.isEmpty() -> listOf(StoryDeckFieldRow("", f))
        else -> decks.map { StoryDeckFieldRow(it, f) }
    }
}

private fun decodeDeckFieldRowsJson(json: String): List<StoryDeckFieldRow> {
    return try {
        val arr = JSONArray(json)
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    StoryDeckFieldRow(
                        deckName = o.optString("deck", ""),
                        fieldName = o.optString("field", ""),
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun encodeDeckFieldRowsJson(rows: List<StoryDeckFieldRow>): String {
    val arr = JSONArray()
    for (r in rows) {
        arr.put(
            JSONObject()
                .put("deck", r.deckName)
                .put("field", r.fieldName),
        )
    }
    return arr.toString()
}
