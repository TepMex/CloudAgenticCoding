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
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anki_entertainer")

private val KEY_BASE_URL = stringPreferencesKey("llm_base_url")
private val KEY_TOKEN = stringPreferencesKey("llm_token")
private val KEY_MODELS = stringPreferencesKey("model_names_lines")
private val KEY_PROVIDERS = stringPreferencesKey("llm_providers_json")
private val KEY_CHUNK_PROMPT = stringPreferencesKey("chunk_prompt")
private val KEY_CHUNK_COUNT = intPreferencesKey("chunk_count")

/**
 * One OpenAI-compatible LLM endpoint.
 * Providers are tried in list order when earlier ones fail to respond.
 */
data class LlmProvider(
    val baseUrl: String = "",
    val token: String = "",
    val modelNames: List<String> = emptyList(),
)

data class AppSettings(
    val providers: List<LlmProvider>,
    val chunkPrompt: String,
    val chunkCount: Int,
)

class AppPreferences(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        prefs.toAppSettings()
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = prefs.toAppSettings()
            val next = transform(current)
            prefs[KEY_PROVIDERS] = encodeProviders(next.providers)
            // Clear legacy single-provider keys after migrating to the list form.
            prefs.remove(KEY_BASE_URL)
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_MODELS)
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

private fun Preferences.toAppSettings(): AppSettings {
    val providers = resolveProviders(
        providersJson = this[KEY_PROVIDERS],
        legacyBaseUrl = this[KEY_BASE_URL],
        legacyToken = this[KEY_TOKEN],
        legacyModelsText = this[KEY_MODELS],
    )
    return AppSettings(
        providers = providers,
        chunkPrompt = this[KEY_CHUNK_PROMPT] ?: AppPreferences.DEFAULT_CHUNK_PROMPT,
        chunkCount = this[KEY_CHUNK_COUNT] ?: AppPreferences.DEFAULT_CHUNK_COUNT,
    )
}

/**
 * Prefer the providers JSON list; otherwise migrate legacy single-provider keys.
 * Always returns at least one (possibly empty) provider slot for the settings UI.
 */
fun resolveProviders(
    providersJson: String?,
    legacyBaseUrl: String?,
    legacyToken: String?,
    legacyModelsText: String?,
): List<LlmProvider> {
    if (!providersJson.isNullOrBlank()) {
        return decodeProviders(providersJson).ifEmpty { listOf(LlmProvider()) }
    }
    val legacyUrl = legacyBaseUrl.orEmpty()
    val legacyTokenValue = legacyToken.orEmpty()
    val legacyModels = parseModelLines(legacyModelsText.orEmpty())
    return if (legacyUrl.isNotBlank() || legacyTokenValue.isNotBlank() || legacyModels.isNotEmpty()) {
        listOf(
            LlmProvider(
                baseUrl = legacyUrl,
                token = legacyTokenValue,
                modelNames = legacyModels,
            ),
        )
    } else {
        listOf(LlmProvider())
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

fun LlmProvider.isConfigured(): Boolean =
    baseUrl.isNotBlank() && modelNames.isNotEmpty()

fun AppSettings.configuredProviders(): List<LlmProvider> =
    providers.filter { it.isConfigured() }

fun AppSettings.isLlmConfigured(): Boolean =
    configuredProviders().isNotEmpty()

fun encodeProviders(providers: List<LlmProvider>): String {
    val array = JSONArray()
    for (provider in providers) {
        array.put(
            JSONObject()
                .put("baseUrl", provider.baseUrl)
                .put("token", provider.token)
                .put("models", encodeModelLines(provider.modelNames)),
        )
    }
    return array.toString()
}

fun decodeProviders(json: String): List<LlmProvider> {
    if (json.isBlank()) return emptyList()
    return try {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                add(
                    LlmProvider(
                        baseUrl = obj.optString("baseUrl").trim(),
                        token = obj.optString("token"),
                        modelNames = parseModelLines(obj.optString("models")),
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Legacy QUERY-only helper. Generation uses [com.tepmex.ankientertainer.data.hanzi.PromptTemplateEngine]
 * for full placeholder expansion; do not add metadata placeholders here.
 */
fun AppSettings.expandPrompt(vocab: String): String =
    chunkPrompt.replace("{QUERY}", vocab)
