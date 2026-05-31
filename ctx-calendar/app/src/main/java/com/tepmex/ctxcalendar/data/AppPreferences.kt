package com.tepmex.ctxcalendar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ctx_calendar")

class AppPreferences(private val context: Context) {

    val takeoutDbUri: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_TAKEOUT_DB_URI]
    }

    suspend fun setTakeoutDbUri(uri: String?) {
        context.dataStore.edit { prefs ->
            if (uri == null) {
                prefs.remove(KEY_TAKEOUT_DB_URI)
            } else {
                prefs[KEY_TAKEOUT_DB_URI] = uri
            }
        }
    }

    companion object {
        private val KEY_TAKEOUT_DB_URI = stringPreferencesKey("takeout_db_uri")
    }
}
