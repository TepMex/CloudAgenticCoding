package com.tepmex.tinglistories.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LlmSettings(
    val baseUrl: String = "",
    val token: String = "",
    val model: String = "",
) {
    val endpointReady: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

private val Context.dataStore by preferencesDataStore("tingli_settings")

class SettingsStore(private val context: Context) {
    val settings: Flow<LlmSettings> = context.dataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun save(baseUrl: String, token: String, model: String) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl.trim()
            prefs[TOKEN] = token.trim()
            prefs[MODEL] = model.trim()
        }
    }

    private fun Preferences.toSettings() = LlmSettings(
        baseUrl = this[BASE_URL].orEmpty(),
        token = this[TOKEN].orEmpty(),
        model = this[MODEL].orEmpty(),
    )

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val MODEL = stringPreferencesKey("model")
    }
}
