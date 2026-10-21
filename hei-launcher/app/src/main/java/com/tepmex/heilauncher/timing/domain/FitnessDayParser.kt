package com.tepmex.heilauncher.timing.domain

import com.tepmex.heilauncher.domain.DailyReading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject

/**
 * Daily Mi Fitness aggregates.
 * Steps come from key `steps` (`value.steps`). Sleep hours come from key `sleep`
 * (`value.total_duration` minutes, divided by 60). The day is the aggregate
 * item’s `time`, the same instant the wake-up parser uses.
 */
object FitnessDayParser {
    fun steps(item: JSONObject, zone: ZoneId): DailyReading? {
        val value = valueObject(item) ?: return null
        if (!value.has("steps")) return null
        val steps = value.optDouble("steps")
        if (steps.isNaN()) return null
        val date = dateOf(item, zone) ?: return null
        return DailyReading(date, steps, item.optLong("update_time", 0L))
    }

    fun sleepHours(item: JSONObject, zone: ZoneId): DailyReading? {
        val value = valueObject(item) ?: return null
        val hasDuration = value.has("total_duration")
        val segments = SleepRecordParser.parseSegments(value.opt("segment_details"))
        if (!hasDuration && segments.isEmpty()) return null
        var minutes = if (hasDuration) value.optInt("total_duration", 0) else 0
        if (minutes <= 0) {
            minutes = segments.sumOf { it.durationMin }
        }
        if (minutes <= 0) return null
        val date = dateOf(item, zone) ?: return null
        return DailyReading(date, minutes / 60.0, item.optLong("update_time", 0L))
    }

    private fun dateOf(item: JSONObject, zone: ZoneId): LocalDate? {
        val epoch = item.optLong("time", 0L)
        if (epoch <= 0L) return null
        return Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate()
    }

    private fun valueObject(item: JSONObject): JSONObject? = when (val raw = item.opt("value")) {
        is JSONObject -> raw
        is String -> if (raw.isBlank()) null else JSONObject(raw)
        else -> null
    }
}
