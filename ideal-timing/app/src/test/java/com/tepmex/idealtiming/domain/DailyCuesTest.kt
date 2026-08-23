package com.tepmex.idealtiming.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DailyCuesTest {
    private val moscowZone = ZoneId.of("Europe/Moscow")

    @Test
    fun mealProgressIsFixedOnSixteenHourDial() {
        val wake = ZonedDateTime.of(2024, 6, 21, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = DailyCues.markers(wake, moscowZone)
        assertEquals(0.5f / 16f, markers.breakfastProgress, 0.0001f)
        assertEquals(6f / 16f, markers.lunchProgress, 0.0001f)
        assertEquals(11f / 16f, markers.dinnerProgress, 0.0001f)
        assertEquals(0.5f / 16f, DailyCues.breakfastProgress(), 0f)
        assertEquals(6f / 16f, DailyCues.lunchProgress(), 0f)
        assertEquals(11f / 16f, DailyCues.dinnerProgress(), 0f)
    }

    @Test
    fun dogWalkUsesNineteenLocalWallClockNotHoursAfterWake() {
        // Wake 07:00 MSK → 19:00 MSK is 12 hours later (not 19).
        val wake = ZonedDateTime.of(2024, 6, 21, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = DailyCues.markers(wake, moscowZone)
        assertNotNull(markers.dogWalkProgress)
        assertEquals(12f / 16f, markers.dogWalkProgress!!, 0.0001f)
        val expectedEpoch = ZonedDateTime.of(2024, 6, 21, 19, 0, 0, 0, moscowZone).toEpochSecond()
        assertEquals(expectedEpoch, markers.dogWalkEpochSec)
        assertEquals(
            IdealClock.pointerDegrees(12f / 16f),
            IdealClock.pointerDegrees(markers.dogWalkProgress!!),
            0.01f,
        )
    }

    @Test
    fun dogWalkOmittedWhenNineteenIsAfterSixteenHours() {
        // Wake 02:00 → dial ends 18:00; 19:00 is off the dial.
        val wake = ZonedDateTime.of(2024, 6, 21, 2, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = DailyCues.markers(wake, moscowZone)
        assertNull(markers.dogWalkProgress)
        assertNull(markers.dogWalkEpochSec)
    }

    @Test
    fun dogWalkOmittedWhenNineteenIsBeforeWake() {
        // Wake 20:00 — 19:00 that day is before wake; next 19:00 is 23h later.
        val wake = ZonedDateTime.of(2024, 6, 21, 20, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = DailyCues.markers(wake, moscowZone)
        assertNull(markers.dogWalkProgress)
        assertNull(markers.dogWalkEpochSec)
    }

    @Test
    fun dogWalkIncludedWhenNineteenIsExactlySixteenHours() {
        val wake = ZonedDateTime.of(2024, 6, 21, 3, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = DailyCues.markers(wake, moscowZone)
        assertEquals(1f, markers.dogWalkProgress!!, 0f)
        assertEquals(
            ZonedDateTime.of(2024, 6, 21, 19, 0, 0, 0, moscowZone).toEpochSecond(),
            markers.dogWalkEpochSec,
        )
    }

    @Test
    fun mealsStayOnDialRegardlessOfWallClock() {
        // Late wake: dinner (wake+11h) is 07:00 next morning — still on the 16h dial.
        val wake = ZonedDateTime.of(2024, 6, 21, 20, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = DailyCues.markers(wake, moscowZone)
        assertEquals(11f / 16f, markers.dinnerProgress, 0.0001f)
        val dinnerEpoch = wake + DailyCues.DINNER_AFTER_WAKE_SEC
        assertEquals(
            LocalDate.of(2024, 6, 22),
            java.time.Instant.ofEpochSecond(dinnerEpoch).atZone(moscowZone).toLocalDate(),
        )
    }
}
