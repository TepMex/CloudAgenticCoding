package com.tepmex.ankidashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "anki_dashboard")

data class AnkiWebAuth(
    val hkey: String?,
    val username: String?,
    val endpoint: String?,
    val syncedAt: Long?,
    val serverMod: Long?,
)

class AppPreferences(private val context: Context) {

    val selectedDecks: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_DECKS] ?: emptySet()
    }

    val collectionUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_COLLECTION_URI]
    }

    val ankiWebUsername: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ANKIWEB_USERNAME].orEmpty()
    }

    val ankiWebEndpoint: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_ANKIWEB_ENDPOINT] ?: DEFAULT_ANKIWEB_ENDPOINT
    }

    val ankiWebSyncedAt: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ANKIWEB_SYNCED_AT]
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

    suspend fun getAnkiWebAuth(): AnkiWebAuth {
        val prefs = context.dataStore.data.first()
        return AnkiWebAuth(
            hkey = prefs[KEY_ANKIWEB_HKEY],
            username = prefs[KEY_ANKIWEB_USERNAME],
            endpoint = prefs[KEY_ANKIWEB_ENDPOINT],
            syncedAt = prefs[KEY_ANKIWEB_SYNCED_AT],
            serverMod = prefs[KEY_ANKIWEB_SERVER_MOD],
        )
    }

    suspend fun saveAnkiWebSettings(username: String, endpoint: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ANKIWEB_USERNAME] = username
            prefs[KEY_ANKIWEB_ENDPOINT] = endpoint
        }
    }

    suspend fun saveAnkiWebAuth(
        hkey: String,
        endpoint: String,
        username: String,
        syncedAt: Long,
        serverMod: Long?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ANKIWEB_HKEY] = hkey
            prefs[KEY_ANKIWEB_ENDPOINT] = endpoint
            prefs[KEY_ANKIWEB_USERNAME] = username
            prefs[KEY_ANKIWEB_SYNCED_AT] = syncedAt
            if (serverMod != null) {
                prefs[KEY_ANKIWEB_SERVER_MOD] = serverMod
            } else {
                prefs.remove(KEY_ANKIWEB_SERVER_MOD)
            }
        }
    }

    suspend fun clearAnkiWebAuth() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ANKIWEB_HKEY)
            prefs.remove(KEY_ANKIWEB_SYNCED_AT)
            prefs.remove(KEY_ANKIWEB_SERVER_MOD)
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
        const val DEFAULT_ANKIWEB_ENDPOINT = "https://sync.ankiweb.net/"
        private val KEY_SELECTED_DECKS = stringSetPreferencesKey("selected_decks")
        private val KEY_COLLECTION_URI = stringPreferencesKey("collection_uri")
        private val KEY_LEECH_FIELDS = stringPreferencesKey("leech_fields")
        private val KEY_ANKIWEB_USERNAME = stringPreferencesKey("ankiweb_username")
        private val KEY_ANKIWEB_ENDPOINT = stringPreferencesKey("ankiweb_endpoint")
        private val KEY_ANKIWEB_HKEY = stringPreferencesKey("ankiweb_hkey")
        private val KEY_ANKIWEB_SYNCED_AT = longPreferencesKey("ankiweb_synced_at")
        private val KEY_ANKIWEB_SERVER_MOD = longPreferencesKey("ankiweb_server_mod")
    }
}
