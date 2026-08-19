package com.tepmex.idealtiming.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.tepmex.idealtiming.domain.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a coarse user [GeoPoint] via the platform [LocationManager] (no Play Services).
 * Prefers last-known network/GPS fixes; persists them in [GeoLocationStore] for offline use.
 */
class DeviceLocationSource(
    private val context: Context,
    private val store: GeoLocationStore,
) {
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun cached(): GeoPoint? = store.load()

    /**
     * Best available point: fresh last-known if permitted, else cached.
     */
    suspend fun resolve(): GeoPoint? = withContext(Dispatchers.IO) {
        if (hasLocationPermission()) {
            val fresh = readLastKnown()
            if (fresh != null) {
                store.save(fresh)
                return@withContext fresh
            }
        }
        store.load()
    }

    @SuppressLint("MissingPermission")
    private fun readLastKnown(): GeoPoint? {
        if (!hasLocationPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val candidates = mutableListOf<Location>()
        for (provider in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )) {
            if (!lm.isProviderEnabled(provider) && provider != LocationManager.PASSIVE_PROVIDER) {
                continue
            }
            try {
                lm.getLastKnownLocation(provider)?.let { candidates.add(it) }
            } catch (_: SecurityException) {
                return null
            } catch (_: Exception) {
                // provider unavailable
            }
        }
        val best = candidates.maxByOrNull { it.time } ?: return null
        return GeoPoint(best.latitude, best.longitude)
    }
}
