package com.tepmex.instantpinyin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "instant_pinyin")

private val KEY_KNOWN_TEXT = stringPreferencesKey("known_hanzi_text")
private val KEY_ONLY_KNOWN = booleanPreferencesKey("only_known")

class KnownHanziStore(private val context: Context) {
    val knownText: Flow<String> = context.dataStore.data.map { it[KEY_KNOWN_TEXT] ?: "" }
    val onlyKnown: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONLY_KNOWN] ?: false }

    suspend fun save(text: String) {
        context.dataStore.edit { it[KEY_KNOWN_TEXT] = text }
    }

    suspend fun setOnlyKnown(value: Boolean) {
        context.dataStore.edit { it[KEY_ONLY_KNOWN] = value }
    }
}
