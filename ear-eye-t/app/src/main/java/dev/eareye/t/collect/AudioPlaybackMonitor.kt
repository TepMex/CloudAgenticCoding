package dev.eareye.t.collect

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Reads the public active-playback list. Android already removes players that
 * are paused, stopped, or not considered active before this list reaches a
 * normal app. AudioPlaybackConfiguration.isActive() and getPlayerState() are
 * hidden system APIs and are not called.
 *
 * Membership means "the system reports an active player", not "a person can
 * hear sound". Several players at once are collapsed later by [dev.eareye.t.domain.AudioAggregateReducer].
 * Callback delivery and the initial snapshot are both posted to [handler], so
 * they cannot race each other.
 */
class AudioPlaybackMonitor(
    context: Context,
    private val handler: Handler,
    private val health: MonitoringHealthTracker,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var registered = false

    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (!registered) return
            deliver(configs.orEmpty(), SOURCE_CALLBACK)
        }
    }

    fun start() {
        if (registered) return
        val manager = audioManager ?: error("AudioManager is unavailable")
        manager.registerAudioPlaybackCallback(callback, handler)
        registered = true
        handler.post {
            if (registered) readAndDeliver(SOURCE_SNAPSHOT)
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        try {
            audioManager?.unregisterAudioPlaybackCallback(callback)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "callback was already unregistered")
        }
    }

    fun snapshot() {
        handler.post {
            if (registered) readAndDeliver(SOURCE_SNAPSHOT)
        }
    }

    private fun readAndDeliver(source: String) {
        try {
            val configs = audioManager?.activePlaybackConfigurations.orEmpty()
            deliver(configs, source)
        } catch (error: Throwable) {
            Log.w(TAG, "playback snapshot failed: ${error.javaClass.simpleName}")
            runBlocking(Dispatchers.IO) {
                health.onObservationFailure(error.javaClass.simpleName)
            }
        }
    }

    private fun deliver(configs: List<AudioPlaybackConfiguration>, source: String) {
        val usages = configs.map { configuration -> configuration.audioAttributes.usage }
        runBlocking(Dispatchers.IO) {
            health.onPlaybackCount(usages, source)
        }
    }

    private companion object {
        const val TAG = "EarEyeAudio"
    }
}
