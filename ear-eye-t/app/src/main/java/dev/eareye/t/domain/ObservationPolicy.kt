package dev.eareye.t.domain

/**
 * Honesty parameters for observation. They are not accuracy guarantees.
 * A gap inside [MAX_CONTINUOUS_GAP_MILLIS] is treated as continuous observation
 * because Doze can delay the 5-minute heartbeat. A larger gap is unknown time,
 * not silence and not continued playback.
 */
object ObservationPolicy {
    const val HEARTBEAT_INTERVAL_MILLIS: Long = 5 * 60 * 1000L
    const val MAX_CONTINUOUS_GAP_MILLIS: Long = 30 * 60 * 1000L
    const val CLOCK_DISCONTINUITY_MILLIS: Long = 5_000L
    const val USAGE_SYNC_OVERLAP_MILLIS: Long = 60 * 60 * 1000L
    const val USAGE_INITIAL_LOOKBACK_MILLIS: Long = 7L * 24 * 60 * 60 * 1000L
    const val DIAGNOSTIC_LIMIT: Int = 100
}

enum class SessionEndReason {
    CLEAN,
    UNEXPECTED,
    GAP,
    CLOCK,
    REBOOT,
}

data class ClockSample(
    val wallMillis: Long,
    val elapsedMillis: Long,
    val bootCount: Int,
)

/**
 * Decide whether two successive observation samples still belong to one
 * continuous session. Elapsed time is the duration authority inside one boot.
 */
fun observationSplit(
    previous: ClockSample,
    next: ClockSample,
    maxContinuousGapMillis: Long = ObservationPolicy.MAX_CONTINUOUS_GAP_MILLIS,
    clockThresholdMillis: Long = ObservationPolicy.CLOCK_DISCONTINUITY_MILLIS,
): SessionEndReason? {
    if (previous.bootCount != next.bootCount) return SessionEndReason.REBOOT
    val elapsedDelta = next.elapsedMillis - previous.elapsedMillis
    if (elapsedDelta < 0) return SessionEndReason.CLOCK
    val wallDelta = next.wallMillis - previous.wallMillis
    if (kotlin.math.abs(wallDelta - elapsedDelta) > clockThresholdMillis) {
        return SessionEndReason.CLOCK
    }
    if (elapsedDelta > maxContinuousGapMillis) return SessionEndReason.GAP
    return null
}
