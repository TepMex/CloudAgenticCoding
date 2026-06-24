package com.tepmex.ankidashboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    suspend fun recordDebtSnapshot(deckKey: String, debt: Int) {
        val today = LocalDate.now().format(DAY_FMT)
        context.dataStore.edit { prefs ->
            val current = parseDebtHistory(prefs[KEY_DEBT_HISTORY])
            val deckMap = current.getOrPut(deckKey) { linkedMapOf() }
            deckMap[today] = debt
            prefs[KEY_DEBT_HISTORY] = serializeDebtHistory(current)
        }
    }

    suspend fun getDebtHistory(deckKey: String): List<Pair<String, Int>> {
        val prefs = context.dataStore.data.first()
        val deckMap = parseDebtHistory(prefs[KEY_DEBT_HISTORY])[deckKey] ?: return emptyList()
        return deckMap.entries.sortedBy { it.key }.map { it.key to it.value }
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
        private val DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE
        private val KEY_SELECTED_DECKS = stringSetPreferencesKey("selected_decks")
        private val KEY_COLLECTION_URI = stringPreferencesKey("collection_uri")
        private val KEY_LEECH_FIELDS = stringPreferencesKey("leech_fields")
        private val KEY_DEBT_HISTORY = stringPreferencesKey("debt_history")
        private val KEY_ANKIWEB_USERNAME = stringPreferencesKey("ankiweb_username")
        private val KEY_ANKIWEB_ENDPOINT = stringPreferencesKey("ankiweb_endpoint")
        private val KEY_ANKIWEB_HKEY = stringPreferencesKey("ankiweb_hkey")
        private val KEY_ANKIWEB_SYNCED_AT = longPreferencesKey("ankiweb_synced_at")
        private val KEY_ANKIWEB_SERVER_MOD = longPreferencesKey("ankiweb_server_mod")

        fun debtDeckKey(deckNames: List<String>): String =
            deckNames.sorted().joinToString("|")

        private fun parseDebtHistory(raw: String?): Map<String, LinkedHashMap<String, Int>> {
            if (raw.isNullOrBlank()) return emptyMap()
            val out = linkedMapOf<String, LinkedHashMap<String, Int>>()
            for (line in raw.split("\n")) {
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                val deckKey = line.substring(0, colon)
                val days = linkedMapOf<String, Int>()
                val entries = line.substring(colon + 1)
                if (entries.isNotEmpty()) {
                    for (entry in entries.split(",")) {
                        val eq = entry.indexOf('=')
                        if (eq <= 0) continue
                        days[entry.substring(0, eq)] = entry.substring(eq + 1).toIntOrNull() ?: continue
                    }
                }
                out[deckKey] = days
            }
            return out
        }

        private fun serializeDebtHistory(
            data: Map<String, LinkedHashMap<String, Int>>,
        ): String =
            data.entries.joinToString("\n") { (deckKey, days) ->
                val dayPart = days.entries.joinToString(",") { "${it.key}=${it.value}" }
                "$deckKey:$dayPart"
            }
    }
}
