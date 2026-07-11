package com.tepmex.paircompelo.domain.elo

import com.google.common.truth.Truth.assertThat
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.abs

class EloCalculatorTest {

    @Test
    fun expectedScores_identicalRatings_areHalf() {
        val result = EloCalculator.expectedScores(1000.0, 1000.0, 400.0)
        assertThat(result.expectedA).isWithin(1e-9).of(0.5)
        assertThat(result.expectedB).isWithin(1e-9).of(0.5)
    }

    @Test
    fun expectedScores_largeDifference_favorsHigher() {
        val result = EloCalculator.expectedScores(1400.0, 1000.0, 400.0)
        assertThat(result.expectedA).isGreaterThan(0.9)
        assertThat(result.expectedB).isLessThan(0.1)
        assertThat(result.expectedA + result.expectedB).isWithin(1e-9).of(1.0)
    }

    @Test
    fun update_winLoss_movesRatingsSymmetrically() {
        val update = EloCalculator.updateRatings(
            ratingA = 1000.0,
            ratingB = 1000.0,
            outcome = ComparisonOutcome.LEFT_WINS,
            kFactor = 32.0,
            ratingScale = 400.0,
        )!!
        assertThat(update.newRatingA - 1000.0).isWithin(1e-9).of(1000.0 - update.newRatingB)
        assertThat(update.newRatingA).isGreaterThan(1000.0)
        assertThat(update.newRatingB).isLessThan(1000.0)
    }

    @Test
    fun update_draw_keepsEqualRatingsEqual() {
        val update = EloCalculator.updateRatings(
            ratingA = 1000.0,
            ratingB = 1000.0,
            outcome = ComparisonOutcome.DRAW,
            kFactor = 32.0,
            ratingScale = 400.0,
        )!!
        assertThat(update.newRatingA).isWithin(1e-9).of(1000.0)
        assertThat(update.newRatingB).isWithin(1e-9).of(1000.0)
    }

    @Test
    fun update_skip_returnsNull() {
        val update = EloCalculator.updateRatings(
            ratingA = 1000.0,
            ratingB = 1100.0,
            outcome = ComparisonOutcome.SKIPPED,
            kFactor = 32.0,
            ratingScale = 400.0,
        )
        assertThat(update).isNull()
    }

    @Test
    fun invalidScale_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            EloCalculator.expectedScores(1000.0, 1000.0, 0.0)
        }
    }

    @Test
    fun invalidKFactor_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            EloCalculator.updateRatings(1000.0, 1000.0, ComparisonOutcome.LEFT_WINS, 0.0, 400.0)
        }
    }

    @Test
    fun underdogWin_movesMoreThanFavoriteWin() {
        val underdogWins = EloCalculator.updateRatings(
            1000.0, 1200.0, ComparisonOutcome.LEFT_WINS, 32.0, 400.0,
        )!!
        val favoriteWins = EloCalculator.updateRatings(
            1200.0, 1000.0, ComparisonOutcome.LEFT_WINS, 32.0, 400.0,
        )!!
        assertThat(abs(underdogWins.newRatingA - 1000.0))
            .isGreaterThan(abs(favoriteWins.newRatingA - 1200.0))
    }
}
