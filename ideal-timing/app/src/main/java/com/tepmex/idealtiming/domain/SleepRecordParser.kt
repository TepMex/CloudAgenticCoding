package com.tepmex.idealtiming.domain

import org.json.JSONArray
import org.json.JSONObject

data class SleepSegment(
    val bedtimeEpochSec: Long,
    val wakeUpEpochSec: Long,
    val durationMin: Int = 0,
)

data class SleepRecord(
    val timeEpochSec: Long,
    val totalDurationMin: Int,
    val sleepScore: Int,
    val segments: List<SleepSegment>,
) {
    val maxWakeUpEpochSec: Long?
        get() = segments.map { it.wakeUpEpochSec }.filter { it > 0 }.maxOrNull()
}

data class WakeChoice(
    val wakeEpochSec: Long,
    val sourceDateEpochSec: Long,
    val sleepScore: Int,
)

object SleepRecordParser {
    fun parseAggregatedItem(item: JSONObject): SleepRecord? {
        val time = item.optLong("time", 0L)
        val valueRaw = item.opt("value")
        val value = when (valueRaw) {
            is JSONObject -> valueRaw
            is String -> if (valueRaw.isBlank()) JSONObject() else JSONObject(valueRaw)
            else -> return null
        }
        val segments = parseSegments(value.opt("segment_details"))
        return SleepRecord(
            timeEpochSec = time,
            totalDurationMin = value.optInt("total_duration", 0),
            sleepScore = value.optInt("sleep_score", 0),
            segments = segments,
        )
    }

    fun parseSegments(raw: Any?): List<SleepSegment> {
        val arr = when (raw) {
            is JSONArray -> raw
            is String -> if (raw.isBlank()) return emptyList() else JSONArray(raw)
            else -> return emptyList()
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val wake = o.optLong("wake_up_time", 0L)
                val bed = o.optLong("bedtime", 0L)
                if (wake <= 0L && bed <= 0L) continue
                add(
                    SleepSegment(
                        bedtimeEpochSec = bed,
                        wakeUpEpochSec = wake,
                        durationMin = o.optInt("duration", 0),
                    ),
                )
            }
        }
    }

    /**
     * Prefer the most recent wake_up_time that is not after [nowEpochSec].
     * If every wake is slightly in the future (sync lag), take the latest wake anyway.
     */
    fun chooseWake(
        records: List<SleepRecord>,
        nowEpochSec: Long,
    ): WakeChoice? {
        data class Cand(val wake: Long, val source: Long, val score: Int)
        val candidates = records.flatMap { rec ->
            rec.segments
                .map { it.wakeUpEpochSec }
                .filter { it > 0 }
                .map { Cand(it, rec.timeEpochSec, rec.sleepScore) }
        }
        if (candidates.isEmpty()) return null
        val past = candidates.filter { it.wake <= nowEpochSec }
        val best = (if (past.isNotEmpty()) past else candidates).maxBy { it.wake }
        return WakeChoice(
            wakeEpochSec = best.wake,
            sourceDateEpochSec = best.source,
            sleepScore = best.score,
        )
    }
}
