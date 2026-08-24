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
        assertEquals(LocalDate.of(2026, 8, 23), stamp.wakeLocalDate)
        assertEquals(0.5f, stamp.progress, 0.0001f)
        assertEquals(now, stamp.checkedInEpochSec)
        assertEquals(180f, IdealClock.pointerDegrees(stamp.progress), 0.001f)
    }

    @Test
    fun firstCheckInForThisWakeDayIsFixedAgainstLaterTaps() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val firstAt = wake + 2 * 3600L
        val first = NfcCheckInStamp.apply(null, wake, firstAt, moscowZone)
        val later = NfcCheckInStamp.apply(first, wake, firstAt + 3 * 3600L, moscowZone)
        assertSame(first, later)
        assertEquals(2f / 16f, later.progress, 0.0001f)
    }

    @Test
    fun resyncOfSameWakeDayKeepsStampEvenIfWakeTimeShifts() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val first = NfcCheckInStamp.apply(null, wake, wake + 2 * 3600L, moscowZone)
        val refinedWake = ZonedDateTime.of(2026, 8, 23, 7, 12, 0, 0, moscowZone).toEpochSecond()
        val afterResync = NfcCheckInStamp.apply(first, refinedWake, refinedWake + 3 * 3600L, moscowZone)
        assertSame(first, afterResync)
        assertEquals(2f / 16f, NfcCheckInStamp.progressForWake(first, refinedWake, moscowZone)!!)
    }

    @Test
    fun newWakeDayCreatesAFreshStamp() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val first = NfcCheckInStamp.apply(null, wake, wake + 3600L, moscowZone)
        val nextWake = ZonedDateTime.of(2026, 8, 24, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val nextMorning = ZonedDateTime.of(2026, 8, 24, 8, 0, 0, 0, moscowZone).toEpochSecond()
        val second = NfcCheckInStamp.apply(first, nextWake, nextMorning, moscowZone)
        assertNotEquals(first.wakeLocalDate, second.wakeLocalDate)
        assertEquals(LocalDate.of(2026, 8, 24), second.wakeLocalDate)
        assertEquals(1f / 16f, second.progress, 0.0001f)
    }

    @Test
    fun stampStaysVisibleAfterMidnightIfWakeUnchanged() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val stamp = NfcCheckIn(
            wakeLocalDate = LocalDate.of(2026, 8, 23),
            progress = 0.25f,
            checkedInEpochSec = wake + 4 * 3600L,
        )
        val afterMidnight = ZonedDateTime.of(2026, 8, 24, 0, 30, 0, 0, moscowZone)
        assertEquals(LocalDate.of(2026, 8, 24), afterMidnight.toLocalDate())
        assertEquals(0.25f, NfcCheckInStamp.progressForWake(stamp, wake, moscowZone)!!)
    }

    @Test
    fun newWakeDayHidesPreviousStamp() {
        val stamp = NfcCheckIn(
            wakeLocalDate = LocalDate.of(2026, 8, 23),
            progress = 0.25f,
            checkedInEpochSec = 1L,
        )
        val nextWake = ZonedDateTime.of(2026, 8, 24, 7, 0, 0, 0, moscowZone).toEpochSecond()
        assertNull(NfcCheckInStamp.progressForWake(stamp, nextWake, moscowZone))
        assertNull(NfcCheckInStamp.progressForWake(null, nextWake, moscowZone))
    }

    @Test
    fun stampKeepsItsProgressEvenAfterPointerMoves() {
        val wake = ZonedDateTime.of(2026, 8, 23, 7, 0, 0, 0, moscowZone).toEpochSecond()
        val stamp = NfcCheckIn(
            wakeLocalDate = LocalDate.of(2026, 8, 23),
            progress = 0.125f,
            checkedInEpochSec = 1L,
        )
        assertEquals(0.125f, NfcCheckInStamp.progressForWake(stamp, wake, moscowZone)!!)
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
    fun wakeLocalDateFollowsZoneNotUtc() {
        // 23 Aug 23:30 in Los Angeles is still 23 Aug locally; UTC is already 24 Aug.
        val pacific = ZoneId.of("America/Los_Angeles")
        val wake = ZonedDateTime.of(2026, 8, 23, 23, 30, 0, 0, pacific).toEpochSecond()
        assertEquals(LocalDate.of(2026, 8, 23), NfcCheckInStamp.wakeLocalDate(wake, pacific))
        assertEquals(LocalDate.of(2026, 8, 24), NfcCheckInStamp.wakeLocalDate(wake, ZoneId.of("UTC")))
    }

    @Test
    fun jsonRoundTripPreservesStamp() {
        val original = NfcCheckIn(
            wakeLocalDate = LocalDate.of(2026, 8, 23),
            progress = 0.3125f,
            checkedInEpochSec = 1_777_000_000L,
        )
        val restored = NfcCheckIn.fromJson(original.toJson())!!
        assertEquals(original.wakeLocalDate, restored.wakeLocalDate)
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
