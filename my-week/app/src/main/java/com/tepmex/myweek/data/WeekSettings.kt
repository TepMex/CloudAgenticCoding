package com.tepmex.myweek.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class WeekSettings(
    context: Context,
    private val realToday: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val _debug = MutableStateFlow(prefs.getBoolean(KEY_DEBUG, false))
    private val _override = MutableStateFlow(readOverride())

    val debug: StateFlow<Boolean> = _debug
    val overrideDate: StateFlow<LocalDate?> = _override

    fun today(): LocalDate {
        val override = _override.value
        return if (_debug.value && override != null) override else realToday()
    }

    fun setDebug(enabled: Boolean) {
        val editor = prefs.edit().putBoolean(KEY_DEBUG, enabled)
        if (!enabled) {
            editor.remove(KEY_DAY)
            _override.value = null
        }
        editor.apply()
        _debug.value = enabled
    }

    fun setOverride(date: LocalDate?) {
        if (!_debug.value) return
        val editor = prefs.edit()
        if (date == null) {
            editor.remove(KEY_DAY)
        } else {
            editor.putLong(KEY_DAY, date.toEpochDay())
        }
        editor.apply()
        _override.value = date
    }

    private fun readOverride(): LocalDate? {
        if (!prefs.getBoolean(KEY_DEBUG, false)) return null
        if (!prefs.contains(KEY_DAY)) return null
        return LocalDate.ofEpochDay(prefs.getLong(KEY_DAY, 0))
    }

    companion object {
        private const val FILE = "my_week_settings"
        private const val KEY_DEBUG = "debug"
        private const val KEY_DAY = "override_epoch_day"
    }
}
