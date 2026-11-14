package dev.eareye.t.collect

import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.eareye.t.EarEyeApp
import dev.eareye.t.domain.ObservationPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The listener is only a system-bound host for the audio callback. Notification
 * content is ignored on purpose. requestRebind() is not called: the platform
 * documents it as useful only after this process called requestUnbind(), which
 * we never do.
 */
class MonitoringNotificationListenerService : NotificationListenerService() {
    private val gate = Any()
    private var started = false
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var monitor: AudioPlaybackMonitor? = null

    private val heartbeat = object : Runnable {
        override fun run() {
            val currentHandler = handler ?: return
            if (!started) return
            try {
                val needsSnapshot = runBlocking(Dispatchers.IO) {
                    graph().health.onHeartbeat()
                }
                if (needsSnapshot) monitor?.snapshot()
            } catch (error: Throwable) {
                Log.w(TAG, "heartbeat failed: ${error.javaClass.simpleName}")
            }
            if (started) currentHandler.postDelayed(this, ObservationPolicy.HEARTBEAT_INTERVAL_MILLIS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val shouldStart = synchronized(gate) {
            if (started) false else {
                started = true
                true
            }
        }
        if (!shouldStart) return
        val thread = HandlerThread("ear-eye-monitor", Process.THREAD_PRIORITY_BACKGROUND)
        thread.start()
        val createdHandler = Handler(thread.looper)
        handlerThread = thread
        handler = createdHandler
        val createdMonitor = AudioPlaybackMonitor(this, createdHandler, graph().health)
        monitor = createdMonitor
        MonitorBridge.resnapshot = { createdMonitor.snapshot() }
        createdHandler.post {
            if (!started) return@post
            try {
                runBlocking(Dispatchers.IO) { graph().health.onListenerConnected() }
                createdMonitor.start()
                createdHandler.postDelayed(heartbeat, ObservationPolicy.HEARTBEAT_INTERVAL_MILLIS)
            } catch (error: Throwable) {
                Log.e(TAG, "monitor start failed: ${error.javaClass.simpleName}")
                runBlocking(Dispatchers.IO) {
                    graph().repository.addDiagnostic(
                        kind = "monitor_start_failed",
                        message = "Не удалось запустить мониторинг аудио (${error.javaClass.simpleName}).",
                        wallMillis = graph().time.wallTimeMillis(),
                    )
                    graph().health.onCleanDisconnect()
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        teardown()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = Unit

    private fun teardown() {
        val shouldStop = synchronized(gate) {
            if (!started) false else {
                started = false
                true
            }
        }
        if (!shouldStop) return
        MonitorBridge.resnapshot = null
        val currentHandler = handler
        val currentMonitor = monitor
        val thread = handlerThread
        if (currentHandler != null) {
            val latch = CountDownLatch(1)
            currentHandler.post {
                try {
                    currentHandler.removeCallbacks(heartbeat)
                    currentMonitor?.stop()
                    runBlocking(Dispatchers.IO) { graph().health.onCleanDisconnect() }
                } catch (error: Throwable) {
                    Log.w(TAG, "disconnect failed: ${error.javaClass.simpleName}")
                } finally {
                    latch.countDown()
                }
            }
            latch.await(2, TimeUnit.SECONDS)
        }
        thread?.quitSafely()
        monitor = null
        handler = null
        handlerThread = null
        MonitoringRuntime.update { it.copy(listenerConnected = false, audioActive = false, sessionId = null) }
    }

    private fun graph() = (application as EarEyeApp).graph

    private companion object {
        const val TAG = "EarEyeMonitor"
    }
}
