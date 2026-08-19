package com.tepmex.idealtiming.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SunCalculatorTest {
    private val moscow = GeoPoint(55.7558, 37.6173)
    private val moscowZone = ZoneId.of("Europe/Moscow")

    @Test
    fun officialAltitudeMatchesNegZeroPoint833() {
        assertEquals(-0.833, SunCalculator.OFFICIAL_ALTITUDE_DEG, 0.0)
        assertEquals(90.833, SunCalculator.OFFICIAL_ZENITH_DEG, 0.0)
        assertEquals(
            90.0 - SunCalculator.OFFICIAL_ALTITUDE_DEG,
            SunCalculator.OFFICIAL_ZENITH_DEG,
            1e-9,
        )
    }

    @Test
    fun moscowSummerSolsticeMatchesTimeAndDateWithinAMinute() {
        // timeanddate.com / NOAA-class: 2024-06-21 Moscow sunrise ≈ 03:44, sunset ≈ 21:18
        val events = SunCalculator.eventsForDate(
            date = LocalDate.of(2024, 6, 21),
            location = moscow,
            zoneId = moscowZone,
        )
        assertNotNull(events.sunrise)
        assertNotNull(events.sunset)
        val rise = events.sunrise!!.atZone(moscowZone)
        val set = events.sunset!!.atZone(moscowZone)
        assertEquals(3, rise.hour)
        assertEquals(44, rise.minute)
        assertEquals(21, set.hour)
        assertEquals(18, set.minute)
    }

    @Test
    fun usesZoneIdForLocalWallClock() {
        val events = SunCalculator.eventsForDate(
            date = LocalDate.of(2024, 6, 21),
            location = moscow,
            zoneId = moscowZone,
        )
        val riseUtc = events.sunrise!!.atZone(ZoneId.of("UTC"))
        // Moscow is UTC+3 in June → 03:44 MSK = 00:44 UTC
        assertEquals(0, riseUtc.hour)
        assertEquals(44, riseUtc.minute)
    }

    @Test
    fun dialMarkersOmitSunriseBeforeWake() {
        // Wake 07:00 MSK — sunrise ~03:44 is outside the dial → no sun icon
        val wake = ZonedDateTime.of(2024, 6, 21, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = SunCalculator.dialMarkers(wake, moscow, moscowZone)
        assertNull(markers.sunriseProgress)
        assertNull(markers.sunriseEpochSec)
        assertNotNull(markers.sunsetProgress)
    }

    @Test
    fun dialMarkersOmitSunsetAfterSixteenHours() {
        // Wake 03:00 MSK — dial ends 19:00; sunset ~21:18 is past wake+16h → no moon icon
        val wake = ZonedDateTime.of(2024, 6, 21, 3, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = SunCalculator.dialMarkers(wake, moscow, moscowZone)
        assertNotNull(markers.sunriseProgress)
        assertNull(markers.sunsetProgress)
        assertNull(markers.sunsetEpochSec)
    }

    @Test
    fun dialMarkersMapSunsetOntoSixteenHourDay() {
        // Wake 07:00 MSK → sunset ~21:18 is ~14h18m → progress ≈ 14.3/16
        val wake = ZonedDateTime.of(2024, 6, 21, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val markers = SunCalculator.dialMarkers(wake, moscow, moscowZone)
        assertNotNull(markers.sunsetProgress)
        assertNull(markers.sunriseProgress) // 03:44 is before wake
        val expected = (14.0 + 18.0 / 60.0) / 16.0
        assertEquals(expected.toFloat(), markers.sunsetProgress!!, 0.01f)
        assertEquals(
            IdealClock.pointerDegrees(markers.sunsetProgress!!),
            IdealClock.pointerDegrees(expected.toFloat()),
            4f,
        )
    }

    @Test
    fun dialMarkersIncludeSunriseWhenAfterWake() {
        // Equinox-ish: daylight ≈12h fits inside a 16h dial from an early wake.
        val date = LocalDate.of(2024, 3, 20)
        val wake = ZonedDateTime.of(2024, 3, 20, 5, 0, 0, 0, moscowZone).toEpochSecond()
        val events = SunCalculator.eventsForDate(date, moscow, moscowZone)
        assertNotNull(events.sunrise)
        assertTrue(events.sunrise!!.isAfter(Instant.ofEpochSecond(wake)))
        val markers = SunCalculator.dialMarkers(wake, moscow, moscowZone)
        assertNotNull(markers.sunriseProgress)
        assertNotNull(markers.sunsetProgress)
        assertTrue(markers.sunriseProgress!! < markers.sunsetProgress!!)
        assertTrue(markers.sunriseProgress!! in 0f..1f)
    }

    @Test
    fun polarWinterHasNoEvents() {
        // Alert, Nunavut — polar night around Dec solstice
        val alert = GeoPoint(82.5, -62.3)
        val events = SunCalculator.eventsForDate(
            date = LocalDate.of(2024, 12, 21),
            location = alert,
            zoneId = ZoneId.of("America/Toronto"),
        )
        assertNull(events.sunrise)
        assertNull(events.sunset)
    }

    @Test
    fun progressOutsideDialIsNull() {
        assertNull(SunCalculator.progressOnDial(1_000L, 999L))
        assertNull(SunCalculator.progressOnDial(1_000L, 1_000L + IdealClock.DAY_SECONDS + 1))
        assertEquals(0f, SunCalculator.progressOnDial(1_000L, 1_000L)!!, 0f)
        assertEquals(1f, SunCalculator.progressOnDial(1_000L, 1_000L + IdealClock.DAY_SECONDS)!!, 0f)
    }
}
