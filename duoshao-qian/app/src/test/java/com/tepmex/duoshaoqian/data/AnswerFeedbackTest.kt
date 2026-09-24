package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnswerFeedbackTest {
    @Test
    fun correctPaysSuccessAndWrongPaysError() {
        assertEquals(AnswerTone.Success, AnswerFeedback.toneFor(ShopGame.PayResult.Correct))
        assertEquals(AnswerTone.Error, AnswerFeedback.toneFor(ShopGame.PayResult.Incorrect))
        assertNull(AnswerFeedback.toneFor(ShopGame.PayResult.NeedListen))
    }

    @Test
    fun bundledClipsMatchTheNamedFiles() {
        assertEquals("sfx_success.mp3", AnswerTone.Success.fileName)
        assertEquals("sfx_error.mp3", AnswerTone.Error.fileName)
        AnswerTone.entries.forEach { tone ->
            val file = rawFile(tone.fileName)
            val header = file.inputStream().use { it.readNBytes(3) }
            assertTrue(
                "${tone.fileName} is not an MP3 (${header.joinToString()})",
                header.contentEquals(byteArrayOf(0x49, 0x44, 0x33)) ||
                    (header.size >= 2 && header[0] == 0xFF.toByte() && (header[1].toInt() and 0xE0) == 0xE0),
            )
            assertTrue("${tone.fileName} is empty", file.length() > 500)
        }
    }

    private fun rawFile(name: String): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        val candidates = generateSequence(start) { it.parentFile }
            .take(6)
            .flatMap { dir ->
                sequenceOf(
                    File(dir, "src/main/res/raw/$name"),
                    File(dir, "app/src/main/res/raw/$name"),
                )
            }
        return candidates.firstOrNull { it.isFile }
            ?: error("missing res/raw/$name from $start")
    }
}
