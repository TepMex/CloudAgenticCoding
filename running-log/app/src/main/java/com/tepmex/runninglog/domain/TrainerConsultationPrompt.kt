package com.tepmex.runninglog.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Per-run aggregate metrics for a human-readable AI trainer prompt.
 * Intentionally excludes time-series / GPS point data.
 */
data class RunSummaryForPrompt(
    val startTimeEpochSec: Long,
    val sportType: String,
    val durationSec: Int,
    val distanceMeters: Double,
    val paceSecPerKm: Double,
    val avgBpm: Int,
    val maxBpm: Int,
    val heartbitsPerKm: Double,
    val cadenceSpm: Double,
    val vo2MaxMlKgMin: Double,
    val calories: Double,
)

/**
 * Builds a paste-ready consultation prompt for an AI running coach from
 * the newest [limit] run aggregates (default 10).
 */
object TrainerConsultationPrompt {
    const val DEFAULT_RUN_LIMIT = 10

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    fun build(
        runsNewestFirst: List<RunSummaryForPrompt>,
        trailingYearAverages: TrailingYearAverages = TrailingYearAverages(null, null),
        zoneId: ZoneId = ZoneId.systemDefault(),
        limit: Int = DEFAULT_RUN_LIMIT,
    ): String {
        val selected = runsNewestFirst.take(limit.coerceAtLeast(0))
        return buildString {
            appendLine(
                "You are an experienced running coach. Please review my recent running " +
                    "journal aggregates and give practical training advice " +
                    "(training load, recovery, pacing, heart-rate efficiency, cadence, " +
                    "and progression). Ask clarifying questions if you need context " +
                    "(goals, injuries, weekly schedule).",
            )
            appendLine()
            appendLine(
                "Data notes: these are per-run summary metrics only (no GPS track or " +
                    "heart-rate time series). Heartbits/km = average heart rate × pace " +
                    "in minutes per km (lower is more efficient). Cadence is steps per " +
                    "minute. VO₂ max prefers the band estimate when present, otherwise " +
                    "an ACSM pace + HR approximation.",
            )
            appendLine()

            if (selected.isEmpty()) {
                appendLine("## Recent runs")
                appendLine()
                appendLine("No runs are available in the journal yet.")
            } else {
                appendLine("## Last ${selected.size} run${if (selected.size == 1) "" else "s"} (newest first)")
                appendLine()
                appendLastTenSummary(selected)
                appendLine()
                selected.forEachIndexed { index, run ->
                    appendRun(index + 1, run, zoneId)
                    if (index < selected.lastIndex) appendLine()
                }
            }

            appendLine()
            appendLine("## Trailing 365-day averages (all journal runs in window)")
            appendLine()
            appendLine(
                "- Average cadence: ${
                    trailingYearAverages.avgCadenceSpm?.let { "%.0f spm".format(it) } ?: "—"
                }",
            )
            appendLine(
                "- Average heartbits/km: ${
                    trailingYearAverages.avgHeartbitsPerKm?.let { "%.0f".format(it) } ?: "—"
                }",
            )
            appendLine()
            append(
                "Please respond as my running trainer: what stands out, what to keep, " +
                    "and what to change next.",
            )
        }
    }

    private fun StringBuilder.appendLastTenSummary(runs: List<RunSummaryForPrompt>) {
        val distances = runs.map { it.distanceMeters }.filter { it > 0 }
        val paces = runs.map { it.paceSecPerKm }.filter { it > 0 }
        val avgBpms = runs.map { it.avgBpm }.filter { it > 0 }
        val cadences = runs.map { it.cadenceSpm }.filter { it > 0 }
        val heartbits = runs.map { it.heartbitsPerKm }.filter { it > 0 }
        val vo2s = runs.map { it.vo2MaxMlKgMin }.filter { it > 0 }
        val totalDuration = runs.sumOf { it.durationSec.coerceAtLeast(0) }
        val totalCalories = runs.map { it.calories }.filter { it > 0 }.sum()

        appendLine("### Snapshot across these runs")
        appendLine(
            "- Total distance: ${
                if (distances.isEmpty()) "—" else RunningMetrics.formatDistanceKm(distances.sum())
            }",
        )
        appendLine("- Total duration: ${formatDuration(totalDuration)}")
        appendLine(
            "- Average pace: ${
                if (paces.isEmpty()) "—" else "${RunningMetrics.formatPace(paces.average())} /km"
            }",
        )
        appendLine(
            "- Average heart rate: ${
                if (avgBpms.isEmpty()) "—" else "${avgBpms.average().roundToInt()} bpm"
            }",
        )
        appendLine(
            "- Average cadence: ${
                if (cadences.isEmpty()) "—" else "%.0f spm".format(cadences.average())
            }",
        )
        appendLine(
            "- Average heartbits/km: ${
                if (heartbits.isEmpty()) "—" else "%.0f".format(heartbits.average())
            }",
        )
        appendLine(
            "- Average VO₂ max: ${
                if (vo2s.isEmpty()) {
                    "—"
                } else {
                    "${RunningMetrics.formatVo2Max(vo2s.average())} ml/kg/min"
                }
            }",
        )
        if (totalCalories > 0) {
            appendLine("- Total calories: ${totalCalories.roundToInt()}")
        }
    }

    private fun StringBuilder.appendRun(
        number: Int,
        run: RunSummaryForPrompt,
        zoneId: ZoneId,
    ) {
        val date = dateFormatter
            .withZone(zoneId)
            .format(Instant.ofEpochSecond(run.startTimeEpochSec))
        appendLine("### Run $number — $date · ${sportLabel(run.sportType)}")
        appendLine("- Distance: ${RunningMetrics.formatDistanceKm(run.distanceMeters)}")
        appendLine("- Duration: ${formatDuration(run.durationSec)}")
        appendLine("- Pace (temp): ${RunningMetrics.formatPace(run.paceSecPerKm)} /km")
        appendLine("- Avg heart rate: ${if (run.avgBpm > 0) "${run.avgBpm} bpm" else "—"}")
        appendLine("- Max heart rate: ${if (run.maxBpm > 0) "${run.maxBpm} bpm" else "—"}")
        appendLine(
            "- Heartbits/km: ${
                run.heartbitsPerKm.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "—"
            }",
        )
        appendLine(
            "- Cadence: ${
                run.cadenceSpm.takeIf { it > 0 }?.let { "%.0f spm".format(it) } ?: "—"
            }",
        )
        appendLine(
            "- VO₂ max: ${
                run.vo2MaxMlKgMin.takeIf { it > 0 }
                    ?.let { "${RunningMetrics.formatVo2Max(it)} ml/kg/min" }
                    ?: "—"
            }",
        )
        appendLine(
            "- Calories: ${
                run.calories.takeIf { it > 0 }?.let { it.roundToInt().toString() } ?: "—"
            }",
        )
    }

    fun sportLabel(sportType: String): String = when (sportType) {
        "outdoor_running" -> "Outdoor"
        "treadmill" -> "Treadmill"
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
