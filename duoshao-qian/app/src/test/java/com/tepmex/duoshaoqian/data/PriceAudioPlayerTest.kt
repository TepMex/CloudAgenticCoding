package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceAudioPlayerTest {
    @Test
    fun sniffsCommonContainers() {
        assertEquals(".mp3", PriceAudioPlayer.sniffExtension(byteArrayOf(0xFF.toByte(), 0xF3.toByte())))
        assertEquals(".mp3", PriceAudioPlayer.sniffExtension(byteArrayOf(0x49, 0x44, 0x33, 0x04)))
        assertEquals(".ogg", PriceAudioPlayer.sniffExtension(byteArrayOf(0x4F, 0x67, 0x67, 0x53)))
        val wav = ByteArray(12)
        "RIFF".toByteArray().copyInto(wav, 0)
        "WAVE".toByteArray().copyInto(wav, 8)
        assertEquals(".wav", PriceAudioPlayer.sniffExtension(wav))
    }
}
