package com.tepmex.wozainaar.work

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tepmex.wozainaar.WoZaiNaarApp
import com.tepmex.wozainaar.notification.LocationNotifications
import kotlinx.coroutines.tasks.await
import kotlin.time.Duration.Companion.seconds

class LocationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val runLabel = if (tags.contains(UNIQUE_ONE_TIME_WORK)) "manual" else "periodic"
        TrackingLogger.log("LocationWorker started ($runLabel, attempt=$runAttemptCount)")

        val app = applicationContext as WoZaiNaarApp
        LocationNotifications.ensureChannel(applicationContext)

        val foregroundInfo = buildForegroundInfo()
        setForeground(foregroundInfo)
        TrackingLogger.log("Foreground service notification posted")

        return try {
            TrackingLogger.log("Requesting current location…")
            val location = fetchCurrentLocation()
            if (location != null) {
                val id = app.repository.insertSample(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracyMeters = location.accuracy.takeIf { it.isFinite() },
                )
                val message = "Saved sample #$id at %.5f, %.5f (±%.0f m)".format(
                    location.latitude,
                    location.longitude,
                    location.accuracy,
                )
                TrackingLogger.log(message)
                Log.i(TAG, message)
            } else {
                TrackingLogger.log("No location fix this run (GPS/network may be unavailable)")
                Log.w(TAG, "No location available this run")
            }
            TrackingLogger.log("LocationWorker finished successfully ($runLabel)")
            Result.success()
        } catch (e: SecurityException) {
            TrackingLogger.log("Failed: location permission missing — ${e.message}")
            Log.e(TAG, "Location permission missing", e)
            Result.failure()
        } catch (e: Exception) {
            TrackingLogger.log("Failed: ${e.javaClass.simpleName} — ${e.message}")
            Log.e(TAG, "Location worker failed", e)
            Result.retry()
        }
    }

    private suspend fun fetchCurrentLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(applicationContext)
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            ).await()
        } catch (e: Exception) {
            TrackingLogger.log("getCurrentLocation failed (${e.message}); trying last known location")
            client.lastLocation.await()
        }
    }

    private fun buildForegroundInfo(): ForegroundInfo {
        val notification = LocationNotifications.buildForegroundNotification(applicationContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                LocationNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            ForegroundInfo(
                LocationNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
            )
        }
    }

    companion object {
        const val TAG = "LocationWorker"
        const val UNIQUE_PERIODIC_WORK = "location_periodic"
        const val UNIQUE_ONE_TIME_WORK = "location_one_time"
    }
}
