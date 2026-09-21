package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertTrue
import org.junit.Test

class ProductCatalogTest {
    @Test
    fun touristPricesStayInRealisticBands() {
        val water = ProductCatalog.all.first { it.id == "water" }
        assertTrue(water.priceYuanOptions.all { it in 1..5 })
        val dumplings = ProductCatalog.all.first { it.id == "dumplings" }
        assertTrue(dumplings.priceYuanOptions.all { it in 15..40 })
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
}
