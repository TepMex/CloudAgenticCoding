package dev.eareye.t.domain

data class EyeReconstruction(
    val active: List<TimeInterval>,
    val unknown: List<TimeInterval>,
)

/**
 * Reconstruct eye-time from a chronological event stream.
 *
 * Eye time is active only when screenInteractive is known true AND keyguardShowing
 * is known false. Missing either flag is unknown, not "unlocked" and not "idle".
 * The current device state is never an input: callers must not invent history from
 * PowerManager or KeyguardManager.
 *
 * [trustedUntilMillis] is the exclusive end of the last successful UsageEvents query.
 * State is not extrapolated past it. A later permission loss leaves the tail unknown.
 *
 * DEVICE_STARTUP without a preceding DEVICE_SHUTDOWN invalidates the open known
 * segment back to the previous event: the moment the device actually went down was
 * not observed.
 */
fun reconstructEye(
    events: List<ScreenEvent>,
    window: TimeInterval,
    trustedUntilMillis: Long,
): EyeReconstruction {
    if (window.isEmpty()) return EyeReconstruction(emptyList(), emptyList())
    val horizon = minOf(window.endMillis, trustedUntilMillis)
    if (horizon <= window.startMillis) {
        return EyeReconstruction(emptyList(), listOf(window))
    }

    val sorted = events
        .asSequence()
        .filter { it.timestampMillis < trustedUntilMillis }
        .sortedWith(compareBy({ it.timestampMillis }, { it.ordinal }, { it.type.code }))
        .toList()

    var screenInteractive: Boolean? = null
    var keyguardShowing: Boolean? = null
    var eyeStart: Long? = null
    var unknownStart: Long? = null
    var lastEventTime: Long? = null
    var shutdownIsCurrentPowerBoundary = false
    val active = mutableListOf<TimeInterval>()
    val unknown = mutableListOf<TimeInterval>()

    fun known(): Boolean = screenInteractive != null && keyguardShowing != null
    fun eye(): Boolean = known() && screenInteractive == true && keyguardShowing == false

    fun closeEye(at: Long) {
        val start = eyeStart ?: return
        if (at > start) active += TimeInterval(start, at)
        eyeStart = null
    }

    fun closeUnknown(at: Long) {
        val start = unknownStart ?: return
        if (at > start) unknown += TimeInterval(start, at)
        unknownStart = null
    }

    fun invalidateSince(from: Long) {
        val rewritten = mutableListOf<TimeInterval>()
        for (interval in active) {
            if (interval.endMillis <= from) rewritten += interval
            else if (interval.startMillis < from) rewritten += TimeInterval(interval.startMillis, from)
        }
        active.clear()
        active += rewritten
        val open = eyeStart
        if (open != null && open < from) {
            active += TimeInterval(open, from)
        }
        eyeStart = null
        if (unknownStart == null || unknownStart!! > from) unknownStart = from
    }

    fun applyKnown(event: ScreenEvent) {
        when (event.type) {
            ScreenEventType.SCREEN_INTERACTIVE -> screenInteractive = true
            ScreenEventType.SCREEN_NON_INTERACTIVE -> screenInteractive = false
            ScreenEventType.KEYGUARD_SHOWN -> keyguardShowing = true
            ScreenEventType.KEYGUARD_HIDDEN -> keyguardShowing = false
            ScreenEventType.DEVICE_SHUTDOWN -> {
                screenInteractive = false
                keyguardShowing = true
                shutdownIsCurrentPowerBoundary = true
            }
            ScreenEventType.DEVICE_STARTUP -> Unit
        }
    }

    for (event in sorted) {
        if (event.timestampMillis >= window.startMillis) break
        if (event.type == ScreenEventType.DEVICE_STARTUP) {
            if (!shutdownIsCurrentPowerBoundary) {
                screenInteractive = null
                keyguardShowing = null
            } else {
                screenInteractive = null
                keyguardShowing = null
            }
            shutdownIsCurrentPowerBoundary = false
        } else {
            applyKnown(event)
        }
        lastEventTime = event.timestampMillis
    }

    if (!known()) unknownStart = window.startMillis else if (eye()) eyeStart = window.startMillis

    for (event in sorted) {
        if (event.timestampMillis < window.startMillis) continue
        if (event.timestampMillis >= horizon) break
        if (event.type == ScreenEventType.DEVICE_STARTUP) {
            if (!shutdownIsCurrentPowerBoundary) {
                invalidateSince(lastEventTime ?: window.startMillis)
            } else {
                closeEye(event.timestampMillis)
            }
            screenInteractive = null
            keyguardShowing = null
            shutdownIsCurrentPowerBoundary = false
            if (unknownStart == null) unknownStart = event.timestampMillis
            lastEventTime = event.timestampMillis
            continue
        }

        val wasKnown = known()
        val wasEye = eye()
        applyKnown(event)
        val nowKnown = known()
        val nowEye = eye()
        if (wasKnown && !nowKnown) {
            closeEye(event.timestampMillis)
            if (unknownStart == null) unknownStart = event.timestampMillis
        } else if (!wasKnown && nowKnown) {
            closeUnknown(event.timestampMillis)
            if (nowEye) eyeStart = event.timestampMillis
        } else if (wasKnown && nowKnown) {
            if (wasEye && !nowEye) closeEye(event.timestampMillis)
            else if (!wasEye && nowEye && eyeStart == null) eyeStart = event.timestampMillis
        }
        lastEventTime = event.timestampMillis
    }

    closeEye(horizon)
    closeUnknown(horizon)
    if (horizon < window.endMillis) unknown += TimeInterval(horizon, window.endMillis)
    val windowClip = TimeInterval(window.startMillis, window.endMillis)
    return EyeReconstruction(
        active = mergeIntervals(clipIntervals(active, windowClip)),
        unknown = mergeIntervals(clipIntervals(unknown, windowClip)),
    )
}

/**
 * Wall-clock spans bracketed by DEVICE_SHUTDOWN and a later DEVICE_STARTUP
 * with no contradictory screen event between them. The device cannot play
 * audio while it is off. An unclosed shutdown is not extended to "now":
 * the missing startup would hide real playback.
 */
fun poweredOffIntervals(
    events: List<ScreenEvent>,
    window: TimeInterval,
    trustedUntilMillis: Long,
): List<TimeInterval> {
    if (window.isEmpty()) return emptyList()
    val horizon = minOf(window.endMillis, trustedUntilMillis)
    val sorted = events
        .asSequence()
        .filter { it.timestampMillis < trustedUntilMillis && it.timestampMillis < horizon }
        .sortedWith(compareBy({ it.timestampMillis }, { it.ordinal }, { it.type.code }))
        .toList()
    val result = mutableListOf<TimeInterval>()
    var offStart: Long? = null
    var contradicted = false
    for (event in sorted) {
        when (event.type) {
            ScreenEventType.DEVICE_SHUTDOWN -> {
                offStart = event.timestampMillis
                contradicted = false
            }
            ScreenEventType.DEVICE_STARTUP -> {
                val start = offStart
                if (start != null && !contradicted && event.timestampMillis > start) {
                    result += TimeInterval(start, event.timestampMillis)
                }
                offStart = null
                contradicted = false
            }
            else -> if (offStart != null) contradicted = true
        }
    }
    return mergeIntervals(clipIntervals(result, window))
}
