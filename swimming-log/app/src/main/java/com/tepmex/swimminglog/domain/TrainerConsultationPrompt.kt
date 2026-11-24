package com.tepmex.swimminglog.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Per-swim aggregate metrics for a human-readable AI coach prompt.
 * Intentionally excludes time-series / GPS point data and VO₂ max.
 */
data class SwimSummaryForPrompt(
    val startTimeEpochSec: Long,
    val sportType: String,
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPer100m: Double,
    val avgBpm: Int,
    val maxBpm: Int,
    val heartbitsPer100m: Double,
    val swolf: Double,
    val calories: Double,
)

/**
 * Builds a paste-ready consultation prompt for an AI swimming coach from
 * the newest [limit] swim aggregates (default 10).
 */
object TrainerConsultationPrompt {
    const val DEFAULT_SWIM_LIMIT = 10

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    fun build(
        swimsNewestFirst: List<SwimSummaryForPrompt>,
        trailingYearAverages: TrailingYearAverages = TrailingYearAverages(null, null),
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = DEFAULT_SWIM_LIMIT,
    ): String {
        val selected = swimsNewestFirst.take(limit.coerceAtLeast(0))
        return buildString {
            appendLine(
                "You are an experienced swimming coach. Please review my recent swimming " +
                    "journal aggregates and give practical training advice " +
                    "(training load, recovery, pacing per 100 m, heart-rate efficiency, " +
                    "SWOLF, and progression). Ask clarifying questions if you need context " +
                    "(goals, injuries, weekly schedule, pool length).",
            )
            appendLine()
            appendLine(
                "Data notes: these are per-swim summary metrics only (no GPS track or " +
                    "heart-rate time series). Pace is min:sec per 100 m, calculated from " +
                    "duration and distance when both are present. Heartbits/100 m = average " +
                    "heart rate × pace in minutes per 100 m (lower is more efficient). " +
                    "SWOLF is strokes plus seconds for one pool length (lower is more efficient).",
            )
            appendLine()

            if (selected.isEmpty()) {
                appendLine("## Recent swims")
                appendLine()
                appendLine("No swims are available in the journal yet.")
            } else {
                appendLine("## Last ${selected.size} swim${if (selected.size == 1) "" else "s"} (newest first)")
                appendLine()
                appendSnapshot(selected)
                appendLine()
                selected.forEachIndexed { index, swim ->
                    appendSwim(index + 1, swim, zoneId)
                    if (index < selected.lastIndex) appendLine()
                }
            }

            appendLine()
            appendLine("## Trailing 365-day averages (all journal swims in window)")
            appendLine()
            appendLine(
                "- Average SWOLF: ${
                    trailingYearAverages.avgSwolf?.let { "%.0f".format(it) } ?: "—"
                }",
            )
            appendLine(
                "- Average heartbits/100 m: ${
                    trailingYearAverages.avgHeartbitsPer100m?.let { "%.0f".format(it) } ?: "—"
                }",
            )
            appendLine()
            append(
                "Please respond as my swimming coach: what stands out, what to keep, " +
                    "and what to change next.",
            )
        }
    }

    private fun StringBuilder.appendSnapshot(swims: List<SwimSummaryForPrompt>) {
        val distances = swims.map { it.distanceMeters }.filter { it > 0 }
        val paces = swims.map { it.paceSecPer100m }.filter { it > 0 }
        val avgBpms = swims.map { it.avgBpm }.filter { it > 0 }
        val swolfs = swims.map { it.swolf }.filter { it > 0 }
        val heartbits = swims.map { it.heartbitsPer100m }.filter { it > 0 }
        val totalDuration = swims.sumOf { it.durationSec.coerceAtLeast(0) }
        val totalCalories = swims.map { it.calories }.filter { it > 0 }.sum()

        appendLine("### Snapshot across these swims")
        appendLine(
            "- Total distance: ${
                if (distances.isEmpty()) "—" else SwimmingMetrics.formatDistanceTotal(distances.sum())
            }",
        )
        appendLine("- Total duration: ${formatDuration(totalDuration)}")
        appendLine(
            "- Average pace: ${
                if (paces.isEmpty()) "—" else "${SwimmingMetrics.formatPace(paces.average())} /100 m"
            }",
        )
        appendLine(
            "- Average heart rate: ${
                if (avgBpms.isEmpty()) "—" else "${avgBpms.average().roundToInt()} bpm"
            }",
        )
        appendLine(
            "- Average SWOLF: ${
                if (swolfs.isEmpty()) "—" else "%.0f".format(swolfs.average())
            }",
        )
        appendLine(
            "- Average heartbits/100 m: ${
                if (heartbits.isEmpty()) "—" else "%.0f".format(heartbits.average())
            }",
        )
        if (totalCalories > 0) {
            appendLine("- Total calories: ${totalCalories.roundToInt()}")
        }
    }

    private fun StringBuilder.appendSwim(
        number: Int,
        swim: SwimSummaryForPrompt,
        zoneId: ZoneId,
    ) {
        val date = dateFormatter
            .withZone(zoneId)
            .format(Instant.ofEpochSecond(swim.startTimeEpochSec))
        appendLine("### Swim $number — $date · ${sportLabel(swim.sportType)}")
        appendLine("- Distance: ${SwimmingMetrics.formatDistance(swim.distanceMeters)}")
        appendLine("- Duration: ${formatDuration(swim.durationSec)}")
        appendLine("- Pace (temp): ${SwimmingMetrics.formatPace(swim.paceSecPer100m)} /100 m")
        appendLine("- Avg heart rate: ${if (swim.avgBpm > 0) "${swim.avgBpm} bpm" else "—"}")
        appendLine("- Max heart rate: ${if (swim.maxBpm > 0) "${swim.maxBpm} bpm" else "—"}")
        appendLine(
            "- Heartbits/100 m: ${
                swim.heartbitsPer100m.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "—"
            }",
        )
        appendLine(
            "- SWOLF: ${
                swim.swolf.takeIf { it > 0 }?.let { SwimmingMetrics.formatSwolf(it) } ?: "—"
            }",
        )
        appendLine(
            "- Calories: ${
                swim.calories.takeIf { it > 0 }?.let { it.roundToInt().toString() } ?: "—"
            }",
        )
    }

    fun sportLabel(sportType: String): String = when (sportType) {
        "pool_swimming" -> "Pool"
        "open_swimming", "open_water_swimming" -> "Open water"
        else -> sportType.ifBlank { "Unknown" }
    }

    fun formatDuration(durationSec: Int): String {
        if (durationSec <= 0) return "—"
        val h = durationSec / 3600
        val m = (durationSec % 3600) / 60
        val s = durationSec % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }
}
