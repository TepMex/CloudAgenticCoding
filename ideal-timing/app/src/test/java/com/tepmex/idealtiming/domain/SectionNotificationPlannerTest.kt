package com.tepmex.idealtiming.domain

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionNotificationPlannerTest {
    private val utc = ZoneOffset.UTC

    @Test
    fun schedulesAllFutureCuesBeforeEndOfDay() {
        val day = LocalDate.of(2024, 6, 21)
        val wake = ZonedDateTime.of(day.atTime(7, 0), utc).toEpochSecond()
        val now = wake + 60
        val dayEnd = day.plusDays(1).atStartOfDay().toEpochSecond(utc)
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd, utc)

        assertEquals(
            listOf(
                wake + DailyCues.BREAKFAST_AFTER_WAKE_SEC to DailyCues.BREAKFAST_MESSAGE,
                wake + 4 * 3600L to "Наступило время для Тактика и работа руками",
                wake + DailyCues.LUNCH_AFTER_WAKE_SEC to DailyCues.LUNCH_MESSAGE,
                wake + 8 * 3600L to "Наступило время для Тактика и работа руками",
                wake + DailyCues.DINNER_AFTER_WAKE_SEC to DailyCues.DINNER_MESSAGE,
                wake + 12 * 3600L to "Наступило время для Отдых, декомпрессия и подготовка ко сну",
                ZonedDateTime.of(day.atTime(19, 0), utc).toEpochSecond() to DailyCues.DOG_WALK_MESSAGE,
                wake + 16 * 3600L to "Пора спать",
            ),
            plan.map { it.fireEpochSec to it.message },
        )
        assertEquals(
            listOf(
                DailyCues.ALARM_BREAKFAST,
                1,
                DailyCues.ALARM_LUNCH,
                2,
                DailyCues.ALARM_DINNER,
                3,
                DailyCues.ALARM_DOG_WALK,
                4,
            ),
            plan.map { it.alarmId },
        )
        // Wake 07:00 → dinner is 18:00; dog walk is 19:00 wall-clock (same as +12h sector).
        assertEquals(wake + 12 * 3600L, plan.first { it.alarmId == DailyCues.ALARM_DOG_WALK }.fireEpochSec)
        assertEquals(wake + 11 * 3600L, plan.first { it.alarmId == DailyCues.ALARM_DINNER }.fireEpochSec)
    }

    @Test
    fun skipsPastBoundariesIncludingMeals() {
        val day = LocalDate.of(2024, 6, 21)
        val wake = ZonedDateTime.of(day.atTime(7, 0), utc).toEpochSecond()
        val now = wake + 9 * 3600L // 16:00 — breakfast, 4h, lunch, 8h already past
        val dayEnd = day.plusDays(1).atStartOfDay().toEpochSecond(utc)
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd, utc)
        assertEquals(
            listOf(
                DailyCues.DINNER_MESSAGE,
                "Наступило время для Отдых, декомпрессия и подготовка ко сну",
                DailyCues.DOG_WALK_MESSAGE,
                "Пора спать",
            ),
            plan.map { it.message },
        )
        assertEquals(wake + 12 * 3600, plan[1].fireEpochSec)
        assertEquals(wake + 16 * 3600, plan[3].fireEpochSec)
    }

    @Test
    fun doesNotScheduleAfterCurrentDay() {
        val day = LocalDate.of(2024, 6, 21)
        val wake = ZonedDateTime.of(day.atTime(7, 0), utc).toEpochSecond()
        val now = wake + 60
        // Day ends at 17:00 — dinner (18:00), 12h/dog (19:00), sleep (23:00) are out.
        val dayEnd = ZonedDateTime.of(day.atTime(17, 0), utc).toEpochSecond()
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd, utc)
        assertEquals(
            listOf(
                DailyCues.BREAKFAST_MESSAGE,
                "Наступило время для Тактика и работа руками",
                DailyCues.LUNCH_MESSAGE,
                "Наступило время для Тактика и работа руками",
            ),
            plan.map { it.message },
        )
        assertTrue(plan.none { it.message == "Пора спать" })
        assertTrue(plan.none { it.message == DailyCues.DOG_WALK_MESSAGE })
        assertTrue(plan.none { it.message == DailyCues.DINNER_MESSAGE })
    }

    @Test
    fun dogWalkIsNineteenLocalEvenWhenWakeIsLateMorning() {
        val day = LocalDate.of(2024, 6, 21)
        val zone = utc
        val wake = ZonedDateTime.of(day.atTime(10, 0), zone).toEpochSecond()
        val now = wake + 60
        val dayEnd = day.plusDays(1).atStartOfDay().toEpochSecond(zone)
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd, zone)
        val dog = plan.single { it.alarmId == DailyCues.ALARM_DOG_WALK }
        assertEquals(ZonedDateTime.of(day.atTime(19, 0), zone).toEpochSecond(), dog.fireEpochSec)
        assertEquals(DailyCues.DOG_WALK_MESSAGE, dog.message)
        // 19:00 is 9h after a 10:00 wake — not 19h after wake.
        assertEquals(wake + 9 * 3600L, dog.fireEpochSec)
    }

    @Test
    fun skipsDogWalkAfterNineteenHasPassed() {
        val day = LocalDate.of(2024, 6, 21)
        val wake = ZonedDateTime.of(day.atTime(7, 0), utc).toEpochSecond()
        val now = ZonedDateTime.of(day.atTime(19, 1), utc).toEpochSecond()
        val dayEnd = day.plusDays(1).atStartOfDay().toEpochSecond(utc)
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd, utc)
        assertTrue(plan.none { it.alarmId == DailyCues.ALARM_DOG_WALK })
        assertTrue(plan.any { it.message == "Пора спать" })
    }

    @Test
    fun emptyWhenWakeMissingOrAllPast() {
        assertTrue(SectionNotificationPlanner.plan(0, 100, 200, utc).isEmpty())
        val day = LocalDate.of(2024, 6, 21)
        val wake = ZonedDateTime.of(day.atTime(7, 0), utc).toEpochSecond()
        val plan = SectionNotificationPlanner.plan(
            wakeEpochSec = wake,
            nowEpochSec = day.plusDays(1).atStartOfDay().toEpochSecond(utc),
            dayEndExclusiveEpochSec = day.plusDays(1).atStartOfDay().toEpochSecond(utc),
            zoneId = utc,
        )
        assertTrue(plan.isEmpty())
    }
}
