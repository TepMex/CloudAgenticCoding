package com.tepmex.paircompelo.domain.elo

import com.google.common.truth.Truth.assertThat
import com.tepmex.paircompelo.core.FakeAppClock
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class DecayCalculatorTest {

    private val initial = 1000.0
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun disabled_returnsCurrentRating() {
        val result = DecayCalculator.decayRating(
            currentRating = 1200.0,
            ratingUpdatedAt = start,
            now = start.plusSeconds(86_400 * 30),
            initialRating = initial,
            decayEnabled = false,
            decayRatePerDay = 0.995,
        )
        assertThat(result.decayedRating).isWithin(1e-9).of(1200.0)
        assertThat(result.decayFactorUsed).isWithin(1e-9).of(1.0)
    }

    @Test
    fun missingUpdatedAt_treatsAsInitial() {
        val result = DecayCalculator.decayRating(
            currentRating = 1200.0,
            ratingUpdatedAt = null,
            now = start,
            initialRating = initial,
            decayEnabled = true,
            decayRatePerDay = 0.995,
        )
        assertThat(result.decayedRating).isWithin(1e-9).of(initial)
    }

    @Test
    fun fractionalDay_decaysPartially() {
        val halfDay = start.plusMillis(43_200_000)
        val result = DecayCalculator.decayRating(
            currentRating = 1200.0,
            ratingUpdatedAt = start,
            now = halfDay,
            initialRating = initial,
            decayEnabled = true,
            decayRatePerDay = 0.995,
        )
        assertThat(result.elapsedDays).isWithin(1e-6).of(0.5)
        assertThat(result.decayedRating).isLessThan(1200.0)
        assertThat(result.decayedRating).isGreaterThan(initial)
    }

    @Test
    fun longPeriod_approachesInitial() {
        val far = start.plusSeconds(86_400L * 3650)
        val result = DecayCalculator.decayRating(
            currentRating = 1400.0,
            ratingUpdatedAt = start,
            now = far,
            initialRating = initial,
            decayEnabled = true,
            decayRatePerDay = 0.995,
        )
        assertThat(result.decayedRating).isWithin(5.0).of(initial)
    }

    @Test
    fun futureTimestamp_treatedAsZeroElapsed() {
        val clock = FakeAppClock(start)
        val result = DecayCalculator.decayRating(
            currentRating = 1200.0,
            ratingUpdatedAt = start.plusSeconds(86_400),
            now = clock.now(),
            initialRating = initial,
            decayEnabled = true,
            decayRatePerDay = 0.995,
        )
        assertThat(result.elapsedDays).isWithin(1e-9).of(0.0)
        assertThat(result.decayedRating).isWithin(1e-9).of(1200.0)
    }

    @Test
    fun invalidDecayRate_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            DecayCalculator.decayRating(1000.0, start, start, 1000.0, true, 1.1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DecayCalculator.decayRating(1000.0, start, start, 1000.0, true, 0.0)
        }
    }

    @Test
    fun decayTowardInitial_fromBelow() {
        val result = DecayCalculator.decayRating(
            currentRating = 800.0,
            ratingUpdatedAt = start,
            now = start.plusSeconds(86_400 * 10),
            initialRating = initial,
            decayEnabled = true,
            decayRatePerDay = 0.995,
        )
        assertThat(result.decayedRating).isGreaterThan(800.0)
        assertThat(result.decayedRating).isLessThan(initial)
    }
}
