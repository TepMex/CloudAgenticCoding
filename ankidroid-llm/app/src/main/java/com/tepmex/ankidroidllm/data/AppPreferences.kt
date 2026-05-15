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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ankidroid_llm")

class AppPreferences(private val context: Context) {

    val settings: Flow<StorySettings> = context.dataStore.data.map { p ->
        StorySettings(
            useRemoteLlm = p[KEY_REMOTE] ?: false,
            llmBaseUrl = p[KEY_BASE_URL] ?: "",
            llmToken = p[KEY_TOKEN] ?: "",
            remoteModelName = p[KEY_REMOTE_MODEL] ?: "",
            systemPrompt = p[KEY_PROMPT] ?: DEFAULT_PROMPT,
            litertModelDownloadUrl = p[KEY_MODEL_URL] ?: DEFAULT_MODEL_URL,
            deckNamesCsv = p[KEY_DECKS] ?: "",
            vocabFieldName = p[KEY_FIELD] ?: "",
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
                deckNamesCsv = prefs[KEY_DECKS] ?: "",
                vocabFieldName = prefs[KEY_FIELD] ?: "",
            )
            val next = transform(cur)
            prefs[KEY_REMOTE] = next.useRemoteLlm
            prefs[KEY_BASE_URL] = next.llmBaseUrl
            prefs[KEY_TOKEN] = next.llmToken
            prefs[KEY_REMOTE_MODEL] = next.remoteModelName
            prefs[KEY_PROMPT] = next.systemPrompt
            prefs[KEY_MODEL_URL] = next.litertModelDownloadUrl
            prefs[KEY_DECKS] = next.deckNamesCsv
            prefs[KEY_FIELD] = next.vocabFieldName
        }
    }

    companion object {
        const val DEFAULT_MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"

        const val DEFAULT_PROMPT =
            "You write vivid, concise stories for language learners. Use the learner's vocabulary naturally. " +
                "Prefer one continuous narrative; keep it under about 400 words unless the user asks otherwise."

        private val KEY_REMOTE = booleanPreferencesKey("use_remote_llm")
        private val KEY_BASE_URL = stringPreferencesKey("llm_base_url")
        private val KEY_TOKEN = stringPreferencesKey("llm_token")
        private val KEY_REMOTE_MODEL = stringPreferencesKey("remote_model")
        private val KEY_PROMPT = stringPreferencesKey("system_prompt")
        private val KEY_MODEL_URL = stringPreferencesKey("litert_model_url")
        private val KEY_DECKS = stringPreferencesKey("deck_names_csv")
        private val KEY_FIELD = stringPreferencesKey("vocab_field")
    }
}

data class StorySettings(
    val useRemoteLlm: Boolean,
    val llmBaseUrl: String,
    val llmToken: String,
    val remoteModelName: String,
    val systemPrompt: String,
    val litertModelDownloadUrl: String,
    val deckNamesCsv: String,
    val vocabFieldName: String,
)
