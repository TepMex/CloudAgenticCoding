package com.tepmex.localtts.data

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tepmex.localtts.util.DiagnosticsLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PcmAudioPlayer {

    private const val TAG = "PcmAudioPlayer"

    suspend fun play(pcm: ShortArray, sampleRate: Int) = withContext(Dispatchers.IO) {
        if (pcm.isEmpty()) {
            DiagnosticsLog.w(TAG, "Empty PCM buffer, skipping playback")
            return@withContext
        }
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBuffer, pcm.size * 2))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        try {
            DiagnosticsLog.d(TAG, "AudioTrack write ${pcm.size} samples, buffer=${track.bufferSizeInFrames}")
            track.write(pcm, 0, pcm.size)
            track.play()
            val durationMs = pcm.size * 1000L / sampleRate
            kotlinx.coroutines.delay(durationMs + 200)
            DiagnosticsLog.d(TAG, "Playback finished (~${durationMs}ms)")
        } catch (e: Throwable) {
            DiagnosticsLog.e(TAG, "Playback failed", e)
            throw e
        } finally {
            track.stop()
            track.release()
        }
    }
}
