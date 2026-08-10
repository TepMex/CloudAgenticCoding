package com.tepmex.idealtiming.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdealClockTest {
    @Test
    fun sectorBoundaries() {
        val wake = 1_000_000L
        assertEquals(DaySector.HealthStrategy, IdealClock.reading(wake, wake).sector)
        assertEquals(DaySector.HealthStrategy, IdealClock.reading(wake, wake + 3 * 3600 + 3599).sector)
        assertEquals(DaySector.TacticsHandworkMorning, IdealClock.reading(wake, wake + 4 * 3600).sector)
        assertEquals(DaySector.TacticsHandworkAfternoon, IdealClock.reading(wake, wake + 8 * 3600).sector)
        assertEquals(DaySector.RestWindDown, IdealClock.reading(wake, wake + 12 * 3600).sector)
    }

    @Test
    fun clampsBeforeWakeToZero() {
        val wake = 1_000_000L
        val r = IdealClock.reading(wake, wake - 60)
        assertEquals(0L, r.elapsedSec)
        assertEquals(0f, r.progress, 0.0001f)
        assertFalse(r.frozenAtSixteenHours)
    }

    @Test
    fun freezesAtSixteenHours() {
        val wake = 1_000_000L
        val atCap = IdealClock.reading(wake, wake + IdealClock.DAY_SECONDS)
        assertEquals(IdealClock.DAY_SECONDS, atCap.elapsedSec)
        assertEquals(1f, atCap.progress, 0.0001f)
        assertTrue(atCap.frozenAtSixteenHours)
        assertEquals(DaySector.RestWindDown, atCap.sector)

        val overflow = IdealClock.reading(wake, wake + IdealClock.DAY_SECONDS + 10_000)
        assertEquals(IdealClock.DAY_SECONDS, overflow.elapsedSec)
        assertEquals(1f, overflow.progress, 0.0001f)
        assertTrue(overflow.frozenAtSixteenHours)
        assertEquals(360f, IdealClock.pointerDegrees(overflow.progress), 0.001f)
    }

    @Test
    fun midDayProgress() {
        val wake = 0L
        val r = IdealClock.reading(wake, 8 * 3600L)
        assertEquals(0.5f, r.progress, 0.0001f)
        assertEquals(180f, IdealClock.pointerDegrees(r.progress), 0.001f)
    }
}
