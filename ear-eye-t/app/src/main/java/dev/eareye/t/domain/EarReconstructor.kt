package dev.eareye.t.domain

data class AudioTransitionRecord(
    val sessionId: Long,
    val wallMillis: Long,
    val elapsedMillis: Long,
    val bootCount: Int,
    val active: Boolean,
)

data class SessionRecord(
    val sessionId: Long,
    val bootCount: Int,
    val connectedWallMillis: Long,
    val connectedElapsedMillis: Long,
    val lastConfirmedWallMillis: Long,
    val lastConfirmedElapsedMillis: Long,
)

data class LiveObservation(
    val sessionId: Long,
    val wallMillis: Long,
    val elapsedMillis: Long,
)

data class EarReconstruction(
    val active: List<TimeInterval>,
    val coverage: List<TimeInterval>,
)

/**
 * Ear intervals are placed on the wall clock using the session's monotonic anchor:
 * wall = connectedWall + (elapsed - connectedElapsed). A stored wall timestamp that
 * jumped because the user changed the clock does not stretch the interval.
 *
 * An open session extends to [live] only when that sample is within
 * [maxContinuousGapMillis] of the last confirmation. A dead session ends at its
 * last confirmation. The gap after that is not coverage and must not be treated
 * as continued audio or as silence.
 */
fun reconstructEar(
    sessions: List<SessionRecord>,
    transitions: List<AudioTransitionRecord>,
    live: LiveObservation? = null,
    maxContinuousGapMillis: Long = ObservationPolicy.MAX_CONTINUOUS_GAP_MILLIS,
): EarReconstruction {
    val bySession = transitions.groupBy { it.sessionId }
    val active = mutableListOf<TimeInterval>()
    val coverage = mutableListOf<TimeInterval>()
    for (session in sessions) {
        val endElapsed = observationEndElapsed(session, live, maxContinuousGapMillis)
        if (endElapsed < session.connectedElapsedMillis) continue
        val startWall = session.connectedWallMillis
        val endWall = startWall + (endElapsed - session.connectedElapsedMillis)
        if (endWall > startWall) coverage += TimeInterval(startWall, endWall)
        val events = bySession[session.sessionId]
            .orEmpty()
            .asSequence()
            .filter { it.bootCount == session.bootCount }
            .filter { it.elapsedMillis in session.connectedElapsedMillis..endElapsed }
            .sortedBy { it.elapsedMillis }
            .toList()
        var on = false
        var onElapsed: Long? = null
        for (event in events) {
            if (event.active && !on) {
                on = true
                onElapsed = event.elapsedMillis
            } else if (!event.active && on) {
                val from = onElapsed
                if (from != null && event.elapsedMillis > from) {
                    active += TimeInterval(
                        startWall + (from - session.connectedElapsedMillis),
                        startWall + (event.elapsedMillis - session.connectedElapsedMillis),
                    )
                }
                on = false
                onElapsed = null
            }
        }
        val from = onElapsed
        if (on && from != null && endElapsed > from) {
            active += TimeInterval(
                startWall + (from - session.connectedElapsedMillis),
                startWall + (endElapsed - session.connectedElapsedMillis),
            )
        }
    }
    return EarReconstruction(
        active = mergeIntervals(active),
        coverage = mergeIntervals(coverage),
    )
}

fun observationEndElapsed(
    session: SessionRecord,
    live: LiveObservation?,
    maxContinuousGapMillis: Long,
): Long {
    val confirmed = session.lastConfirmedElapsedMillis
    if (live != null && live.sessionId == session.sessionId) {
        val gap = live.elapsedMillis - confirmed
        if (gap >= 0 && gap <= maxContinuousGapMillis) return live.elapsedMillis
    }
    return confirmed
}
