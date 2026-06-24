package com.tepmex.zoulushang2.location

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
import com.tepmex.zoulushang2.ZouLuShang2App
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

class PaintForegroundService : Service() {
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

        if (!LocationPermissions.hasAll(this)) {
            Log.w(TAG, "Missing permissions; stopping paint session")
            stopSession()
            return START_NOT_STICKY
        }

        PaintNotifications.ensureChannel(this)
        PaintSession.start()
        startForegroundWithType(strokesApplied = 0)

        samplingJob?.cancel()
        samplingJob = serviceScope.launch {
            runSamplingLoop()
            stopSession()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        samplingJob?.cancel()
        serviceScope.cancel()
        PaintSession.stop()
        super.onDestroy()
    }

    private suspend fun runSamplingLoop() {
        val app = application as ZouLuShang2App
        while (currentCoroutineContext().isActive) {
            captureAndPaint(app)
            updateNotification()
            delay(SAMPLE_INTERVAL_MS)
        }
    }

    private suspend fun captureAndPaint(app: ZouLuShang2App) {
        val location = fetchCurrentLocation() ?: return
        val session = PaintSession.state.value
        val strokes = app.repository.recordLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            lastLatitude = session.lastLatitude,
            lastLongitude = session.lastLongitude,
        )
        PaintSession.recordStroke(
            latitude = location.latitude,
            longitude = location.longitude,
            strokesApplied = strokes,
        )
    }

    private suspend fun fetchCurrentLocation(): android.location.Location? {
        val client = LocationServices.getFusedLocationProviderClient(this)
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellation.token,
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentLocation failed; trying last known location", e)
            client.lastLocation.await()
        }
    }

    private fun updateNotification() {
        startForegroundWithType(strokesApplied = PaintSession.state.value.strokesApplied)
    }

    private fun startForegroundWithType(strokesApplied: Int) {
        val notification = PaintNotifications.buildForegroundNotification(
            context = this,
            strokesApplied = strokesApplied,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PaintNotifications.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(PaintNotifications.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun stopSession() {
        samplingJob?.cancel()
        PaintSession.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val TAG = "PaintForegroundService"
        private const val ACTION_STOP = "com.tepmex.zoulushang2.action.STOP_PAINT"
        private const val SAMPLE_INTERVAL_MS = 3_000L

        fun start(context: Context) {
            val intent = Intent(context, PaintForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PaintForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
