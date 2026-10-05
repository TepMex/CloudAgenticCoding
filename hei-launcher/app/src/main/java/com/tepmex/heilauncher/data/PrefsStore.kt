package com.tepmex.heilauncher.data

import android.content.Context
import com.tepmex.heilauncher.domain.ClockFormat
import com.tepmex.heilauncher.domain.NameSize

data class LauncherPrefs(
    val clockFormat: ClockFormat = ClockFormat.SYSTEM,
    val showYearProgress: Boolean = true,
    val showDayProgress: Boolean = true,
    val showBattery: Boolean = true,
    val showWeather: Boolean = true,
    val nameSize: NameSize = NameSize.L,
    val favouriteIds: List<String> = emptyList(),
)

class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val lock = Any()
    private val listeners = mutableListOf<(LauncherPrefs) -> Unit>()

    fun read(): LauncherPrefs = synchronized(lock) { readLocked() }

    fun update(transform: (LauncherPrefs) -> LauncherPrefs): LauncherPrefs {
        val next: LauncherPrefs
        val notify = synchronized(lock) {
            val current = readLocked()
            next = transform(current)
            if (next != current) {
                writeLocked(next)
                listeners.toList()
            } else {
                emptyList()
            }
        }
        notify.forEach { it(next) }
        return next
    }

    fun addListener(listener: (LauncherPrefs) -> Unit) {
        synchronized(lock) { listeners.add(listener) }
    }

    fun removeListener(listener: (LauncherPrefs) -> Unit) {
        synchronized(lock) { listeners.remove(listener) }
    }

    private fun readLocked(): LauncherPrefs {
        val clock = prefs.getString(KEY_CLOCK, ClockFormat.SYSTEM.name)
        val size = prefs.getString(KEY_SIZE, NameSize.L.name)
        val favourites = prefs.getString(KEY_FAVS, "").orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return LauncherPrefs(
            clockFormat = enumValue(clock, ClockFormat.SYSTEM),
            showYearProgress = prefs.getBoolean(KEY_YEAR, true),
            showDayProgress = prefs.getBoolean(KEY_DAY, true),
            showBattery = prefs.getBoolean(KEY_BATTERY, true),
            showWeather = prefs.getBoolean(KEY_WEATHER, true),
            nameSize = enumValue(size, NameSize.L),
            favouriteIds = favourites,
        )
    }

    private fun writeLocked(value: LauncherPrefs) {
        prefs.edit()
            .putString(KEY_CLOCK, value.clockFormat.name)
            .putBoolean(KEY_YEAR, value.showYearProgress)
            .putBoolean(KEY_DAY, value.showDayProgress)
            .putBoolean(KEY_BATTERY, value.showBattery)
            .putBoolean(KEY_WEATHER, value.showWeather)
            .putString(KEY_SIZE, value.nameSize.name)
            .putString(KEY_FAVS, value.favouriteIds.joinToString("\n"))
            .apply()
    }

    private fun <T : Enum<T>> enumValue(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return fallback.declaringJavaClass.enumConstants?.firstOrNull { it.name == raw } ?: fallback
    }

    companion object {
        private const val FILE = "hei_launcher"
        private const val KEY_CLOCK = "clock"
        private const val KEY_YEAR = "year"
        private const val KEY_DAY = "day"
        private const val KEY_BATTERY = "battery"
        private const val KEY_WEATHER = "weather"
        private const val KEY_SIZE = "name_size"
        private const val KEY_FAVS = "favourites"
    }
}
