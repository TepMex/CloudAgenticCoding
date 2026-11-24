package com.tepmex.swimminglog.domain

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainerConsultationPromptTest {
    private val zone = ZoneOffset.UTC

    private fun sampleSwim(
        start: Long = 1_700_000_000L,
        sportType: String = "pool_swimming",
        durationSec: Int = 1800,
        distanceMeters: Double = 1500.0,
        paceSecPer100m: Double = 120.0,
        avgBpm: Int = 140,
        maxBpm: Int = 165,
        heartbitsPer100m: Double = 280.0,
        swolf: Double = 38.0,
        calories: Double = 400.0,
    ) = SwimSummaryForPrompt(
        startTimeEpochSec = start,
        sportType = sportType,
        durationSec = durationSec,
        distanceMeters = distanceMeters,
        paceSecPer100m = paceSecPer100m,
        avgBpm = avgBpm,
        maxBpm = maxBpm,
        heartbitsPer100m = heartbitsPer100m,
        swolf = swolf,
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
        assertEquals("Pool", TrainerConsultationPrompt.sportLabel("pool_swimming"))
        assertEquals("Open water", TrainerConsultationPrompt.sportLabel("open_swimming"))
        assertEquals("Open water", TrainerConsultationPrompt.sportLabel("open_water_swimming"))
        assertEquals("cycling", TrainerConsultationPrompt.sportLabel("cycling"))
    }

    @Test
    fun build_emptyJournal_explainsNoSwims() {
        val prompt = TrainerConsultationPrompt.build(
            swimsNewestFirst = emptyList(),
            zoneId = zone,
        )
        assertTrue(prompt.contains("experienced swimming coach"))
        assertTrue(prompt.contains("No swims are available"))
        assertTrue(prompt.contains("Trailing 365-day averages"))
        assertFalse(prompt.contains("### Swim 1"))
        assertFalse(prompt.contains("VO"))
        assertFalse(prompt.contains("cadence"))
    }

    @Test
    fun build_includesHumanReadableAggregatesForLastTenOnly() {
        val swims = (1..12).map { i ->
            sampleSwim(
                start = 1_700_000_000L - i * 86_400L,
                distanceMeters = 1000.0 * i,
                avgBpm = 130 + i,
                calories = 100.0 * i,
            )
        }
        val averages = TrailingYearAverages(avgSwolf = 37.0, avgHeartbitsPer100m = 270.0)
        val prompt = TrainerConsultationPrompt.build(
            swimsNewestFirst = swims,
            trailingYearAverages = averages,
            zoneId = zone,
            limit = 10,
        )

        assertTrue(prompt.contains("Last 10 swims (newest first)"))
        assertTrue(prompt.contains("### Snapshot across these swims"))
        assertTrue(prompt.contains("Total distance: 55.00 km"))
        assertTrue(prompt.contains("### Swim 1 —"))
        assertTrue(prompt.contains("### Swim 10 —"))
        assertFalse(prompt.contains("### Swim 11"))
        assertTrue(prompt.contains("Pool"))
        assertTrue(prompt.contains("Pace (temp): 2:00 /100 m"))
        assertTrue(prompt.contains("Heartbits/100 m:"))
        assertTrue(prompt.contains("SWOLF: 38"))
        assertTrue(prompt.contains("Calories:"))
        assertTrue(prompt.contains("Average SWOLF: 37"))
        assertTrue(prompt.contains("Average heartbits/100 m: 270"))
        assertTrue(prompt.contains("Please respond as my swimming coach"))
        assertFalse(prompt.contains("VO"))
        assertFalse(prompt.contains("cadence"))
        assertFalse(prompt.contains("vo2"))
        assertFalse(prompt.contains("rawJson"))
        assertFalse(prompt.contains("workoutId"))
        assertFalse(prompt.contains("{"))
    }

    @Test
    fun build_singleSwim_usesSingularHeading() {
        val prompt = TrainerConsultationPrompt.build(
            swimsNewestFirst = listOf(sampleSwim(sportType = "open_swimming")),
            zoneId = zone,
        )
        assertTrue(prompt.contains("Last 1 swim (newest first)"))
        assertTrue(prompt.contains("Open water"))
        assertTrue(prompt.contains("Duration: 30:00"))
        assertTrue(prompt.contains("Distance: 1500 m"))
    }
}
