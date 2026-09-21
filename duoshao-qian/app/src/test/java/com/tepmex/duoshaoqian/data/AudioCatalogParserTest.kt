package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class AudioCatalogParserTest {
    @Test
    fun parsesObjectKeyedByNumberWithAudioBase64() {
        val mp3 = fakeMp3()
        val json = """
            {
              "3": {
                "number": 3,
                "hanzi": "三块",
                "pinyin": "sān kuài",
                "audio_base64": "$mp3"
              },
              "12": {
                "hanzi": "十二块",
                "audioBase64": "$mp3"
              }
            }
        """.trimIndent()
        val catalog = AudioCatalogParser.parse(json)
        assertEquals(setOf(3, 12), catalog.amounts)
        assertEquals("三块", catalog.clip(3)?.hanzi)
        assertEquals(3, catalog.clip(3)?.audioBytes()?.size)
    }

    @Test
    fun parsesArrayAndRawBase64Values() {
        val mp3 = fakeMp3()
        val json = """
            {
              "entries": [
                {"value": 5, "audio": "data:audio/mpeg;base64,$mp3"}
              ],
              "8": "$mp3"
            }
        """.trimIndent()
        val catalog = AudioCatalogParser.parse(json)
        assertEquals(setOf(5, 8), catalog.amounts)
    }

    @Test
    fun parseAmountAcceptsYuanDecorations() {
        assertEquals(15, AudioCatalogParser.parseAmount("15"))
        assertEquals(15, AudioCatalogParser.parseAmount("¥15"))
        assertEquals(15, AudioCatalogParser.parseAmount("15元"))
        assertEquals(15, AudioCatalogParser.parseAmount("15.0"))
    }

    @Test
    fun bundledCatalogHasProductPrices() {
        val file = java.io.File("src/main/assets/chinese_money_numbers.json")
        assertTrue("missing ${file.absolutePath}", file.isFile)
        val catalog = AudioCatalogParser.parse(file.readText(Charsets.UTF_8))
        ProductCatalog.all.flatMap { it.priceYuanOptions }.distinct().forEach { price ->
            assertTrue("missing audio for $price", price in catalog.amounts)
        }
    }

    private fun fakeMp3(): String = Base64.getEncoder().encodeToString(ByteArray(64) { 0xFF.toByte() })
}
