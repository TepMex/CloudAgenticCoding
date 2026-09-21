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
        val claypotRice = ProductCatalog.all.first { it.id == "claypot_rice" }
        assertTrue(claypotRice.priceYuanOptions.all { it in 40..50 })
        val dapanji = ProductCatalog.all.first { it.id == "dapanji" }
        assertTrue(dapanji.priceYuanOptions.all { it in 100..114 })
        ProductCatalog.all.forEach { product ->
            assertTrue("${product.id} has prices", product.priceYuanOptions.isNotEmpty())
            assertTrue("${product.id} stays in 1..114", product.priceYuanOptions.all { it in 1..114 })
        }
        assertTrue(water.priceYuanOptions.none { it >= 10 })
        assertTrue(teaEgg.priceYuanOptions.none { it >= 10 })
    }

    @Test
    fun sitDownMealsCoverFortyThroughOneHundredFourteen() {
        val offered = ProductCatalog.all.flatMap { it.priceYuanOptions }.toSet()
        (40..114).forEach { amount ->
            assertTrue("missing product for ¥$amount", amount in offered)
        }
    }

    @Test
    fun playableFiltersToAudioAmounts() {
        val playable = ProductCatalog.playable(setOf(3, 25))
        assertTrue(playable.any { it.id == "water" })
        assertTrue(playable.any { it.id == "dumplings" })
        assertTrue(playable.none { it.id == "milk_tea" })
        assertTrue(playable.first { it.id == "water" }.priceYuanOptions == listOf(3))
        val hundred = ProductCatalog.playable(setOf(100))
        assertTrue(hundred.any { it.id == "peking_duck" })
        assertTrue(hundred.any { it.id == "hotpot" })
        assertTrue(hundred.none { it.id == "water" })
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
