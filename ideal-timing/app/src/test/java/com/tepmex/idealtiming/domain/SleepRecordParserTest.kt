package com.tepmex.idealtiming.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SleepRecordParserTest {
    @Test
    fun parseAggregatedItem_readsWakeFromSegments() {
        val item = JSONObject(
            """
            {
              "time": 1700000000,
              "value": {
                "total_duration": 420,
                "sleep_score": 88,
                "segment_details": [
                  {"bedtime": 1699960000, "wake_up_time": 1699985000, "duration": 420}
                ]
              }
            }
            """.trimIndent(),
        )
        val rec = SleepRecordParser.parseAggregatedItem(item)
        assertNotNull(rec)
        assertEquals(88, rec!!.sleepScore)
        assertEquals(1699985000L, rec.maxWakeUpEpochSec)
    }

    @Test
    fun chooseWake_prefersLatestPastWake() {
        val records = listOf(
            SleepRecord(
                timeEpochSec = 100,
                totalDurationMin = 400,
                sleepScore = 70,
                segments = listOf(SleepSegment(80, 90)),
            ),
            SleepRecord(
                timeEpochSec = 200,
                totalDurationMin = 410,
                sleepScore = 90,
                segments = listOf(SleepSegment(180, 195)),
            ),
            SleepRecord(
                timeEpochSec = 300,
                totalDurationMin = 50,
                sleepScore = 40,
                segments = listOf(SleepSegment(290, 310)), // future relative to now=200
            ),
        )
        val choice = SleepRecordParser.chooseWake(records, nowEpochSec = 200)
        assertNotNull(choice)
        assertEquals(195L, choice!!.wakeEpochSec)
        assertEquals(90, choice.sleepScore)
    }

    @Test
    fun chooseWake_fallsBackToLatestWhenAllFuture() {
        val records = listOf(
            SleepRecord(
                timeEpochSec = 10,
                totalDurationMin = 10,
                sleepScore = 50,
                segments = listOf(SleepSegment(1, 50)),
            ),
        )
        val choice = SleepRecordParser.chooseWake(records, nowEpochSec = 40)
        assertEquals(50L, choice!!.wakeEpochSec)
    }

    @Test
    fun chooseWake_empty() {
        assertNull(SleepRecordParser.chooseWake(emptyList(), 1L))
    }
}
