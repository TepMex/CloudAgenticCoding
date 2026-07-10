package com.tepmex.paircompelo.domain.pairing

import com.tepmex.paircompelo.domain.model.PairCandidate
import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import com.tepmex.paircompelo.domain.model.Rateable
import java.util.UUID
import kotlin.math.abs
import kotlin.random.Random

/**
 * Selects pairs of active entities for comparison.
 *
 * Strategies:
 * - [PairSelectionStrategy.RANDOM]: uniform random distinct pair
 * - [PairSelectionStrategy.SIMILAR_RATING]: prefer close Elo ratings
 * - [PairSelectionStrategy.LEAST_COMPARED]: prefer fewest comparisons
 * - [PairSelectionStrategy.BALANCED_ADAPTIVE] (default): weighted mix of
 *   low comparison counts, rating proximity, recent-pair avoidance, and
 *   few prior head-to-head meetings
 *
 * Returns null when fewer than two entities are available.
 */
class PairSelector(
    private val random: Random = Random.Default,
) {

    data class PairHistory(
        /** Ordered recent pair keys ("id1|id2" with sorted UUIDs). */
        val recentPairKeys: List<String> = emptyList(),
        /** Head-to-head comparison counts keyed by sorted pair key. */
        val headToHeadCounts: Map<String, Int> = emptyMap(),
    )

    fun select(
        entities: List<Rateable>,
        strategy: PairSelectionStrategy,
        history: PairHistory = PairHistory(),
    ): PairCandidate? {
        if (entities.size < 2) return null
        return when (strategy) {
            PairSelectionStrategy.RANDOM -> selectRandom(entities)
            PairSelectionStrategy.SIMILAR_RATING -> selectSimilarRating(entities)
            PairSelectionStrategy.LEAST_COMPARED -> selectLeastCompared(entities)
            PairSelectionStrategy.BALANCED_ADAPTIVE -> selectBalancedAdaptive(entities, history)
        }
    }

    fun selectRandom(entities: List<Rateable>): PairCandidate? {
        if (entities.size < 2) return null
        val left = entities[random.nextInt(entities.size)]
        var right = entities[random.nextInt(entities.size)]
        var guard = 0
        while (right.id == left.id && guard++ < 32) {
            right = entities[random.nextInt(entities.size)]
        }
        if (right.id == left.id) {
            right = entities.first { it.id != left.id }
        }
        return PairCandidate(left, right)
    }

    fun selectSimilarRating(entities: List<Rateable>): PairCandidate? {
        if (entities.size < 2) return null
        val sorted = entities.sortedBy { it.rating }
        // Sample a few adjacent windows and pick the closest gap, with light randomness.
        val candidates = mutableListOf<PairCandidate>()
        for (i in 0 until sorted.lastIndex) {
            candidates += PairCandidate(sorted[i], sorted[i + 1])
        }
        // Prefer smallest absolute rating difference; break ties randomly.
        val minDiff = candidates.minOf { abs(it.left.rating - it.right.rating) }
        val close = candidates.filter { abs(it.left.rating - it.right.rating) <= minDiff + 1e-9 }
        return close[random.nextInt(close.size)]
    }

    fun selectLeastCompared(entities: List<Rateable>): PairCandidate? {
        if (entities.size < 2) return null
        val sorted = entities.sortedBy { it.comparisonCount }
        // Take the least-compared half (at least 2) and pick randomly within it.
        val poolSize = maxOf(2, sorted.size / 2)
        val pool = sorted.take(poolSize)
        return selectRandom(pool)
    }

    fun selectBalancedAdaptive(
        entities: List<Rateable>,
        history: PairHistory,
    ): PairCandidate? {
        if (entities.size < 2) return null
        val recentSet = history.recentPairKeys.takeLast(20).toSet()
        val pairs = mutableListOf<PairCandidate>()
        val weights = mutableListOf<Double>()

        for (i in entities.indices) {
            for (j in i + 1 until entities.size) {
                val a = entities[i]
                val b = entities[j]
                val key = pairKey(a.id, b.id)
                val ratingDiff = abs(a.rating - b.rating)
                val comparisonPenalty = (a.comparisonCount + b.comparisonCount).toDouble()
                val h2h = history.headToHeadCounts[key]?.toDouble() ?: 0.0
                val recentPenalty = if (key in recentSet) 8.0 else 0.0
                // Higher weight = more desirable. Invert penalties.
                val weight = 1.0 /
                    (1.0 + ratingDiff / 50.0 + comparisonPenalty / 10.0 + h2h * 2.0 + recentPenalty)
                pairs += PairCandidate(a, b)
                weights += weight.coerceAtLeast(1e-9)
            }
        }

        val total = weights.sum()
        var ticket = random.nextDouble() * total
        for (idx in pairs.indices) {
            ticket -= weights[idx]
            if (ticket <= 0) return pairs[idx]
        }
        return pairs.last()
    }

    companion object {
        fun pairKey(a: UUID, b: UUID): String {
            val (first, second) = if (a.toString() <= b.toString()) a to b else b to a
            return "$first|$second"
        }
    }
}
