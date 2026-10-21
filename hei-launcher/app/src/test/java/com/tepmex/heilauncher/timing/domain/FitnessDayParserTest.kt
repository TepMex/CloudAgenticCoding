package com.tepmex.heilauncher.timing.domain

import java.time.LocalDate
import java.time.ZoneOffset
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FitnessDayParserTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 20)
    private val epoch = day.atStartOfDay(zone).toEpochSecond()

    @Test
    fun stepsUseTheAggregateTimeNotTheValueTime() {
        val item = JSONObject(
            """
            {"time": $epoch, "update_time": 9, "value": {"time": 1, "steps": 8420, "distance": 5}}
            """.trimIndent(),
        )
        val reading = FitnessDayParser.steps(item, zone)
        assertEquals(day, reading!!.date)
        assertEquals(8420.0, reading.value, 0.001)
        assertEquals(9L, reading.updatedEpochSec)
    }

    @Test
    fun stepsAcceptAStringValueAndRejectAMissingCount() {
        val encoded = JSONObject()
            .put("time", epoch)
            .put("value", JSONObject().put("steps", 12).toString())
        assertEquals(12.0, FitnessDayParser.steps(encoded, zone)!!.value, 0.001)
        val missing = JSONObject().put("time", epoch).put("value", JSONObject().put("distance", 1))
        assertNull(FitnessDayParser.steps(missing, zone))
        assertNull(FitnessDayParser.steps(JSONObject().put("value", JSONObject().put("steps", 1)), zone))
    }

    @Test
    fun sleepHoursComeFromTotalDurationMinutes() {
        val item = JSONObject()
            .put("time", epoch)
            .put("value", JSONObject().put("total_duration", 433).put("sleep_score", 80))
        val reading = FitnessDayParser.sleepHours(item, zone)
        assertEquals(day, reading!!.date)
        assertEquals(433.0 / 60.0, reading.value, 0.0001)
    }

    @Test
    fun sleepFallsBackToSegmentDurationsAndSkipsEmptyNights() {
        val segments = org.json.JSONArray()
            .put(JSONObject().put("bedtime", 1).put("wake_up_time", 2).put("duration", 120))
            .put(JSONObject().put("bedtime", 3).put("wake_up_time", 4).put("duration", 30))
        val item = JSONObject()
            .put("time", epoch)
            .put("value", JSONObject().put("segment_details", segments))
        assertEquals(2.5, FitnessDayParser.sleepHours(item, zone)!!.value, 0.001)
        val empty = JSONObject()
            .put("time", epoch)
            .put("value", JSONObject().put("total_duration", 0))
        assertNull(FitnessDayParser.sleepHours(empty, zone))
    }
}
