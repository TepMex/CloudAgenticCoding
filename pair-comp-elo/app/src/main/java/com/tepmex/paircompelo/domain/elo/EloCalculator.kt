package com.tepmex.paircompelo.domain.elo

import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import kotlin.math.pow

/**
 * Pure Elo rating calculator.
 *
 * Expected score for A against B:
 * `expectedA = 1 / (1 + 10 ^ ((ratingB - ratingA) / ratingScale))`
 *
 * Rating update:
 * `newRating = rating + kFactor * (actual - expected)`
 *
 * Actual scores: win = 1.0, draw = 0.5, loss = 0.0. Skips do not change ratings.
 */
object EloCalculator {

    data class ExpectedScores(val expectedA: Double, val expectedB: Double)

    data class RatingUpdate(
        val newRatingA: Double,
        val newRatingB: Double,
        val expectedA: Double,
        val expectedB: Double,
        val actualA: Double,
        val actualB: Double,
    )

    fun expectedScores(ratingA: Double, ratingB: Double, ratingScale: Double): ExpectedScores {
        require(ratingScale > 0.0) { "ratingScale must be > 0" }
        require(ratingA.isFinite() && ratingB.isFinite()) { "ratings must be finite" }
        val expectedA = 1.0 / (1.0 + 10.0.pow((ratingB - ratingA) / ratingScale))
        return ExpectedScores(expectedA = expectedA, expectedB = 1.0 - expectedA)
    }

    fun actualScores(outcome: ComparisonOutcome, leftIsA: Boolean = true): Pair<Double, Double>? {
        return when (outcome) {
            ComparisonOutcome.SKIPPED -> null
            ComparisonOutcome.DRAW -> 0.5 to 0.5
            ComparisonOutcome.LEFT_WINS -> if (leftIsA) 1.0 to 0.0 else 0.0 to 1.0
            ComparisonOutcome.RIGHT_WINS -> if (leftIsA) 0.0 to 1.0 else 1.0 to 0.0
        }
    }

    fun updateRatings(
        ratingA: Double,
        ratingB: Double,
        outcome: ComparisonOutcome,
        kFactor: Double,
        ratingScale: Double,
        leftIsA: Boolean = true,
    ): RatingUpdate? {
        require(kFactor > 0.0) { "kFactor must be > 0" }
        val actuals = actualScores(outcome, leftIsA) ?: return null
        val expected = expectedScores(ratingA, ratingB, ratingScale)
        val newA = ratingA + kFactor * (actuals.first - expected.expectedA)
        val newB = ratingB + kFactor * (actuals.second - expected.expectedB)
        return RatingUpdate(
            newRatingA = newA,
            newRatingB = newB,
            expectedA = expected.expectedA,
            expectedB = expected.expectedB,
            actualA = actuals.first,
            actualB = actuals.second,
        )
    }
}
