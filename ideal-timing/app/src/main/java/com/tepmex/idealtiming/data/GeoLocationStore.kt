package com.tepmex.idealtiming.data

import android.content.Context
import android.content.SharedPreferences
import com.tepmex.idealtiming.domain.GeoPoint
import org.json.JSONObject

/**
 * Last-known user coordinates for offline sunrise / sunset.
 * Plain prefs (not secrets); refreshed whenever a fix is obtained.
 */
class GeoLocationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ideal_timing_geo", Context.MODE_PRIVATE)

    fun load(): GeoPoint? {
        if (!prefs.contains(KEY_LAT) || !prefs.contains(KEY_LON)) return null
        return try {
            GeoPoint(
                latitudeDeg = prefs.getFloat(KEY_LAT, 0f).toDouble(),
                longitudeDeg = prefs.getFloat(KEY_LON, 0f).toDouble(),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(point: GeoPoint) {
        prefs.edit()
            .putFloat(KEY_LAT, point.latitudeDeg.toFloat())
            .putFloat(KEY_LON, point.longitudeDeg.toFloat())
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis() / 1000L)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun updatedAtEpochSec(): Long = prefs.getLong(KEY_UPDATED_AT, 0L)

    fun toDebugJson(): String =
        JSONObject()
            .put("lat", prefs.getFloat(KEY_LAT, Float.NaN).toDouble())
            .put("lon", prefs.getFloat(KEY_LON, Float.NaN).toDouble())
            .put("updated_at", updatedAtEpochSec())
            .toString()

    companion object {
        private const val KEY_LAT = "latitude_deg"
        private const val KEY_LON = "longitude_deg"
        private const val KEY_UPDATED_AT = "updated_at_epoch_sec"
    }
}
