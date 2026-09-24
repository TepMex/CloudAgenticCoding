package com.tepmex.duoshaoqian.data

import android.content.Context
import android.media.MediaPlayer
import com.tepmex.duoshaoqian.R

class AnswerSfxPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(tone: AnswerTone) {
        stop()
        val resId = when (tone) {
            AnswerTone.Success -> R.raw.sfx_success
            AnswerTone.Error -> R.raw.sfx_error
        }
        val mediaPlayer = MediaPlayer.create(context, resId) ?: return
        player = mediaPlayer
        mediaPlayer.setOnCompletionListener { completed ->
            if (player === completed) {
                completed.release()
                player = null
            }
        }
        mediaPlayer.setOnErrorListener { failed, _, _ ->
            if (player === failed) {
                failed.release()
                player = null
            }
            true
        }
        mediaPlayer.start()
    }

    fun stop() {
        player?.run {
            runCatching {
                setOnCompletionListener(null)
                setOnErrorListener(null)
                if (isPlaying) stop()
            }
            release()
        }
        player = null
    }
}
