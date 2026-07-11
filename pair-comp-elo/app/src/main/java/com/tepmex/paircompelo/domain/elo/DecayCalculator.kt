package com.tepmex.paircompelo.domain.elo

import java.time.Duration
import java.time.Instant
import kotlin.math.pow

/**
 * Time-based rating decay toward the configured initial rating.
 *
 * Formula:
 * `decayedRating = initialRating + decayRatePerDay ^ elapsedDays * (currentRating - initialRating)`
 *
 * Notes:
 * - Decay reduces the influence of older comparisons but does not fully model
 *   context-dependent preferences.
 * - [elapsedDays] may be fractional and is based on the exact duration since
 *   [ratingUpdatedAt].
 * - Future timestamps (clock skew) are treated as zero elapsed time — never reverse decay.
 * - When decay is disabled, the current rating is returned unchanged.
 * - When [ratingUpdatedAt] is null, the rating is treated as the initial rating.
 */
object DecayCalculator {

    data class DecayResult(
        val decayedRating: Double,
        /** Multiplier applied to the (current - initial) delta; 1.0 when no decay. */
        val decayFactorUsed: Double,
        val elapsedDays: Double,
    )

    fun decayRating(
        currentRating: Double,
        ratingUpdatedAt: Instant?,
        now: Instant,
        initialRating: Double,
        decayEnabled: Boolean,
        decayRatePerDay: Double,
    ): DecayResult {
        require(initialRating.isFinite()) { "initialRating must be finite" }
        require(currentRating.isFinite()) { "currentRating must be finite" }
        require(decayRatePerDay > 0.0 && decayRatePerDay <= 1.0) {
            "decayRatePerDay must be in (0, 1]"
        }

        if (!decayEnabled) {
            return DecayResult(
                decayedRating = currentRating,
                decayFactorUsed = 1.0,
                elapsedDays = 0.0,
            )
        }

        if (ratingUpdatedAt == null) {
            return DecayResult(
                decayedRating = initialRating,
                decayFactorUsed = 0.0,
                elapsedDays = 0.0,
            )
        }

        val elapsed = Duration.between(ratingUpdatedAt, now)
        val elapsedDays = if (elapsed.isNegative || elapsed.isZero) {
            0.0
        } else {
            elapsed.toMillis() / 86_400_000.0
        }

        if (elapsedDays == 0.0) {
            return DecayResult(
                decayedRating = currentRating,
                decayFactorUsed = 1.0,
                elapsedDays = 0.0,
            )
        }

        val factor = decayRatePerDay.pow(elapsedDays)
        val decayed = initialRating + factor * (currentRating - initialRating)
        return DecayResult(
            decayedRating = decayed,
            decayFactorUsed = factor,
            elapsedDays = elapsedDays,
        )
    }
}
