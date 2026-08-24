package com.tepmex.idealtiming.data

import android.content.Context
import android.content.SharedPreferences
import com.tepmex.idealtiming.domain.NfcCheckIn

/**
 * Last NFC physical check-in. Plain prefs (not secrets); ignored once a wake
 * for a different local date is synced.
 */
class NfcCheckInStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ideal_timing_nfc_checkin", Context.MODE_PRIVATE)

    fun load(): NfcCheckIn? = NfcCheckIn.fromJson(prefs.getString(KEY_CHECK_IN, null))

    fun save(checkIn: NfcCheckIn) {
        prefs.edit().putString(KEY_CHECK_IN, checkIn.toJson()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CHECK_IN).apply()
    }

    companion object {
        private const val KEY_CHECK_IN = "nfc_check_in_json"
    }
}
