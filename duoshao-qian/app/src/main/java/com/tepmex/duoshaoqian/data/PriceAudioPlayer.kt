package com.tepmex.duoshaoqian.data

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class PriceAudioPlayer(private val context: Context) {
    private var player: MediaPlayer? = null
    private var lastFile: File? = null

    fun play(bytes: ByteArray) {
        stop()
        val ext = sniffExtension(bytes)
        val file = File.createTempFile("duoshao-price-", ext, context.cacheDir)
        file.writeBytes(bytes)
        lastFile = file
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        mediaPlayer.setDataSource(file.absolutePath)
        mediaPlayer.setOnCompletionListener { stop() }
        mediaPlayer.setOnErrorListener { _, _, _ ->
            stop()
            true
        }
        mediaPlayer.prepare()
        mediaPlayer.start()
    }

    fun stop() {
        player?.run {
            runCatching { if (isPlaying) stop() }
            reset()
            release()
        }
        player = null
        lastFile?.delete()
        lastFile = null
    }

    companion object {
        fun sniffExtension(bytes: ByteArray): String {
            if (bytes.size >= 4 && bytes[0] == 0x4F.toByte() && bytes[1] == 0x67.toByte() &&
                bytes[2] == 0x67.toByte() && bytes[3] == 0x53.toByte()
            ) {
                return ".ogg"
            }
            if (bytes.size >= 12 && bytes.copyOfRange(0, 4).toStringAscii() == "RIFF" &&
                bytes.copyOfRange(8, 12).toStringAscii() == "WAVE"
            ) {
                return ".wav"
            }
            if (bytes.size >= 3 && bytes[0] == 0x49.toByte() && bytes[1] == 0x44.toByte() &&
                bytes[2] == 0x33.toByte()
            ) {
                return ".mp3"
            }
            if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && (bytes[1].toInt() and 0xE0) == 0xE0) {
                return ".mp3"
            }
            return ".bin"
        }

        private fun ByteArray.toStringAscii(): String = decodeToString()
    }
}
