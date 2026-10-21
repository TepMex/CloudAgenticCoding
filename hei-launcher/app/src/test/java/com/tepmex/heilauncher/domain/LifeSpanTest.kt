package com.tepmex.heilauncher.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class LifeSpanTest {
    @Test
    fun spanIsOnePixelPerDayThrough2083() {
        assertEquals(32873, LifeSpan.dayCount)
        assertEquals(25567, LifeSpan.nearEndIndex)
        assertEquals(LocalDate.of(1993, 1, 21), LifeSpan.birth)
        assertEquals(LocalDate.of(2063, 1, 21), LifeSpan.nearEnd)
        assertEquals(LocalDate.of(2083, 1, 21), LifeSpan.farEnd)
    }

    @Test
    fun october2026FillsThroughTodayAndSplitsTheFuture() {
        val today = LocalDate.of(2026, 10, 21)
        assertEquals(12326, LifeSpan.todayIndex(today))
        val counts = IntArray(3)
        for (index in 0 until LifeSpan.dayCount) {
            counts[LifeSpan.pixel(index, today).ordinal]++
        }
        assertEquals(12327, counts[LifeSpan.Pixel.Filled.ordinal])
        assertEquals(13241, counts[LifeSpan.Pixel.Near.ordinal])
        assertEquals(7305, counts[LifeSpan.Pixel.Far.ordinal])
        assertEquals(LifeSpan.Pixel.Filled, LifeSpan.pixel(0, LifeSpan.birth))
        assertEquals(LifeSpan.Pixel.Near, LifeSpan.pixel(0, LifeSpan.birth.minusDays(1)))
    }

    @Test
    fun hoursUntil2063AreTruncatedCivilHours() {
        assertEquals(317770L, LifeSpan.hoursUntilNearEnd(LocalDateTime.of(2026, 10, 21, 13, 2)))
        assertEquals(2L, LifeSpan.hoursUntilNearEnd(LocalDateTime.of(2063, 1, 20, 22, 0)))
        assertEquals(0L, LifeSpan.hoursUntilNearEnd(LocalDateTime.of(2063, 1, 20, 23, 30)))
        assertEquals(0L, LifeSpan.hoursUntilNearEnd(LocalDateTime.of(2063, 1, 21, 0, 0)))
        assertEquals(0L, LifeSpan.hoursUntilNearEnd(LocalDateTime.of(2083, 1, 21, 12, 0)))
    }

    @Test
    fun gridFollowsWidthSoTheHourCountCanSitBesideIt() {
        assertEquals(100, LifeSpan.grid(100).columns)
        assertEquals(329, LifeSpan.grid(100).rows)
        assertEquals(LifeSpan.dayCount, LifeSpan.grid(1_000_000).columns)
        assertEquals(1, LifeSpan.grid(1_000_000).rows)
        assertEquals(1, LifeSpan.grid(0).columns)
        assertEquals(LifeSpan.dayCount, LifeSpan.grid(0).rows)
    }
}
