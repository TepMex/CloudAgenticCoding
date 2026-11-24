package com.tepmex.swimminglog.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwimmingMetricsTest {
    @Test
    fun heartbitsPer100m_isAvgBpmTimesTempoMinutes() {
        // 140 bpm × 2.0 min/100 m = 280
        val paceSec = 2.0 * 60
        assertEquals(280.0, SwimmingMetrics.heartbitsPer100m(140, paceSec), 0.001)
    }

    @Test
    fun pace_prefersDurationOverDistance() {
        // 1500 m in 1800 s → 120 s/100 m, even if cloud avg_pace disagrees.
        val pace = SwimmingMetrics.resolvePaceSecPer100m(
            avgPace = 900.0,
            durationSec = 1800,
            distanceMeters = 1500.0,
        )
        assertEquals(120.0, pace, 0.001)
    }

    @Test
    fun pace_cloudFallbackTreatsLargeValuesAsSecPerKm() {
        assertEquals(120.0, SwimmingMetrics.paceFromCloudAvgPace(1200.0), 0.001)
        assertEquals(45.0, SwimmingMetrics.paceFromCloudAvgPace(450.0), 0.001)
    }

    @Test
    fun pace_cloudFallbackKeepsAlreadyPer100mValues() {
        assertEquals(120.0, SwimmingMetrics.paceFromCloudAvgPace(120.0), 0.001)
        assertEquals(0.0, SwimmingMetrics.resolvePaceSecPer100m(0.0, 0, 0.0), 0.0)
    }

    @Test
    fun swolf_prefersCloudAverage() {
        val swolf = SwimmingMetrics.resolveSwolf(
            cloudAvgSwolf = 38.0,
            strokeCount = 640,
            avgStrokeFreqPerMin = 48.0,
            poolLengthMeters = 25.0,
            durationSec = 800,
            distanceMeters = 1000.0,
        )
        assertEquals(38.0, swolf, 0.001)
    }

    @Test
    fun swolf_estimatesFromStrokesAndPoolLength() {
        // 1000 m / 25 m = 40 lengths; 800 s → 20 s/length; 640 strokes → 16/length; SWOLF 36.
        val swolf = SwimmingMetrics.estimateSwolf(
            strokeCount = 640,
            avgStrokeFreqPerMin = 0.0,
            poolLengthMeters = 25.0,
            durationSec = 800,
            distanceMeters = 1000.0,
        )
        assertEquals(36.0, swolf, 0.001)
    }

    @Test
    fun swolf_estimatesFromStrokeFrequencyWhenCountMissing() {
        val swolf = SwimmingMetrics.estimateSwolf(
            strokeCount = 0,
            avgStrokeFreqPerMin = 48.0,
            poolLengthMeters = 25.0,
            durationSec = 800,
            distanceMeters = 1000.0,
        )
        assertEquals(36.0, swolf, 0.001)
    }

    @Test
    fun swolf_zeroWhenPoolLengthMissing() {
        assertEquals(
            0.0,
            SwimmingMetrics.estimateSwolf(
                strokeCount = 640,
                avgStrokeFreqPerMin = 48.0,
                poolLengthMeters = 0.0,
                durationSec = 800,
                distanceMeters = 1000.0,
            ),
            0.0,
        )
    }

    @Test
    fun poolLength_centimetersConverted() {
        assertEquals(25.0, SwimmingMetrics.normalizePoolLengthMeters(2500.0), 0.001)
        assertEquals(50.0, SwimmingMetrics.normalizePoolLengthMeters(50.0), 0.001)
    }

    @Test
    fun strokeFreq_subFiveIsPerSecond() {
        assertEquals(48.0, SwimmingMetrics.normalizeStrokeFreqPerMin(0.8), 0.001)
        assertEquals(32.0, SwimmingMetrics.normalizeStrokeFreqPerMin(32.0), 0.001)
    }

    @Test
    fun formatPace_mmss() {
        assertEquals("2:05", SwimmingMetrics.formatPace(125.0))
        assertEquals("—", SwimmingMetrics.formatPace(0.0))
    }

    @Test
    fun formatDistance_metersAndTotals() {
        assertEquals("1500 m", SwimmingMetrics.formatDistance(1500.0))
        assertEquals("750 m", SwimmingMetrics.formatDistance(750.4))
        assertEquals("—", SwimmingMetrics.formatDistance(0.0))
        assertEquals("55.00 km", SwimmingMetrics.formatDistanceTotal(55_000.0))
        assertEquals("800 m", SwimmingMetrics.formatDistanceTotal(800.0))
    }

    @Test
    fun trailingYearAverages_meansOfSwimsInLast365Days() {
        val day = 86_400L
        val now = 400 * day
        val samples = listOf(
            SwimMetricSample(startTimeEpochSec = now - 10 * day, swolf = 36.0, heartbitsPer100m = 260.0),
            SwimMetricSample(startTimeEpochSec = now - 100 * day, swolf = 40.0, heartbitsPer100m = 300.0),
            SwimMetricSample(startTimeEpochSec = now - 400 * day, swolf = 50.0, heartbitsPer100m = 200.0),
            SwimMetricSample(startTimeEpochSec = now - 5 * day, swolf = 0.0, heartbitsPer100m = 0.0),
        )
        val avg = SwimmingMetrics.trailingYearAverages(samples, nowEpochSec = now)
        assertEquals(38.0, avg.avgSwolf!!, 0.001)
        assertEquals(280.0, avg.avgHeartbitsPer100m!!, 0.001)
    }

    @Test
    fun trailingYearAverages_nullWhenNoValidSamplesInWindow() {
        val avg = SwimmingMetrics.trailingYearAverages(emptyList(), nowEpochSec = 1_000_000L)
        assertEquals(null, avg.avgSwolf)
        assertEquals(null, avg.avgHeartbitsPer100m)
    }

    @Test
    fun compareSwolf_lowerIsBetter() {
        assertEquals(MetricVsAverage.Better, SwimmingMetrics.compareSwolf(34.0, 38.0))
        assertEquals(MetricVsAverage.Worse, SwimmingMetrics.compareSwolf(42.0, 38.0))
        assertEquals(MetricVsAverage.Equal, SwimmingMetrics.compareSwolf(38.0, 38.0))
        assertEquals(MetricVsAverage.Unavailable, SwimmingMetrics.compareSwolf(34.0, null))
        assertEquals(MetricVsAverage.Unavailable, SwimmingMetrics.compareSwolf(0.0, 38.0))
    }

    @Test
    fun compareHeartbitsPer100m_lowerIsBetter() {
        assertEquals(MetricVsAverage.Better, SwimmingMetrics.compareHeartbitsPer100m(260.0, 280.0))
        assertEquals(MetricVsAverage.Worse, SwimmingMetrics.compareHeartbitsPer100m(300.0, 280.0))
        assertEquals(MetricVsAverage.Equal, SwimmingMetrics.compareHeartbitsPer100m(280.0, 280.0))
        assertEquals(MetricVsAverage.Unavailable, SwimmingMetrics.compareHeartbitsPer100m(260.0, null))
        assertEquals(MetricVsAverage.Unavailable, SwimmingMetrics.compareHeartbitsPer100m(0.0, 280.0))
    }
}

