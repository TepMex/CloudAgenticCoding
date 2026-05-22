package com.tepmex.ankidashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anki_dashboard")

class AppPreferences(private val context: Context) {

    val selectedDecks: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_DECKS] ?: emptySet()
    }

    val collectionUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLLECTION_URI]
    }

    val leechFieldByDeck: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_LEECH_FIELDS] ?: return@map emptyMap()
        raw.split("\n")
            .mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                line.substring(0, idx) to line.substring(idx + 1)
            }
            .toMap()
    }

    suspend fun setSelectedDecks(decks: Set<String>) {
        context.dataStore.edit { it[KEY_SELECTED_DECKS] = decks }
    }

    suspend fun setCollectionUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) {
                it.remove(KEY_COLLECTION_URI)
            } else {
                it[KEY_COLLECTION_URI] = uri
            }
        }
    }

    suspend fun setLeechField(deckName: String, fieldName: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[KEY_LEECH_FIELDS]
                ?.split("\n")
                ?.mapNotNull { line ->
                    val idx = line.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    line.substring(0, idx) to line.substring(idx + 1)
                }
                ?.toMap()
                ?.toMutableMap()
                ?: mutableMapOf()
            current[deckName] = fieldName
            prefs[KEY_LEECH_FIELDS] = current.entries.joinToString("\n") { "${it.key}=${it.value}" }
        }
    }

    companion object {
        private val KEY_SELECTED_DECKS = stringSetPreferencesKey("selected_decks")
        private val KEY_COLLECTION_URI = stringPreferencesKey("collection_uri")
        private val KEY_LEECH_FIELDS = stringPreferencesKey("leech_fields")
    }
}
