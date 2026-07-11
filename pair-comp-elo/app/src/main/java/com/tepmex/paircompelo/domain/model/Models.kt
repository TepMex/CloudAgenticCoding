package com.tepmex.paircompelo.domain.model

import java.time.Instant
import java.util.UUID

data class PreferenceList(
    val id: UUID,
    val name: String,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val rating: Double,
    val ratingUpdatedAt: Instant,
    val comparisonCount: Int = 0,
) {
    val isArchived: Boolean get() = archivedAt != null
}

data class PreferenceItem(
    val id: UUID,
    val listId: UUID,
    val name: String,
    val description: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null,
    val rating: Double,
    val ratingUpdatedAt: Instant,
    val comparisonCount: Int = 0,
    val winCount: Int = 0,
    val lossCount: Int = 0,
    val skipCount: Int = 0,
    /** Explicit order within a list for manual reordering. */
    val sortOrder: Int = 0,
) {
    val isArchived: Boolean get() = archivedAt != null
}

enum class ComparisonOutcome {
    LEFT_WINS,
    RIGHT_WINS,
    DRAW,
    SKIPPED,
}

data class ItemComparison(
    val id: UUID,
    val listId: UUID,
    val leftItemId: UUID,
    val rightItemId: UUID,
    val winnerItemId: UUID? = null,
    val outcome: ComparisonOutcome,
    val comparedAt: Instant,
    val leftRatingBefore: Double,
    val rightRatingBefore: Double,
    val leftRatingAfter: Double,
    val rightRatingAfter: Double,
    val kFactorUsed: Double,
    val decayFactorUsed: Double,
    val isReverted: Boolean = false,
)

data class ListComparison(
    val id: UUID,
    val leftListId: UUID,
    val rightListId: UUID,
    val winnerListId: UUID? = null,
    val outcome: ComparisonOutcome,
    val comparedAt: Instant,
    val leftRatingBefore: Double,
    val rightRatingBefore: Double,
    val leftRatingAfter: Double,
    val rightRatingAfter: Double,
    val kFactorUsed: Double,
    val decayFactorUsed: Double,
    val isReverted: Boolean = false,
)

enum class PairSelectionStrategy {
    RANDOM,
    SIMILAR_RATING,
    LEAST_COMPARED,
    BALANCED_ADAPTIVE,
}

/**
 * Global ranking settings. Designed so list-specific overrides can be added later
 * without changing the core Elo/decay algorithms.
 */
data class RankingSettings(
    val initialRating: Double = DEFAULT_INITIAL_RATING,
    val kFactor: Double = DEFAULT_K_FACTOR,
    val ratingScale: Double = DEFAULT_RATING_SCALE,
    val decayEnabled: Boolean = true,
    val decayRatePerDay: Double = DEFAULT_DECAY_RATE,
    val minimumComparisonsBeforeStable: Int = DEFAULT_MIN_COMPARISONS,
    val pairSelectionStrategy: PairSelectionStrategy = PairSelectionStrategy.BALANCED_ADAPTIVE,
    val allowDraws: Boolean = true,
    val allowSkipping: Boolean = true,
    val showRatingsDuringComparison: Boolean = true,
) {
    companion object {
        const val DEFAULT_INITIAL_RATING = 1000.0
        const val DEFAULT_K_FACTOR = 32.0
        const val DEFAULT_RATING_SCALE = 400.0
        const val DEFAULT_DECAY_RATE = 0.995
        const val DEFAULT_MIN_COMPARISONS = 5

        val Defaults = RankingSettings()
    }
}

/** Rateable entity used by Elo/decay/pair-selection algorithms. */
data class Rateable(
    val id: UUID,
    val rating: Double,
    val ratingUpdatedAt: Instant?,
    val comparisonCount: Int,
)

data class PairCandidate(
    val left: Rateable,
    val right: Rateable,
)

data class RankedEntry(
    val id: UUID,
    val name: String,
    val rating: Double,
    val rank: Int,
    val comparisonCount: Int,
    val isStable: Boolean,
    val ratingDeltaFromInitial: Double,
)
