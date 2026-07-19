package com.tepmex.ankientertainer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anki_entertainer")

private val KEY_BASE_URL = stringPreferencesKey("llm_base_url")
private val KEY_TOKEN = stringPreferencesKey("llm_token")
private val KEY_MODELS = stringPreferencesKey("model_names_lines")
private val KEY_CHUNK_PROMPT = stringPreferencesKey("chunk_prompt")
private val KEY_CHUNK_COUNT = intPreferencesKey("chunk_count")

data class AppSettings(
    val llmBaseUrl: String,
    val llmToken: String,
    val modelNames: List<String>,
    val chunkPrompt: String,
    val chunkCount: Int,
)

class AppPreferences(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            llmBaseUrl = prefs[KEY_BASE_URL] ?: "",
            llmToken = prefs[KEY_TOKEN] ?: "",
            modelNames = parseModelLines(prefs[KEY_MODELS] ?: ""),
            chunkPrompt = prefs[KEY_CHUNK_PROMPT] ?: DEFAULT_CHUNK_PROMPT,
            chunkCount = prefs[KEY_CHUNK_COUNT] ?: DEFAULT_CHUNK_COUNT,
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                llmBaseUrl = prefs[KEY_BASE_URL] ?: "",
                llmToken = prefs[KEY_TOKEN] ?: "",
                modelNames = parseModelLines(prefs[KEY_MODELS] ?: ""),
                chunkPrompt = prefs[KEY_CHUNK_PROMPT] ?: DEFAULT_CHUNK_PROMPT,
                chunkCount = prefs[KEY_CHUNK_COUNT] ?: DEFAULT_CHUNK_COUNT,
            )
            val next = transform(current)
            prefs[KEY_BASE_URL] = next.llmBaseUrl
            prefs[KEY_TOKEN] = next.llmToken
            prefs[KEY_MODELS] = encodeModelLines(next.modelNames)
            prefs[KEY_CHUNK_PROMPT] = next.chunkPrompt
            prefs[KEY_CHUNK_COUNT] = next.chunkCount.coerceIn(MIN_CHUNK_COUNT, MAX_CHUNK_COUNT)
        }
    }

    companion object {
        const val DEFAULT_CHUNK_COUNT = 5
        const val MIN_CHUNK_COUNT = 1
        const val MAX_CHUNK_COUNT = 20

        const val DEFAULT_CHUNK_PROMPT =
            "You write short, entertaining text snippets for language learners. " +
                "Each snippet should naturally use or relate to this vocabulary word: {QUERY}. " +
                "Keep each snippet under about 80 words. Output plain text only."
    }
}

fun parseModelLines(text: String): List<String> =
    text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

fun encodeModelLines(models: List<String>): String =
    models.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")

fun AppSettings.isLlmConfigured(): Boolean =
    llmBaseUrl.isNotBlank() && modelNames.isNotEmpty()

/**
 * Legacy QUERY-only helper. Generation uses [com.tepmex.ankientertainer.data.hanzi.PromptTemplateEngine]
 * for full placeholder expansion; do not add metadata placeholders here.
 */
fun AppSettings.expandPrompt(vocab: String): String =
    chunkPrompt.replace("{QUERY}", vocab)
