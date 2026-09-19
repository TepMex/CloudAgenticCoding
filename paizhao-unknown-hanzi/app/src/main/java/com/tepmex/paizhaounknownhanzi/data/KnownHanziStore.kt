package com.tepmex.paizhaounknownhanzi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "paizhao_unknown_hanzi")

private val KEY_KNOWN_TEXT = stringPreferencesKey("known_hanzi_text")

class KnownHanziStore(private val context: Context) {
    val knownText: Flow<String> = context.dataStore.data.map { it[KEY_KNOWN_TEXT] ?: "" }

    suspend fun save(text: String) {
        context.dataStore.edit { it[KEY_KNOWN_TEXT] = text }
    }
}
