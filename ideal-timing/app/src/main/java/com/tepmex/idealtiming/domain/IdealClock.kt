package com.tepmex.idealtiming.domain

/**
 * Four equal sectors of the 16-hour ideal day, numbered 1–4 clockwise from wake (12 o’clock).
 */
enum class DaySector(
    val index: Int,
    val labelRu: String,
    val labelEn: String,
) {
    HealthStrategy(
        index = 1,
        labelRu = "Здоровье и стратегия",
        labelEn = "Health & strategy",
    ),
    TacticsHandworkMorning(
        index = 2,
        labelRu = "Тактика и работа руками",
        labelEn = "Tactics & handwork",
    ),
    TacticsHandworkAfternoon(
        index = 3,
        labelRu = "Тактика и работа руками",
        labelEn = "Tactics & handwork",
    ),
    RestWindDown(
        index = 4,
        labelRu = "Отдых, декомпрессия и подготовка ко сну",
        labelEn = "Rest, decompress & wind-down",
    );

    companion object {
        fun fromIndex(zeroBased: Int): DaySector =
            entries[zeroBased.coerceIn(0, entries.lastIndex)]
    }
}

data class ClockReading(
    val wakeEpochSec: Long,
    val nowEpochSec: Long,
    val elapsedSec: Long,
    val progress: Float,
    val sector: DaySector,
    val frozenAtSixteenHours: Boolean,
) {
    val elapsedHours: Double get() = elapsedSec / 3600.0
}

object IdealClock {
    const val DAY_HOURS = 16
    const val SECTOR_HOURS = 4
    const val DAY_SECONDS = DAY_HOURS * 3600L

    /**
     * Map [nowEpochSec] relative to [wakeEpochSec] onto a 16-hour dial.
     * Elapsed time is clamped to `[0, 16h]` — never overflows past the freeze point.
     */
    fun reading(wakeEpochSec: Long, nowEpochSec: Long): ClockReading {
        val raw = nowEpochSec - wakeEpochSec
        val elapsed = raw.coerceIn(0L, DAY_SECONDS)
        val frozen = raw >= DAY_SECONDS
        val progress = if (DAY_SECONDS == 0L) 0f else (elapsed.toDouble() / DAY_SECONDS).toFloat()
        val sectorIndex = when {
            elapsed >= DAY_SECONDS -> 3
            else -> (elapsed / (SECTOR_HOURS * 3600L)).toInt().coerceIn(0, 3)
        }
        return ClockReading(
            wakeEpochSec = wakeEpochSec,
            nowEpochSec = nowEpochSec,
            elapsedSec = elapsed,
            progress = progress,
            sector = DaySector.fromIndex(sectorIndex),
            frozenAtSixteenHours = frozen,
        )
    }

    /** Sweep angle in degrees from 12 o’clock, clockwise. */
    fun pointerDegrees(progress: Float): Float = progress.coerceIn(0f, 1f) * 360f
}
