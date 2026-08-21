package com.tepmex.runninglog.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class TrainerConsultationPromptTest {
    private val zone = ZoneOffset.UTC

    private fun sampleRun(
        start: Long = 1_700_000_000L,
        sportType: String = "outdoor_running",
        durationSec: Int = 1800,
        distanceMeters: Double = 5000.0,
        paceSecPerKm: Double = 360.0,
        avgBpm: Int = 148,
        maxBpm: Int = 182,
        heartbitsPerKm: Double = 888.0,
        cadenceSpm: Double = 170.0,
        vo2MaxMlKgMin: Double = 47.0,
        calories: Double = 320.0,
    ) = RunSummaryForPrompt(
        startTimeEpochSec = start,
        sportType = sportType,
        durationSec = durationSec,
        distanceMeters = distanceMeters,
        paceSecPerKm = paceSecPerKm,
        avgBpm = avgBpm,
        maxBpm = maxBpm,
        heartbitsPerKm = heartbitsPerKm,
        cadenceSpm = cadenceSpm,
        vo2MaxMlKgMin = vo2MaxMlKgMin,
        calories = calories,
    )

    @Test
    fun formatDuration_mmssAndHhmmss() {
        assertEquals("—", TrainerConsultationPrompt.formatDuration(0))
        assertEquals("30:00", TrainerConsultationPrompt.formatDuration(1800))
        assertEquals("1:05:07", TrainerConsultationPrompt.formatDuration(3907))
    }

    @Test
    fun sportLabel_humanReadable() {
        assertEquals("Outdoor", TrainerConsultationPrompt.sportLabel("outdoor_running"))
        assertEquals("Treadmill", TrainerConsultationPrompt.sportLabel("treadmill"))
        assertEquals("cycling", TrainerConsultationPrompt.sportLabel("cycling"))
    }

    @Test
    fun build_emptyJournal_explainsNoRuns() {
        val prompt = TrainerConsultationPrompt.build(
            runsNewestFirst = emptyList(),
            zoneId = zone,
        )
        assertTrue(prompt.contains("experienced running coach"))
        assertTrue(prompt.contains("No runs are available"))
        assertTrue(prompt.contains("Trailing 365-day averages"))
        assertFalse(prompt.contains("### Run 1"))
    }

    @Test
    fun build_includesHumanReadableAggregatesForLastTenOnly() {
        val runs = (1..12).map { i ->
            sampleRun(
                start = 1_700_000_000L - i * 86_400L,
                distanceMeters = 1000.0 * i,
                avgBpm = 140 + i,
                calories = 100.0 * i,
            )
        }
        val averages = TrailingYearAverages(avgCadenceSpm = 168.0, avgHeartbitsPerKm = 860.0)
        val prompt = TrainerConsultationPrompt.build(
            runsNewestFirst = runs,
            trailingYearAverages = averages,
            zoneId = zone,
            limit = 10,
        )

        assertTrue(prompt.contains("Last 10 runs (newest first)"))
        assertTrue(prompt.contains("### Snapshot across these runs"))
        assertTrue(prompt.contains("Total distance: 55.00 km")) // 1+…+10 = 55
        assertTrue(prompt.contains("### Run 1 —"))
        assertTrue(prompt.contains("### Run 10 —"))
        assertFalse(prompt.contains("### Run 11"))
        assertTrue(prompt.contains("Outdoor"))
        assertTrue(prompt.contains("Pace (temp): 6:00 /km"))
        assertTrue(prompt.contains("Avg heart rate: 148 bpm") || prompt.contains("Avg heart rate:"))
        assertTrue(prompt.contains("Max heart rate:"))
        assertTrue(prompt.contains("Heartbits/km:"))
        assertTrue(prompt.contains("Cadence: 170 spm"))
        assertTrue(prompt.contains("VO₂ max: 47 ml/kg/min"))
        assertTrue(prompt.contains("Calories:"))
        assertTrue(prompt.contains("Average cadence: 168 spm"))
        assertTrue(prompt.contains("Average heartbits/km: 860"))
        assertTrue(prompt.contains("Please respond as my running trainer"))
        // No raw JSON / machine payloads
        assertFalse(prompt.contains("rawJson"))
        assertFalse(prompt.contains("workoutId"))
        assertFalse(prompt.contains("{"))
    }

    @Test
    fun build_singleRun_usesSingularHeading() {
        val prompt = TrainerConsultationPrompt.build(
            runsNewestFirst = listOf(sampleRun(sportType = "treadmill")),
            zoneId = zone,
        )
        assertTrue(prompt.contains("Last 1 run (newest first)"))
        assertTrue(prompt.contains("Treadmill"))
        assertTrue(prompt.contains("Duration: 30:00"))
        assertTrue(prompt.contains("Distance: 5.00 km"))
    }
}