class SportRecordParserTest {
    @Test
    fun filtersSwimmingTypes() {
        assertTrue(SportRecordParser.isSwimming("pool_swimming"))
        assertTrue(SportRecordParser.isSwimming("open_swimming"))
        assertTrue(SportRecordParser.isSwimming("open_water_swimming"))
        assertFalse(SportRecordParser.isSwimming("outdoor_running"))
        assertFalse(SportRecordParser.isSwimming("cycling"))
    }

    @Test
    fun parsesCloudPayloadPer100mAndIgnoresVo2AsAMetric() {
        val rec = JSONObject(
            """
            {
              "sid": "swim-1",
              "key": "pool_swimming",
              "watermark": 99,
              "value": "{\"start_time\":1700000000,\"end_time\":1700001800,\"duration\":1800,\"distance\":1500,\"avg_hrm\":140,\"max_hrm\":165,\"vo2_max\":47,\"avg_pace\":1200,\"avg_swolf\":38,\"calories\":400}"
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals("swim-1#99", parsed.workoutId)
        assertEquals("pool_swimming", parsed.sportType)
        assertEquals(1500.0, parsed.distanceMeters, 0.01)
        assertEquals(120.0, parsed.paceSecPer100m, 0.01)
        assertEquals(140, parsed.avgBpm)
        assertEquals(165, parsed.maxBpm)
        assertEquals(38.0, parsed.swolf, 0.01)
        assertEquals(99L, parsed.watermark)
        assertTrue(parsed.rawJson.contains("vo2_max"))
        assertEquals(140 * 2.0, SwimmingMetrics.heartbitsPer100m(parsed.avgBpm, parsed.paceSecPer100m), 0.01)
    }

    @Test
    fun estimatesSwolfWhenCloudFieldAbsent() {
        val rec = JSONObject(
            """
            {
              "sid": "swim-3",
              "key": "pool_swimming",
              "watermark": 3,
              "value": {
                "distance": 1000,
                "duration": 800,
                "avg_hrm": 130,
                "pool_length": 25,
                "stroke_count": 640
              }
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals(36.0, parsed.swolf, 0.001)
        assertEquals(80.0, parsed.paceSecPer100m, 0.001)
    }

    @Test
    fun readsNestedSwolfAverage() {
        val rec = JSONObject(
            """
            {
              "sid": "swim-4",
              "key": "open_swimming",
              "watermark": 4,
              "value": {
                "distance": 2000,
                "duration": 2400,
                "swolf": {"avg": 41}
              }
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals(41.0, parsed.swolf, 0.001)
        assertEquals(120.0, parsed.paceSecPer100m, 0.001)
    }

    @Test
    fun prefersCorrectedDistance() {
        val rec = JSONObject(
            """
            {
              "sid": "swim-2",
              "key": "open_water_swimming",
              "watermark": 1,
              "value": {"distance": 1400, "corrected_distance": 1500, "duration": 1800, "avg_hrm": 140}
            }
            """.trimIndent(),
        )
        val parsed = SportRecordParser.parseCloudRecord(rec)!!
        assertEquals("swim-2#1", parsed.workoutId)
        assertEquals(1500.0, parsed.distanceMeters, 0.01)
        assertEquals(120.0, parsed.paceSecPer100m, 0.01)
    }

    @Test
    fun resolveWorkoutId_prefersSidPlusWatermark() {
        assertEquals("abc#10", SportRecordParser.resolveWorkoutId("abc", 10L, 0L))
        assertEquals("wm:10", SportRecordParser.resolveWorkoutId("", 10L, 0L))
        assertEquals("abc#1700", SportRecordParser.resolveWorkoutId("abc", 0L, 1700L))
        assertEquals("abc", SportRecordParser.resolveWorkoutId("abc", 0L, 0L))
    }
}
