package com.tepmex.duoshaoqian.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShopGameTest {
    private val water = ProductCatalog.all.first { it.id == "water" }
    private val dumplings = ProductCatalog.all.first { it.id == "dumplings" }

    @Test
    fun payRequiresListeningThenExactTender() {
        val game = ShopGame(listOf(water), random = Random(1))
        val price = game.snapshot.round.priceYuan
        assertEquals(ShopGame.PayResult.NeedListen, game.pay())
        game.markListened()
        game.addNote(1)
        assertEquals(ShopGame.PayResult.Incorrect, game.pay())
        assertEquals(0, game.snapshot.streak)
        game.clearTender()
        repeat(price) { game.addNote(1) }
        assertEquals(ShopGame.PayResult.Correct, game.pay())
        assertEquals(1, game.snapshot.correctCount)
        assertEquals(1, game.snapshot.streak)
    }

    @Test
    fun banknotesCanComposeAnyWholeYuanPrice() {
        val game = ShopGame(listOf(dumplings), random = Random(4))
        val price = game.snapshot.round.priceYuan
        game.markListened()
        makeChange(game, price)
        assertEquals(price, game.snapshot.tenderYuan)
        assertEquals(ShopGame.PayResult.Correct, game.pay())
    }

    @Test
    fun successAdvancesProduct() {
        val mango = ProductCatalog.all.first { it.id == "mango" }
        val jianbing = ProductCatalog.all.first { it.id == "jianbing" }
        val game = ShopGame(listOf(mango, jianbing), random = Random(2))
        val firstId = game.snapshot.round.product.id
        game.markListened()
        makeChange(game, game.snapshot.round.priceYuan)
        assertEquals(ShopGame.PayResult.Correct, game.pay())
        game.advanceAfterSuccess()
        assertNotEquals(firstId, game.snapshot.round.product.id)
        assertFalse(game.snapshot.listened)
        assertTrue(game.snapshot.tender.isEmpty())
    }

    @Test
    fun walletContainsEveryCirculatingNote() {
        assertEquals(listOf(1, 5, 10, 20, 50, 100), RmbWallet.notes.map { it.yuan })
    }

    @Test
    fun spokenAmountsAreSampledEvenly() {
        val products = ProductCatalog.all
        val amounts = products.flatMap { it.priceYuanOptions }.toSet()
        val game = ShopGame(products, random = Random(0))
        val counts = amounts.associateWith { 0 }.toMutableMap()
        val roundsPerAmount = 250
        repeat(amounts.size * roundsPerAmount) {
            val price = game.snapshot.round.priceYuan
            counts[price] = counts.getValue(price) + 1
            game.skip()
        }
        amounts.forEach { amount ->
            val seen = counts.getValue(amount)
            assertTrue(
                "¥$amount appeared $seen times, expected about $roundsPerAmount",
                seen in (roundsPerAmount * 3 / 5)..(roundsPerAmount * 7 / 5),
            )
        }
    }

    private fun makeChange(game: ShopGame, price: Int) {
        var remaining = price
        for (note in RmbWallet.notes.sortedByDescending { it.yuan }) {
            while (remaining >= note.yuan) {
                game.addNote(note.yuan)
                remaining -= note.yuan
            }
        }
    }
}
