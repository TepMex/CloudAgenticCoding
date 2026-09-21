package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProductCatalogTest {
    @Test
    fun touristPricesStayInRealisticBands() {
        val water = ProductCatalog.all.first { it.id == "water" }
        assertTrue(water.priceYuanOptions.all { it in 1..5 })
        val teaEgg = ProductCatalog.all.first { it.id == "tea_egg" }
        assertTrue(teaEgg.priceYuanOptions.all { it in 1..5 })
        val dumplings = ProductCatalog.all.first { it.id == "dumplings" }
        assertTrue(dumplings.priceYuanOptions.all { it in 15..40 })
        val roastDuckRice = ProductCatalog.all.first { it.id == "roast_duck_rice" }
        assertTrue(roastDuckRice.priceYuanOptions.all { it in 25..40 })
        ProductCatalog.all.forEach { product ->
            assertTrue("${product.id} has prices", product.priceYuanOptions.isNotEmpty())
            assertTrue("${product.id} never costs 100", product.priceYuanOptions.none { it >= 100 })
        }
    }

    @Test
    fun playableFiltersToAudioAmounts() {
        val playable = ProductCatalog.playable(setOf(3, 25))
        assertTrue(playable.any { it.id == "water" })
        assertTrue(playable.any { it.id == "dumplings" })
        assertTrue(playable.none { it.id == "milk_tea" })
        assertTrue(playable.first { it.id == "water" }.priceYuanOptions == listOf(3))
    }

    @Test
    fun everyBundledSpokenAmountHasAProduct() {
        val catalog = AudioCatalogParser.parse(
            File("src/main/assets/chinese_money_numbers.json").readText(Charsets.UTF_8),
        )
        val offered = ProductCatalog.all.flatMap { it.priceYuanOptions }.toSet()
        assertEquals(catalog.amounts, offered)
    }

    @Test
    fun everyProductHasAStallPhoto() {
        ProductCatalog.all.forEach { product ->
            val file = File("src/main/assets/${product.imageAsset}")
            assertTrue("missing ${file.path}", file.isFile)
        }
    }
}
