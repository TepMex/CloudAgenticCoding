package com.tepmex.paircompelo.domain.pairing

import com.google.common.truth.Truth.assertThat
import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import com.tepmex.paircompelo.domain.model.Rateable
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

class PairSelectorTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val selector = PairSelector(Random(42))

    private fun entity(rating: Double, comps: Int = 0) = Rateable(
        id = UUID.randomUUID(),
        rating = rating,
        ratingUpdatedAt = now,
        comparisonCount = comps,
    )

    @Test
    fun fewerThanTwo_returnsNull() {
        assertThat(selector.select(emptyList(), PairSelectionStrategy.RANDOM)).isNull()
        assertThat(selector.select(listOf(entity(1000.0)), PairSelectionStrategy.RANDOM)).isNull()
    }

    @Test
    fun random_returnsDistinctPair() {
        val entities = List(5) { entity(1000.0 + it) }
        repeat(20) {
            val pair = selector.select(entities, PairSelectionStrategy.RANDOM)!!
            assertThat(pair.left.id).isNotEqualTo(pair.right.id)
        }
    }

    @Test
    fun similarRating_prefersClosePair() {
        val closeA = entity(1000.0)
        val closeB = entity(1005.0)
        val far = entity(1600.0)
        val pair = selector.selectSimilarRating(listOf(closeA, far, closeB))!!
        val ids = setOf(pair.left.id, pair.right.id)
        assertThat(ids).containsAtLeast(closeA.id, closeB.id)
        assertThat(ids).doesNotContain(far.id)
    }

    @Test
    fun leastCompared_prefersLowCounts() {
        val low1 = entity(1000.0, comps = 0)
        val low2 = entity(1000.0, comps = 1)
        val high = entity(1000.0, comps = 50)
        val high2 = entity(1000.0, comps = 40)
        val pair = selector.selectLeastCompared(listOf(high, low1, high2, low2))!!
        val ids = setOf(pair.left.id, pair.right.id)
        assertThat(ids.contains(high.id)).isFalse()
        assertThat(ids.contains(high2.id)).isFalse()
    }

    @Test
    fun balanced_avoidsRecentPairWhenPossible() {
        val a = entity(1000.0, 0)
        val b = entity(1000.0, 0)
        val c = entity(1000.0, 0)
        val keyAb = PairSelector.pairKey(a.id, b.id)
        val history = PairSelector.PairHistory(
            recentPairKeys = List(20) { keyAb },
            headToHeadCounts = mapOf(keyAb to 10),
        )
        val counts = mutableMapOf<String, Int>()
        repeat(100) {
            val pair = selector.selectBalancedAdaptive(listOf(a, b, c), history)!!
            val key = PairSelector.pairKey(pair.left.id, pair.right.id)
            counts[key] = (counts[key] ?: 0) + 1
        }
        assertThat(counts[keyAb] ?: 0).isLessThan(40)
    }
}
