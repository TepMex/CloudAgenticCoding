package com.tepmex.heilauncher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.tepmex.heilauncher.domain.Weather
import com.tepmex.heilauncher.domain.parseOpenMeteo
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class WeatherRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()
    private var lastFailedAttempt = 0L

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun refresh(): Weather? {
        val cached = readCache()
        val now = System.currentTimeMillis()
        if (cached != null && now - cached.fetchedAt < FRESH_MS) return cached.weather
        if (now - lastFailedAttempt < RETRY_MS) return cached?.weather
        if (!hasLocationPermission()) return cached?.weather
        val location = lastLocation() ?: locate()
        if (location == null) {
            lastFailedAttempt = System.currentTimeMillis()
            return cached?.weather
        }
        val fresh = fetch(location.latitude, location.longitude)
        if (fresh == null) {
            lastFailedAttempt = System.currentTimeMillis()
            return cached?.weather
        }
        writeCache(fresh, System.currentTimeMillis())
        lastFailedAttempt = 0L
        return fresh
    }

    @SuppressLint("MissingPermission")
    private fun lastLocation(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        return PROVIDERS.mapNotNull { provider ->
            if (!runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)) {
                return@mapNotNull null
            }
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.filter { now - it.time < MAX_FIX_AGE_MS }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun locate(): Location? {
        if (!hasLocationPermission()) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = PROVIDERS.firstOrNull { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        } ?: return null
        return withTimeoutOrNull(LOCATE_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, executor) { location ->
                        if (cont.isActive) cont.resume(location)
                    }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun fetch(lat: Double, lon: Double): Weather? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 4_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode != 200) {
                null
            } else {
                connection.inputStream.bufferedReader().use { parseOpenMeteo(it.readText()) }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun readCache(): Cached? {
        if (!prefs.contains(KEY_AT)) return null
        val at = prefs.getLong(KEY_AT, 0L)
        if (at <= 0L) return null
        return Cached(Weather(prefs.getInt(KEY_TEMP, 0), prefs.getInt(KEY_CODE, 0)), at)
    }

    private fun writeCache(weather: Weather, at: Long) {
        prefs.edit()
            .putInt(KEY_TEMP, weather.tempC)
            .putInt(KEY_CODE, weather.code)
            .putLong(KEY_AT, at)
            .apply()
    }

    private data class Cached(val weather: Weather, val fetchedAt: Long)

    companion object {
        private const val FILE = "hei_launcher"
        private const val KEY_TEMP = "weather_c"
        private const val KEY_CODE = "weather_code"
        private const val KEY_AT = "weather_at"
        private const val FRESH_MS = 30L * 60 * 1000
        private const val RETRY_MS = 10L * 60 * 1000
        private const val MAX_FIX_AGE_MS = 6L * 60 * 60 * 1000
        private const val LOCATE_MS = 2_500L
        private val PROVIDERS = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
        )
    }
}
