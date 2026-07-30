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
    fun runningOxygenCost_acsmFlatRunning() {
        // 6:00/km → 166.666… m/min → 0.2×v + 3.5 = 36.833…
        val cost = RunningMetrics.runningOxygenCostMlKgMin(360.0)
        assertEquals(0.2 * (60_000.0 / 360.0) + 3.5, cost, 0.001)
    }

    @Test
    fun estimateVo2Max_dividesCostByHrIntensity() {
        // pace 6:00, avg 150, max 185 → cost / (150/185)
        val paceSec = 360.0
        val cost = RunningMetrics.runningOxygenCostMlKgMin(paceSec)
        val expected = cost / (150.0 / 185.0)
        assertEquals(expected, RunningMetrics.estimateVo2MaxMlKgMin(paceSec, 150, 185), 0.001)
    }

    @Test
    fun estimateVo2Max_zeroWhenInputsInvalid() {
        assertEquals(0.0, RunningMetrics.estimateVo2MaxMlKgMin(0.0, 150, 185), 0.0)
        assertEquals(0.0, RunningMetrics.estimateVo2MaxMlKgMin(360.0, 0, 185), 0.0)
        assertEquals(0.0, RunningMetrics.estimateVo2MaxMlKgMin(360.0, 150, 0), 0.0)
        assertEquals(0.0, RunningMetrics.estimateVo2MaxMlKgMin(360.0, 190, 185), 0.0)
    }

    @Test
    fun resolveVo2Max_prefersCloudValue() {
        assertEquals(
            49.0,
            RunningMetrics.resolveVo2MaxMlKgMin(
                cloudVo2Max = 49,
                paceSecPerKm = 360.0,
                avgBpm = 150,
                maxBpm = 185,
            ),
            0.0,
        )
    }

    @Test
    fun resolveVo2Max_fallsBackToEstimateWhenCloudMissing() {
        val estimated = RunningMetrics.estimateVo2MaxMlKgMin(360.0, 150, 185)
        assertEquals(
            estimated,
            RunningMetrics.resolveVo2MaxMlKgMin(
                cloudVo2Max = 0,
                paceSecPerKm = 360.0,
                avgBpm = 150,
                maxBpm = 185,
            ),
            0.001,
        )
    }

    @Test
    fun formatVo2Max_roundedIntegerOrDash() {
        assertEquals("45", RunningMetrics.formatVo2Max(45.4))
        assertEquals("—", RunningMetrics.formatVo2Max(0.0))
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

    @Test
    fun trailingYearAverages_meansOfRunsInLast365Days() {
        val day = 86_400L
        val now = 400 * day
        val samples = listOf(
            // Inside window
            RunMetricSample(startTimeEpochSec = now - 10 * day, cadenceSpm = 160.0, heartbitsPerKm = 800.0),
            RunMetricSample(startTimeEpochSec = now - 100 * day, cadenceSpm = 180.0, heartbitsPerKm = 900.0),
            // Outside window (>365 days ago)
            RunMetricSample(startTimeEpochSec = now - 400 * day, cadenceSpm = 200.0, heartbitsPerKm = 700.0),
            // Zero metrics ignored for that average
            RunMetricSample(startTimeEpochSec = now - 5 * day, cadenceSpm = 0.0, heartbitsPerKm = 0.0),
        )
        val avg = RunningMetrics.trailingYearAverages(samples, nowEpochSec = now)
        assertEquals(170.0, avg.avgCadenceSpm!!, 0.001)
        assertEquals(850.0, avg.avgHeartbitsPerKm!!, 0.001)
    }

    @Test
    fun trailingYearAverages_nullWhenNoValidSamplesInWindow() {
        val avg = RunningMetrics.trailingYearAverages(emptyList(), nowEpochSec = 1_000_000L)
        assertEquals(null, avg.avgCadenceSpm)
        assertEquals(null, avg.avgHeartbitsPerKm)
    }

    @Test
    fun compareCadence_higherIsBetter() {
        assertEquals(MetricVsAverage.Better, RunningMetrics.compareCadence(180.0, 170.0))
        assertEquals(MetricVsAverage.Worse, RunningMetrics.compareCadence(160.0, 170.0))
        assertEquals(MetricVsAverage.Equal, RunningMetrics.compareCadence(170.0, 170.0))
        assertEquals(MetricVsAverage.Unavailable, RunningMetrics.compareCadence(180.0, null))
        assertEquals(MetricVsAverage.Unavailable, RunningMetrics.compareCadence(0.0, 170.0))
    }

    @Test
    fun compareHeartbitsPerKm_lowerIsBetter() {
        assertEquals(MetricVsAverage.Better, RunningMetrics.compareHeartbitsPerKm(800.0, 850.0))
        assertEquals(MetricVsAverage.Worse, RunningMetrics.compareHeartbitsPerKm(900.0, 850.0))
        assertEquals(MetricVsAverage.Equal, RunningMetrics.compareHeartbitsPerKm(850.0, 850.0))
        assertEquals(MetricVsAverage.Unavailable, RunningMetrics.compareHeartbitsPerKm(800.0, null))
        assertEquals(MetricVsAverage.Unavailable, RunningMetrics.compareHeartbitsPerKm(0.0, 850.0))
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
              "value": "{\"start_time\":1700000000,\"end_time\":1700001800,\"duration\":1800,\"distance\":5000,\"avg_hrm\":148,\"max_hrm\":182,\"vo2_max\":47,\"avg_pace\":360,\"avg_cadence\":170,\"calories\":320}"
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals("run-1#99", parsed.workoutId)
        assertEquals("outdoor_running", parsed.sportType)
        assertEquals(5000.0, parsed.distanceMeters, 0.01)
        assertEquals(360.0, parsed.paceSecPerKm, 0.01)
        assertEquals(148, parsed.avgBpm)
        assertEquals(182, parsed.maxBpm)
        assertEquals(47, parsed.cloudVo2Max)
        assertEquals(170.0, parsed.cadenceSpm, 0.01)
        assertEquals(99L, parsed.watermark)
        assertEquals(148 * 6.0, RunningMetrics.heartbitsPerKm(parsed.avgBpm, parsed.paceSecPerKm), 0.01)
        assertEquals(
            47.0,
            RunningMetrics.resolveVo2MaxMlKgMin(
                parsed.cloudVo2Max,
                parsed.paceSecPerKm,
                parsed.avgBpm,
                parsed.maxBpm,
            ),
            0.0,
        )
    }

    @Test
    fun estimatesVo2MaxWhenCloudFieldAbsent() {
        val rec = JSONObject(
            """
            {
              "sid": "run-3",
              "key": "outdoor_running",
              "watermark": 3,
              "value": {
                "distance": 5000,
                "duration": 1800,
                "avg_pace": 360,
                "avg_hrm": 150,
                "max_hrm": 185
              }
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals(0, parsed.cloudVo2Max)
        assertEquals(185, parsed.maxBpm)
        val expected = RunningMetrics.estimateVo2MaxMlKgMin(360.0, 150, 185)
        assertEquals(
            expected,
            RunningMetrics.resolveVo2MaxMlKgMin(
                parsed.cloudVo2Max,
                parsed.paceSecPerKm,
                parsed.avgBpm,
                parsed.maxBpm,
            ),
            0.001,
        )
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
