package com.tepmex.instantpinyin.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tepmex.instantpinyin.domain.NormRect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "instant_pinyin")

private val KEY_KNOWN_TEXT = stringPreferencesKey("known_hanzi_text")
private val KEY_ONLY_KNOWN = booleanPreferencesKey("only_known")
private val KEY_PINYIN_ONLY = booleanPreferencesKey("pinyin_only")
private val KEY_ZONE_LEFT = floatPreferencesKey("zone_left")
private val KEY_ZONE_TOP = floatPreferencesKey("zone_top")
private val KEY_ZONE_RIGHT = floatPreferencesKey("zone_right")
private val KEY_ZONE_BOTTOM = floatPreferencesKey("zone_bottom")

class KnownHanziStore(private val context: Context) {
    val knownText: Flow<String> = context.dataStore.data.map { it[KEY_KNOWN_TEXT] ?: "" }
    val onlyKnown: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONLY_KNOWN] ?: false }
    val pinyinOnly: Flow<Boolean> = context.dataStore.data.map { it[KEY_PINYIN_ONLY] ?: false }
    val zone: Flow<NormRect> = context.dataStore.data.map { prefs ->
        val left = prefs[KEY_ZONE_LEFT] ?: return@map NormRect.Default
        NormRect(
            left = left,
            top = prefs[KEY_ZONE_TOP] ?: NormRect.Default.top,
            right = prefs[KEY_ZONE_RIGHT] ?: NormRect.Default.right,
            bottom = prefs[KEY_ZONE_BOTTOM] ?: NormRect.Default.bottom,
        ).clamped()
    }

    suspend fun save(text: String) {
        context.dataStore.edit { it[KEY_KNOWN_TEXT] = text }
    }

    suspend fun setOnlyKnown(value: Boolean) {
        context.dataStore.edit { it[KEY_ONLY_KNOWN] = value }
    }

    suspend fun setPinyinOnly(value: Boolean) {
        context.dataStore.edit { it[KEY_PINYIN_ONLY] = value }
    }

    suspend fun setZone(zone: NormRect) {
        val clamped = zone.clamped()
        context.dataStore.edit {
            it[KEY_ZONE_LEFT] = clamped.left
            it[KEY_ZONE_TOP] = clamped.top
            it[KEY_ZONE_RIGHT] = clamped.right
            it[KEY_ZONE_BOTTOM] = clamped.bottom
        }
    }
}
