package com.tepmex.myweek.domain

import kotlin.math.abs
import kotlin.random.Random

/**
 * Balanced adaptive pair selection from pair-comp-elo.
 *
 * Higher weight is more desirable. The weight falls as the rating gap grows,
 * as the two options have been compared more often, as they have met each
 * other before, and when the pair is among the last 20 pairs.
 */
class StepPairs(
    private val random: Random = Random.Default,
) {
    data class History(
        val recentPairKeys: List<String> = emptyList(),
        val headToHeadCounts: Map<String, Int> = emptyMap(),
    )

    data class Candidate(val leftKey: String, val rightKey: String)

    fun select(options: List<StepOption>, history: History = History()): Candidate? {
        if (options.size < 2) return null
        val recent = history.recentPairKeys.takeLast(20).toSet()
        val pairs = mutableListOf<Candidate>()
        val weights = mutableListOf<Double>()
        for (i in options.indices) {
            for (j in i + 1 until options.size) {
                val a = options[i]
                val b = options[j]
                val key = pairKey(a.key, b.key)
                val weight = 1.0 / (
                    1.0 +
                        abs(a.rating - b.rating) / 50.0 +
                        (a.comparisonCount + b.comparisonCount) / 10.0 +
                        (history.headToHeadCounts[key]?.toDouble() ?: 0.0) * 2.0 +
                        if (key in recent) 8.0 else 0.0
                    )
                pairs += Candidate(a.key, b.key)
                weights += weight.coerceAtLeast(1e-9)
            }
        }
        val total = weights.sum()
        var ticket = random.nextDouble() * total
        for (index in pairs.indices) {
            ticket -= weights[index]
            if (ticket <= 0.0) return pairs[index]
        }
        return pairs.last()
    }

    companion object {
        fun pairKey(a: String, b: String): String {
            val (first, second) = if (a <= b) a to b else b to a
            return "$first\u0001$second"
        }
    }
}
