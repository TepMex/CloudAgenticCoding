package com.tepmex.idealtiming.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionNotificationPlannerTest {
    @Test
    fun schedulesAllFutureBoundariesBeforeEndOfDay() {
        val wake = 1_700_000_000L // arbitrary epoch
        val now = wake + 60
        val dayEnd = wake + 20 * 3600L
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd)
        assertEquals(4, plan.size)
        assertEquals(wake + 4 * 3600, plan[0].fireEpochSec)
        assertEquals("Наступило время для Тактика и работа руками", plan[0].message)
        assertEquals(wake + 8 * 3600, plan[1].fireEpochSec)
        assertEquals("Наступило время для Тактика и работа руками", plan[1].message)
        assertEquals(wake + 12 * 3600, plan[2].fireEpochSec)
        assertEquals(
            "Наступило время для Отдых, декомпрессия и подготовка ко сну",
            plan[2].message,
        )
        assertEquals(wake + 16 * 3600, plan[3].fireEpochSec)
        assertEquals("Пора спать", plan[3].message)
        assertEquals(listOf(1, 2, 3, 4), plan.map { it.alarmId })
    }

    @Test
    fun skipsPastBoundaries() {
        val wake = 1_700_000_000L
        val now = wake + 9 * 3600L
        val dayEnd = wake + 24 * 3600L
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd)
        assertEquals(2, plan.size)
        assertEquals(wake + 12 * 3600, plan[0].fireEpochSec)
        assertEquals(wake + 16 * 3600, plan[1].fireEpochSec)
        assertEquals("Пора спать", plan[1].message)
    }

    @Test
    fun doesNotScheduleAfterCurrentDay() {
        val wake = 1_700_000_000L
        val now = wake + 60
        // Day ends before the 12h and 16h marks
        val dayEnd = wake + 10 * 3600L
        val plan = SectionNotificationPlanner.plan(wake, now, dayEnd)
        assertEquals(2, plan.size)
        assertEquals(wake + 4 * 3600, plan[0].fireEpochSec)
        assertEquals(wake + 8 * 3600, plan[1].fireEpochSec)
        assertTrue(plan.none { it.message == "Пора спать" })
    }

    @Test
    fun emptyWhenWakeMissingOrAllPast() {
        assertTrue(SectionNotificationPlanner.plan(0, 100, 200).isEmpty())
        val wake = 1_000L
        val plan = SectionNotificationPlanner.plan(
            wakeEpochSec = wake,
            nowEpochSec = wake + IdealClock.DAY_SECONDS + 1,
            dayEndExclusiveEpochSec = wake + IdealClock.DAY_SECONDS + 100,
        )
        assertTrue(plan.isEmpty())
    }
}
