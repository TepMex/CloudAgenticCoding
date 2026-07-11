package com.tepmex.paircompelo.domain.ranking

import com.tepmex.paircompelo.domain.elo.DecayCalculator
import com.tepmex.paircompelo.domain.elo.EloCalculator
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.ListComparison
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.PreferenceList
import com.tepmex.paircompelo.domain.model.RankedEntry
import com.tepmex.paircompelo.domain.model.RankingSettings
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

/**
 * Rebuilds ratings from comparison history — the source of truth.
 *
 * Behavior:
 * 1. Reset ratings to [RankingSettings.initialRating]
 * 2. Sort comparisons chronologically
 * 3. Replay in order, applying decay between relevant timestamps
 * 4. Recompute before/after rating snapshots and aggregates
 *
 * Decay is applied once per comparison based on each participant's
 * `ratingUpdatedAt`, then ratings and timestamps are persisted for the next step.
 */
object RankingRecalculator {

    data class ItemState(
        val item: PreferenceItem,
        val comparisons: List<ItemComparison>,
    )

    data class ListState(
        val list: PreferenceList,
        val comparisons: List<ListComparison>,
    )

    data class ItemRecalcResult(
        val items: List<PreferenceItem>,
        val comparisons: List<ItemComparison>,
    )

    data class ListRecalcResult(
        val lists: List<PreferenceList>,
        val comparisons: List<ListComparison>,
    )

    fun recalculateItems(
        items: List<PreferenceItem>,
        comparisons: List<ItemComparison>,
        settings: RankingSettings,
        createdAtFallback: Instant,
    ): ItemRecalcResult {
        val activeComparisons = comparisons
            .filter { !it.isReverted }
            .sortedBy { it.comparedAt }

        val byId = items.associate { item ->
            item.id to item.copy(
                rating = settings.initialRating,
                ratingUpdatedAt = item.createdAt,
                comparisonCount = 0,
                winCount = 0,
                lossCount = 0,
                skipCount = 0,
            )
        }.toMutableMap()

        val rewritten = mutableListOf<ItemComparison>()

        for (comparison in activeComparisons) {
            val left = byId[comparison.leftItemId] ?: continue
            val right = byId[comparison.rightItemId] ?: continue
            if (left.listId != right.listId || left.listId != comparison.listId) continue

            val leftDecay = DecayCalculator.decayRating(
                currentRating = left.rating,
                ratingUpdatedAt = left.ratingUpdatedAt,
                now = comparison.comparedAt,
                initialRating = settings.initialRating,
                decayEnabled = settings.decayEnabled,
                decayRatePerDay = settings.decayRatePerDay,
            )
            val rightDecay = DecayCalculator.decayRating(
                currentRating = right.rating,
                ratingUpdatedAt = right.ratingUpdatedAt,
                now = comparison.comparedAt,
                initialRating = settings.initialRating,
                decayEnabled = settings.decayEnabled,
                decayRatePerDay = settings.decayRatePerDay,
            )

            val decayFactorUsed = (leftDecay.decayFactorUsed + rightDecay.decayFactorUsed) / 2.0
            val leftBefore = leftDecay.decayedRating
            val rightBefore = rightDecay.decayedRating

            val update = EloCalculator.updateRatings(
                ratingA = leftBefore,
                ratingB = rightBefore,
                outcome = comparison.outcome,
                kFactor = settings.kFactor,
                ratingScale = settings.ratingScale,
            )

            val leftAfter = update?.newRatingA ?: leftBefore
            val rightAfter = update?.newRatingB ?: rightBefore

            var leftNext = left.copy(
                rating = leftAfter,
                ratingUpdatedAt = comparison.comparedAt,
                comparisonCount = left.comparisonCount + 1,
                updatedAt = comparison.comparedAt,
            )
            var rightNext = right.copy(
                rating = rightAfter,
                ratingUpdatedAt = comparison.comparedAt,
                comparisonCount = right.comparisonCount + 1,
                updatedAt = comparison.comparedAt,
            )

            when (comparison.outcome) {
                ComparisonOutcome.LEFT_WINS -> {
                    leftNext = leftNext.copy(winCount = leftNext.winCount + 1)
                    rightNext = rightNext.copy(lossCount = rightNext.lossCount + 1)
                }
                ComparisonOutcome.RIGHT_WINS -> {
                    rightNext = rightNext.copy(winCount = rightNext.winCount + 1)
                    leftNext = leftNext.copy(lossCount = leftNext.lossCount + 1)
                }
                ComparisonOutcome.DRAW -> {
                    // Draws count as comparisons but not wins/losses.
                }
                ComparisonOutcome.SKIPPED -> {
                    leftNext = leftNext.copy(skipCount = leftNext.skipCount + 1)
                    rightNext = rightNext.copy(skipCount = rightNext.skipCount + 1)
                }
            }

            byId[left.id] = leftNext
            byId[right.id] = rightNext

            rewritten += comparison.copy(
                leftRatingBefore = leftBefore,
                rightRatingBefore = rightBefore,
                leftRatingAfter = leftAfter,
                rightRatingAfter = rightAfter,
                kFactorUsed = settings.kFactor,
                decayFactorUsed = decayFactorUsed,
            )
        }

        // Items never compared keep initial rating; ensure ratingUpdatedAt is set.
        val finalItems = byId.values.map { item ->
            if (item.comparisonCount == 0) {
                item.copy(
                    rating = settings.initialRating,
                    ratingUpdatedAt = item.createdAt.takeIf { it != Instant.EPOCH } ?: createdAtFallback,
                )
            } else {
                item
            }
        }

        return ItemRecalcResult(items = finalItems, comparisons = rewritten)
    }

