package com.tepmex.idealtiming.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tepmex.idealtiming.mi.AuthToken
import org.json.JSONObject

class AuthTokenStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "ideal_timing_auth",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): AuthToken? = AuthToken.fromJson(prefs.getString(KEY_TOKEN, null))

    fun save(token: AuthToken) {
        prefs.edit().putString(KEY_TOKEN, token.toJson()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    companion object {
        private const val KEY_TOKEN = "auth_token_json"
    }
}

data class WakeSnapshot(
    val wakeEpochSec: Long,
    val syncedAtEpochSec: Long,
    val sourceDateEpochSec: Long = 0L,
    val sleepScore: Int = 0,
) {
    fun toJson(): String = JSONObject()
        .put("wake_epoch_sec", wakeEpochSec)
        .put("synced_at_epoch_sec", syncedAtEpochSec)
        .put("source_date_epoch_sec", sourceDateEpochSec)
        .put("sleep_score", sleepScore)
        .toString()

    companion object {
        fun fromJson(raw: String?): WakeSnapshot? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                val wake = o.optLong("wake_epoch_sec", 0L)
                if (wake <= 0L) null
                else WakeSnapshot(
                    wakeEpochSec = wake,
                    syncedAtEpochSec = o.optLong("synced_at_epoch_sec", 0L),
                    sourceDateEpochSec = o.optLong("source_date_epoch_sec", 0L),
                    sleepScore = o.optInt("sleep_score", 0),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

class WakeSnapshotStore(context: Context) {
    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            "ideal_timing_wake",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): WakeSnapshot? = WakeSnapshot.fromJson(prefs.getString(KEY_WAKE, null))

    fun save(snapshot: WakeSnapshot) {
        prefs.edit().putString(KEY_WAKE, snapshot.toJson()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_WAKE).apply()
    }

    companion object {
        private const val KEY_WAKE = "wake_snapshot_json"
    }
}
