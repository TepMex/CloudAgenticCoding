package com.tepmex.runninglog.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningMetricsTest {
    @Test
    fun heartbitsPerKm_isAvgBpmTimesTempoMinutes() {
        // 150 bpm × 6.0 min/km = 900
        val paceSec = 6.0 * 60
        assertEquals(900.0, RunningMetrics.heartbitsPerKm(150, paceSec), 0.001)
    }

    @Test
    fun paceFallsBackToDurationOverDistance() {
        val pace = RunningMetrics.resolvePaceSecPerKm(
            avgPaceSec = 0.0,
            durationSec = 1800,
            distanceMeters = 5000.0,
        )
        assertEquals(360.0, pace, 0.001)
    }

    @Test
    fun cadenceFallsBackToStepsOverMinutes() {
        val cadence = RunningMetrics.resolveCadenceSpm(
            avgCadence = 0.0,
            steps = 1800,
            durationSec = 600,
        )
        assertEquals(180.0, cadence, 0.001)
    }

    @Test
    fun formatPace_mmss() {
        assertEquals("5:30", RunningMetrics.formatPace(330.0))
        assertEquals("—", RunningMetrics.formatPace(0.0))
    }
}

class SportRecordParserTest {
    @Test
    fun filtersRunningTypes() {
        assertTrue(SportRecordParser.isRunning("outdoor_running"))
        assertTrue(SportRecordParser.isRunning("treadmill"))
        assertFalse(SportRecordParser.isRunning("cycling"))
    }

    @Test
    fun parsesCloudPayload() {
        val rec = JSONObject(
            """
            {
              "sid": "run-1",
              "key": "outdoor_running",
              "watermark": 99,
              "value": "{\"start_time\":1700000000,\"end_time\":1700001800,\"duration\":1800,\"distance\":5000,\"avg_hrm\":148,\"avg_pace\":360,\"avg_cadence\":170,\"calories\":320}"
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals("run-1#99", parsed.workoutId)
        assertEquals("outdoor_running", parsed.sportType)
        assertEquals(5000.0, parsed.distanceMeters, 0.01)
        assertEquals(360.0, parsed.paceSecPerKm, 0.01)
        assertEquals(148, parsed.avgBpm)
        assertEquals(170.0, parsed.cadenceSpm, 0.01)
        assertEquals(99L, parsed.watermark)
        assertEquals(148 * 6.0, RunningMetrics.heartbitsPerKm(parsed.avgBpm, parsed.paceSecPerKm), 0.01)
    }

    @Test
    fun prefersCorrectedDistance() {
        val rec = JSONObject(
            """
            {
              "sid": "run-2",
              "key": "treadmill",
              "watermark": 1,
              "value": {"distance": 4000, "corrected_distance": 4120, "duration": 1200, "avg_hrm": 140}
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals("run-2#1", parsed.workoutId)
        assertEquals(4120.0, parsed.distanceMeters, 0.01)
        assertEquals(1200 / 4.12, parsed.paceSecPerKm, 0.5)
    }

    @Test
    fun resolveWorkoutId_prefersSidPlusWatermark() {
        assertEquals("abc#10", SportRecordParser.resolveWorkoutId("abc", 10L, 0L))
        assertEquals("wm:10", SportRecordParser.resolveWorkoutId("", 10L, 0L))
        assertEquals("abc#1700", SportRecordParser.resolveWorkoutId("abc", 0L, 1700L))
        assertEquals("abc", SportRecordParser.resolveWorkoutId("abc", 0L, 0L))
    }
}
