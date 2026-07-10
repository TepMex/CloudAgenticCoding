package com.tepmex.paircompelo.domain.elo

import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.RankingSettings
import java.time.Instant
import java.util.UUID

/**
 * Applies decay then Elo for a single comparison between two participants.
 * Shared by item and list comparison flows.
 */
object ComparisonEngine {

    data class Participant(
        val id: UUID,
        val rating: Double,
        val ratingUpdatedAt: Instant?,
    )

    data class Result(
        val leftRatingBefore: Double,
        val rightRatingBefore: Double,
        val leftRatingAfter: Double,
        val rightRatingAfter: Double,
        val kFactorUsed: Double,
        val decayFactorUsed: Double,
        val winnerId: UUID?,
    )

    fun apply(
        left: Participant,
        right: Participant,
        outcome: ComparisonOutcome,
        settings: RankingSettings,
        comparedAt: Instant,
    ): Result {
        require(left.id != right.id) { "Comparison entities must be distinct" }

        val leftDecay = DecayCalculator.decayRating(
            currentRating = left.rating,
            ratingUpdatedAt = left.ratingUpdatedAt,
            now = comparedAt,
            initialRating = settings.initialRating,
            decayEnabled = settings.decayEnabled,
            decayRatePerDay = settings.decayRatePerDay,
        )
        val rightDecay = DecayCalculator.decayRating(
            currentRating = right.rating,
            ratingUpdatedAt = right.ratingUpdatedAt,
            now = comparedAt,
            initialRating = settings.initialRating,
            decayEnabled = settings.decayEnabled,
            decayRatePerDay = settings.decayRatePerDay,
        )

        val leftBefore = leftDecay.decayedRating
        val rightBefore = rightDecay.decayedRating
        val decayFactorUsed = (leftDecay.decayFactorUsed + rightDecay.decayFactorUsed) / 2.0

        val update = EloCalculator.updateRatings(
            ratingA = leftBefore,
            ratingB = rightBefore,
            outcome = outcome,
            kFactor = settings.kFactor,
            ratingScale = settings.ratingScale,
        )

        val leftAfter = update?.newRatingA ?: leftBefore
        val rightAfter = update?.newRatingB ?: rightBefore

        val winnerId = when (outcome) {
            ComparisonOutcome.LEFT_WINS -> left.id
            ComparisonOutcome.RIGHT_WINS -> right.id
            ComparisonOutcome.DRAW, ComparisonOutcome.SKIPPED -> null
        }

        return Result(
            leftRatingBefore = leftBefore,
            rightRatingBefore = rightBefore,
            leftRatingAfter = leftAfter,
            rightRatingAfter = rightAfter,
            kFactorUsed = settings.kFactor,
            decayFactorUsed = decayFactorUsed,
            winnerId = winnerId,
        )
    }
}
