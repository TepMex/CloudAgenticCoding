package com.tepmex.tinglistories.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

class StoryPlayer {
    private var player: MediaPlayer? = null
    private var generation = 0

    fun play(file: File, onDone: () -> Unit) {
        stop()
        val gen = ++generation
        val mp = MediaPlayer()
        var finished = false
        fun finish() {
            if (finished || gen != generation) return
            finished = true
            if (player === mp) {
                player = null
                mp.release()
            }
            onDone()
        }
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener { finish() }
            mp.setOnErrorListener { _, _, _ ->
                finish()
                true
            }
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            mp.release()
            if (player === mp) player = null
            throw e
        }
    }

    fun stop() {
        generation++
        val current = player
        player = null
        current?.runCatching { release() }
    }
}
