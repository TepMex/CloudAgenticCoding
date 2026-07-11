package com.tepmex.paircompelo.domain.ranking

import com.google.common.truth.Truth.assertThat
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.RankingSettings
import org.junit.Test
import java.time.Instant
import java.util.UUID

class RankingRecalculatorTest {

    private val listId = UUID.randomUUID()
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")

    private fun item(name: String, id: UUID = UUID.randomUUID()) = PreferenceItem(
        id = id,
        listId = listId,
        name = name,
        createdAt = t0,
        updatedAt = t0,
        rating = 1500.0,
        ratingUpdatedAt = t0,
        comparisonCount = 99,
        winCount = 9,
        lossCount = 9,
    )

    @Test
    fun chronologicalReplay_rebuildsFromHistory() {
        val a = item("A")
        val b = item("B")
        val settings = RankingSettings.Defaults.copy(decayEnabled = false)
        val comparisons = listOf(
            ItemComparison(
                id = UUID.randomUUID(),
                listId = listId,
                leftItemId = a.id,
                rightItemId = b.id,
                winnerItemId = a.id,
                outcome = ComparisonOutcome.LEFT_WINS,
                comparedAt = t0.plusSeconds(10),
                leftRatingBefore = 0.0,
                rightRatingBefore = 0.0,
                leftRatingAfter = 0.0,
                rightRatingAfter = 0.0,
                kFactorUsed = 0.0,
                decayFactorUsed = 0.0,
            ),
            ItemComparison(
                id = UUID.randomUUID(),
                listId = listId,
                leftItemId = a.id,
                rightItemId = b.id,
                winnerItemId = b.id,
                outcome = ComparisonOutcome.RIGHT_WINS,
                comparedAt = t0.plusSeconds(20),
                leftRatingBefore = 0.0,
                rightRatingBefore = 0.0,
                leftRatingAfter = 0.0,
                rightRatingAfter = 0.0,
                kFactorUsed = 0.0,
                decayFactorUsed = 0.0,
            ),
        )
        val result = RankingRecalculator.recalculateItems(
            items = listOf(a, b),
            comparisons = comparisons,
            settings = settings,
            createdAtFallback = t0,
        )
        val aFinal = result.items.first { it.id == a.id }
        val bFinal = result.items.first { it.id == b.id }
        assertThat(aFinal.comparisonCount).isEqualTo(2)
        assertThat(bFinal.comparisonCount).isEqualTo(2)
        assertThat(aFinal.winCount).isEqualTo(1)
        assertThat(aFinal.lossCount).isEqualTo(1)
        assertThat(result.comparisons).hasSize(2)
        assertThat(result.comparisons[0].leftRatingBefore).isWithin(1e-6).of(settings.initialRating)
    }

    @Test
    fun revertedComparisons_areIgnored() {
        val a = item("A")
        val b = item("B")
        val comparisons = listOf(
            ItemComparison(
                id = UUID.randomUUID(),
                listId = listId,
                leftItemId = a.id,
                rightItemId = b.id,
                winnerItemId = a.id,
                outcome = ComparisonOutcome.LEFT_WINS,
                comparedAt = t0.plusSeconds(10),
                leftRatingBefore = 0.0,
                rightRatingBefore = 0.0,
                leftRatingAfter = 0.0,
                rightRatingAfter = 0.0,
                kFactorUsed = 0.0,
                decayFactorUsed = 0.0,
                isReverted = true,
            ),
        )
        val result = RankingRecalculator.recalculateItems(
            items = listOf(a, b),
            comparisons = comparisons,
            settings = RankingSettings.Defaults,
            createdAtFallback = t0,
        )
        assertThat(result.items.first { it.id == a.id }.rating)
            .isWithin(1e-9).of(RankingSettings.DEFAULT_INITIAL_RATING)
        assertThat(result.items.first { it.id == a.id }.comparisonCount).isEqualTo(0)
    }

    @Test
    fun assignRanks_handlesTies() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val id3 = UUID.randomUUID()
        val ranks = RankingRecalculator.assignRanks(
            entries = listOf(
                Triple(id1, "A", 1100.0),
                Triple(id2, "B", 1100.0),
                Triple(id3, "C", 1000.0),
            ),
            comparisonCounts = mapOf(id1 to 5, id2 to 3, id3 to 2),
            initialRating = 1000.0,
            minimumComparisons = 5,
        )
        assertThat(ranks[0].rank).isEqualTo(1)
        assertThat(ranks[1].rank).isEqualTo(1)
        assertThat(ranks[2].rank).isEqualTo(3)
        assertThat(ranks[0].isStable).isTrue()
        assertThat(ranks[1].isStable).isFalse()
    }
}
