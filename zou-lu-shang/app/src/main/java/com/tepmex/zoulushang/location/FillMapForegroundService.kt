package com.tepmex.zoulushang.location

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.tepmex.zoulushang.ZouLuShangApp
import com.tepmex.zoulushang.notification.FillMapNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FillMapForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var samplingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
        }

        val cityId = intent?.getLongExtra(EXTRA_CITY_ID, -1L) ?: -1L
        if (cityId < 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!LocationPermissions.hasAll(this)) {
            Log.w(TAG, "Missing permissions; stopping fill-map session")
            stopSession()
            return START_NOT_STICKY
        }

        FillMapNotifications.ensureChannel(this)
        val endsAtMillis = System.currentTimeMillis() + SESSION_DURATION_MS
        FillMapSession.start(cityId, endsAtMillis)
        startForegroundWithType(samplesTaken = 0, endsAtMillis = endsAtMillis)

        samplingJob?.cancel()
        samplingJob = serviceScope.launch {
            runSamplingLoop(cityId, endsAtMillis)
            stopSession()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        samplingJob?.cancel()
        serviceScope.cancel()
        FillMapSession.stop()
        super.onDestroy()
    }

    private suspend fun runSamplingLoop(cityId: Long, endsAtMillis: Long) {
        val app = application as ZouLuShangApp
        while (currentCoroutineContext().isActive && System.currentTimeMillis() < endsAtMillis) {
            captureAndStore(app, cityId)
            updateNotification(endsAtMillis)
            val remaining = endsAtMillis - System.currentTimeMillis()
            if (remaining <= 0) break
            delay(minOf(SAMPLE_INTERVAL_MS, remaining))
        }
    }

    private suspend fun captureAndStore(app: ZouLuShangApp, cityId: Long) {
        val location = fetchCurrentLocation() ?: return
        app.repository.recordLiveLocation(
            cityId = cityId,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.accuracy.takeIf { it.isFinite() },
        )
        FillMapSession.recordSample()
    }

    private suspend fun fetchCurrentLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(this)
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellation.token,
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed; trying last known location", e)
            client.lastLocation.await()
        }
    }

    private fun updateNotification(endsAtMillis: Long) {
        val minutesRemaining = ((endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0) / 60_000).toInt()
        startForegroundWithType(
            samplesTaken = FillMapSession.state.value.samplesTaken,
            endsAtMillis = endsAtMillis,
            minutesRemaining = minutesRemaining,
        )
    }

    private fun startForegroundWithType(
        samplesTaken: Int,
        endsAtMillis: Long,
        minutesRemaining: Int = ((endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0) / 60_000).toInt(),
    ) {
        val notification = FillMapNotifications.buildForegroundNotification(
            context = this,
            samplesTaken = samplesTaken,
            minutesRemaining = minutesRemaining,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FillMapNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(FillMapNotifications.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun stopSession() {
        samplingJob?.cancel()
        FillMapSession.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val TAG = "FillMapForegroundService"
        const val EXTRA_CITY_ID = "city_id"
        private const val ACTION_STOP = "com.tepmex.zoulushang.action.STOP_FILL_MAP"
        private const val SAMPLE_INTERVAL_MS = 60_000L
        private const val SESSION_DURATION_MS = 30 * 60_000L

        fun start(context: Context, cityId: Long) {
            val intent = Intent(context, FillMapForegroundService::class.java).apply {
                putExtra(EXTRA_CITY_ID, cityId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FillMapForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
