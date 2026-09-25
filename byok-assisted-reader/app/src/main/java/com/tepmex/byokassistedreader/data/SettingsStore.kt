package com.tepmex.byokassistedreader.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ReaderSettings(
    val baseUrl: String = "",
    val token: String = "",
    val model: String = "",
    val knownWords: String = "",
    val volumeKeys: Boolean = true,
    val bookUri: String = "",
    val pageIndex: Int = 0,
) {
    val endpointReady: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

private val Context.dataStore by preferencesDataStore("byok_reader")

class SettingsStore(private val context: Context) {
    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs -> prefs.toSettings() }

    suspend fun saveConnection(baseUrl: String, token: String, model: String, knownWords: String, volumeKeys: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl.trim()
            prefs[TOKEN] = token.trim()
            prefs[MODEL] = model.trim()
            prefs[KNOWN] = knownWords
            prefs[VOLUME] = volumeKeys
        }
    }

    suspend fun saveBook(uri: String, pageIndex: Int) {
        context.dataStore.edit { prefs ->
            prefs[BOOK] = uri
            prefs[PAGE] = pageIndex
        }
    }

    private fun Preferences.toSettings() = ReaderSettings(
        baseUrl = this[BASE_URL].orEmpty(),
        token = this[TOKEN].orEmpty(),
        model = this[MODEL].orEmpty(),
        knownWords = this[KNOWN].orEmpty(),
        volumeKeys = this[VOLUME] ?: true,
        bookUri = this[BOOK].orEmpty(),
        pageIndex = this[PAGE] ?: 0,
    )

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val MODEL = stringPreferencesKey("model")
        val KNOWN = stringPreferencesKey("known_words")
        val VOLUME = booleanPreferencesKey("volume_keys")
        val BOOK = stringPreferencesKey("book_uri")
        val PAGE = intPreferencesKey("page_index")
    }
}
