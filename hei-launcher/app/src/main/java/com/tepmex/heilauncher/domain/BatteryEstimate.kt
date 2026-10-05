package com.tepmex.heilauncher.domain

data class BatteryStatus(
    val percent: Int,
    val charging: Boolean,
    val remainingMillis: Long?,
)

/** µAh remaining divided by |µA| draw. Null when the reading is unusable. */
fun estimateRemainingMillis(chargeMicroAmpHours: Long, currentMicroAmps: Long): Long? {
    if (chargeMicroAmpHours <= 0L || currentMicroAmps == 0L || currentMicroAmps == Long.MIN_VALUE) {
        return null
    }
    val drainMicroAmps = kotlin.math.abs(currentMicroAmps)
    if (drainMicroAmps < 1_000L) return null
    val hours = chargeMicroAmpHours.toDouble() / drainMicroAmps.toDouble()
    if (hours < 0.05 || hours > 200.0) return null
    return (hours * 3_600_000.0).toLong()
}

fun formatBattery(status: BatteryStatus): String {
    val percent = "${status.percent.coerceIn(0, 100)}%"
    val remaining = status.remainingMillis
    if (status.charging || remaining == null || remaining <= 0L) return percent
    val minutes = remaining / 60_000L
    val extra = when {
        minutes < 1 -> null
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h"
    }
    return if (extra == null) percent else "$percent $extra"
}
