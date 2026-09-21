package com.tepmex.duoshaoqian.data

import kotlin.random.Random

data class Round(
    val product: Product,
    val priceYuan: Int,
)

data class ShopSnapshot(
    val round: Round,
    val tender: List<Int>,
    val listened: Boolean,
    val correctCount: Int,
    val streak: Int,
) {
    val tenderYuan: Int get() = tender.sum()
}

class ShopGame(
    private val products: List<Product>,
    private val random: Random = Random.Default,
) {
    init {
        require(products.isNotEmpty()) { "Need at least one product with audio prices" }
    }

    var snapshot: ShopSnapshot = ShopSnapshot(
        round = newRound(avoidProductId = null),
        tender = emptyList(),
        listened = false,
        correctCount = 0,
        streak = 0,
    )
        private set

    fun markListened() {
        snapshot = snapshot.copy(listened = true)
    }

    fun addNote(yuan: Int) {
        snapshot = snapshot.copy(tender = snapshot.tender + yuan)
    }

    fun removeNoteAt(index: Int) {
        if (index !in snapshot.tender.indices) return
        snapshot = snapshot.copy(tender = snapshot.tender.filterIndexed { i, _ -> i != index })
    }

    fun clearTender() {
        snapshot = snapshot.copy(tender = emptyList())
    }

    enum class PayResult { Correct, Incorrect, NeedListen }

    fun pay(): PayResult {
        if (!snapshot.listened) return PayResult.NeedListen
        if (snapshot.tenderYuan != snapshot.round.priceYuan) {
            snapshot = snapshot.copy(streak = 0)
            return PayResult.Incorrect
        }
        snapshot = snapshot.copy(
            correctCount = snapshot.correctCount + 1,
            streak = snapshot.streak + 1,
        )
        return PayResult.Correct
    }

    fun advanceAfterSuccess() {
        snapshot = ShopSnapshot(
            round = newRound(avoidProductId = snapshot.round.product.id),
            tender = emptyList(),
            listened = false,
            correctCount = snapshot.correctCount,
            streak = snapshot.streak,
        )
    }

    fun skip() {
        snapshot = ShopSnapshot(
            round = newRound(avoidProductId = snapshot.round.product.id),
            tender = emptyList(),
            listened = false,
            correctCount = snapshot.correctCount,
            streak = 0,
        )
    }

    private fun newRound(avoidProductId: String?): Round {
        // Sample the spoken yuan amount first so cheap snacks cannot drown out
        // 三十块 / 四十块 just because more stall photos sit in the 2–12 band.
        val amounts = products.flatMap { it.priceYuanOptions }.distinct()
        val price = amounts.random(random)
        val matching = products.filter { price in it.priceYuanOptions }
        val product = matching.filter { it.id != avoidProductId }.ifEmpty { matching }.random(random)
        return Round(product, price)
    }
}
