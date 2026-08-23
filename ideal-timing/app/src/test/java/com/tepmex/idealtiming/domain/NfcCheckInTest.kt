package com.tepmex.idealtiming.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NfcCheckInTest {
    private val moscowZone = ZoneId.of("Europe/Moscow")

    @Test
    fun stampsPointerProgressAtScanInstant() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val now = wake + 8 * 3600L
        val stamp = NfcCheckInStamp.apply(
            existing = null,
            wakeEpochSec = wake,
            nowEpochSec = now,
            zoneId = moscowZone,
        )
        assertEquals(LocalDate.of(2026, 8, 23), stamp.localDate)
        assertEquals(0.5f, stamp.progress, 0.0001f)
        assertEquals(now, stamp.checkedInEpochSec)
        assertEquals(180f, IdealClock.pointerDegrees(stamp.progress), 0.001f)
    }

    @Test
    fun firstCheckInOfTheDayIsFixedAgainstLaterTaps() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val firstAt = wake + 2 * 3600L
        val first = NfcCheckInStamp.apply(null, wake, firstAt, moscowZone)
        val later = NfcCheckInStamp.apply(first, wake, firstAt + 3 * 3600L, moscowZone)
        assertSame(first, later)
        assertEquals(2f / 16f, later.progress, 0.0001f)
    }

    @Test
    fun nextLocalDateCreatesAFreshStamp() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val first = NfcCheckInStamp.apply(null, wake, wake + 3600L, moscowZone)
        val nextWake = ZonedDateTime.of(2026, 8, 24, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val nextMorning = ZonedDateTime.of(2026, 8, 24, 8, 0, 0, 0, moscowZone).toEpochSecond()
        val second = NfcCheckInStamp.apply(first, nextWake, nextMorning, moscowZone)
        assertNotEquals(first.localDate, second.localDate)
        assertEquals(LocalDate.of(2026, 8, 24), second.localDate)
        assertEquals(1f / 16f, second.progress, 0.0001f)
    }

    @Test
    fun yesterdayStampIsNotDrawnToday() {
        val yesterday = NfcCheckIn(
            localDate = LocalDate.of(2026, 8, 22),
            progress = 0.25f,
            checkedInEpochSec = 1L,
        )
        val now = ZonedDateTime.of(2026, 8, 23, 12, 0, 0, 0, moscowZone).toEpochSecond()
        assertNull(NfcCheckInStamp.progressForToday(yesterday, now, moscowZone))
        assertNull(NfcCheckInStamp.progressForToday(null, now, moscowZone))
    }

    @Test
    fun todayStampKeepsItsProgressEvenAfterPointerMoves() {
        val stamp = NfcCheckIn(
            localDate = LocalDate.of(2026, 8, 23),
            progress = 0.125f,
            checkedInEpochSec = 1L,
        )
        val later = ZonedDateTime.of(2026, 8, 23, 22, 0, 0, 0, moscowZone).toEpochSecond()
        assertEquals(0.125f, NfcCheckInStamp.progressForToday(stamp, later, moscowZone)!!)
    }

    @Test
    fun freezeAtSixteenHoursStampsTheRim() {
        val wake = ZonedDateTime.of(2026, 8, 23, 6, 0, 0, 0, moscowZone).toEpochSecond()
        val afterDay = wake + IdealClock.DAY_SECONDS + 1800L
        val stamp = NfcCheckInStamp.apply(null, wake, afterDay, moscowZone)
        assertEquals(1f, stamp.progress, 0f)
        assertEquals(360f, IdealClock.pointerDegrees(stamp.progress), 0.001f)
    }

    @Test
    fun localDateFollowsZoneNotUtc() {
        // 23 Aug 23:30 in Los Angeles is still 23 Aug locally; UTC is already 24 Aug.
        val pacific = ZoneId.of("America/Los_Angeles")
        val now = ZonedDateTime.of(2026, 8, 23, 23, 30, 0, 0, pacific).toEpochSecond()
        assertEquals(LocalDate.of(2026, 8, 23), NfcCheckInStamp.localDate(now, pacific))
        assertEquals(LocalDate.of(2026, 8, 24), NfcCheckInStamp.localDate(now, ZoneId.of("UTC")))
    }

    @Test
    fun jsonRoundTripPreservesStamp() {
        val original = NfcCheckIn(
            localDate = LocalDate.of(2026, 8, 23),
            progress = 0.3125f,
            checkedInEpochSec = 1_777_000_000L,
        )
        val restored = NfcCheckIn.fromJson(original.toJson())!!
        assertEquals(original.localDate, restored.localDate)
        assertEquals(original.progress, restored.progress, 0.00001f)
        assertEquals(original.checkedInEpochSec, restored.checkedInEpochSec)
    }

    @Test
    fun jsonRejectsBlankAndCorruptPayloads() {
        assertNull(NfcCheckIn.fromJson(null))
        assertNull(NfcCheckIn.fromJson(""))
        assertNull(NfcCheckIn.fromJson("{not json"))
        assertNull(NfcCheckIn.fromJson("""{"local_date":"2026-08-23","progress":0.5}"""))
    }
}