    fun recalculateLists(
        lists: List<PreferenceList>,
        comparisons: List<ListComparison>,
        settings: RankingSettings,
        createdAtFallback: Instant,
    ): ListRecalcResult {
        val activeComparisons = comparisons
            .filter { !it.isReverted }
            .sortedBy { it.comparedAt }

        val byId = lists.associate { list ->
            list.id to list.copy(
                rating = settings.initialRating,
                ratingUpdatedAt = list.createdAt,
                comparisonCount = 0,
            )
        }.toMutableMap()

        val rewritten = mutableListOf<ListComparison>()

        for (comparison in activeComparisons) {
            val left = byId[comparison.leftListId] ?: continue
            val right = byId[comparison.rightListId] ?: continue

            val leftDecay = DecayCalculator.decayRating(
                currentRating = left.rating,
                ratingUpdatedAt = left.ratingUpdatedAt,
                now = comparison.comparedAt,
                initialRating = settings.initialRating,
                decayEnabled = settings.decayEnabled,
                decayRatePerDay = settings.decayRatePerDay,
            )
            val rightDecay = DecayCalculator.decayRating(
                currentRating = right.rating,
                ratingUpdatedAt = right.ratingUpdatedAt,
                now = comparison.comparedAt,
                initialRating = settings.initialRating,
                decayEnabled = settings.decayEnabled,
                decayRatePerDay = settings.decayRatePerDay,
            )

            val decayFactorUsed = (leftDecay.decayFactorUsed + rightDecay.decayFactorUsed) / 2.0
            val leftBefore = leftDecay.decayedRating
            val rightBefore = rightDecay.decayedRating

            val update = EloCalculator.updateRatings(
                ratingA = leftBefore,
                ratingB = rightBefore,
                outcome = comparison.outcome,
                kFactor = settings.kFactor,
                ratingScale = settings.ratingScale,
            )

            val leftAfter = update?.newRatingA ?: leftBefore
            val rightAfter = update?.newRatingB ?: rightBefore

            byId[left.id] = left.copy(
                rating = leftAfter,
                ratingUpdatedAt = comparison.comparedAt,
                comparisonCount = left.comparisonCount + 1,
                updatedAt = comparison.comparedAt,
            )
            byId[right.id] = right.copy(
                rating = rightAfter,
                ratingUpdatedAt = comparison.comparedAt,
                comparisonCount = right.comparisonCount + 1,
                updatedAt = comparison.comparedAt,
            )

            rewritten += comparison.copy(
                leftRatingBefore = leftBefore,
                rightRatingBefore = rightBefore,
                leftRatingAfter = leftAfter,
                rightRatingAfter = rightAfter,
                kFactorUsed = settings.kFactor,
                decayFactorUsed = decayFactorUsed,
            )
        }

        val finalLists = byId.values.map { list ->
            if (list.comparisonCount == 0) {
                list.copy(
                    rating = settings.initialRating,
                    ratingUpdatedAt = list.createdAt.takeIf { it != Instant.EPOCH } ?: createdAtFallback,
                )
            } else {
                list
            }
        }

        return ListRecalcResult(lists = finalLists, comparisons = rewritten)
    }

    /**
     * Assigns display ranks with explicit tie handling.
     * Items with ratings within [epsilon] share the same rank (competition ranking:
     * 1,2,2,4). Secondary sort by comparison count descending, then name.
     */
    fun assignRanks(
        entries: List<Triple<UUID, String, Double>>,
        comparisonCounts: Map<UUID, Int>,
        initialRating: Double,
        minimumComparisons: Int,
        epsilon: Double = 1e-6,
        nowDecayedRatings: Map<UUID, Double> = emptyMap(),
    ): List<RankedEntry> {
        val sorted = entries.sortedWith(
            compareByDescending<Triple<UUID, String, Double>> { nowDecayedRatings[it.first] ?: it.third }
                .thenByDescending { comparisonCounts[it.first] ?: 0 }
                .thenBy { it.second.lowercase() },
        )

        val result = mutableListOf<RankedEntry>()
        var displayRank = 1
        var index = 0
        while (index < sorted.size) {
            val rating = nowDecayedRatings[sorted[index].first] ?: sorted[index].third
            var end = index
            while (end + 1 < sorted.size) {
                val nextRating = nowDecayedRatings[sorted[end + 1].first] ?: sorted[end + 1].third
                if (abs(nextRating - rating) <= epsilon) end++ else break
            }
            for (i in index..end) {
                val (id, name, stored) = sorted[i]
                val effective = nowDecayedRatings[id] ?: stored
                val count = comparisonCounts[id] ?: 0
                result += RankedEntry(
                    id = id,
                    name = name,
                    rating = effective,
                    rank = displayRank,
                    comparisonCount = count,
                    isStable = count >= minimumComparisons,
                    ratingDeltaFromInitial = effective - initialRating,
                )
            }
            displayRank += (end - index + 1)
            index = end + 1
        }
        return result
    }
}
