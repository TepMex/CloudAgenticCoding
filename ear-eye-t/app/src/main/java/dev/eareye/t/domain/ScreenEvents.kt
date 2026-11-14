package dev.eareye.t.domain

enum class ScreenEventType(val code: Int) {
    SCREEN_INTERACTIVE(1),
    SCREEN_NON_INTERACTIVE(2),
    KEYGUARD_SHOWN(3),
    KEYGUARD_HIDDEN(4),
    DEVICE_SHUTDOWN(5),
    DEVICE_STARTUP(6),
    ;

    companion object {
        fun fromCode(code: Int): ScreenEventType? = entries.firstOrNull { it.code == code }
    }
}

data class ScreenEvent(
    val timestampMillis: Long,
    val type: ScreenEventType,
    val ordinal: Int,
)

/**
 * Number events that share a millisecond in the order they were observed.
 * The ordinal is what keeps SCREEN_NON_INTERACTIVE and a later SCREEN_INTERACTIVE
 * at the same timestamp from being reordered by event-type code.
 */
fun withStableOrdinals(events: List<Pair<Long, ScreenEventType>>): List<ScreenEvent> {
    val counts = mutableMapOf<Long, Int>()
    return events.map { (timestamp, type) ->
        val ordinal = counts.getOrDefault(timestamp, 0)
        counts[timestamp] = ordinal + 1
        ScreenEvent(timestamp, type, ordinal)
    }
}

/**
 * Replace events inside [range] with [incoming]. Events outside the range stay.
 * A second call with the same inputs does not create duplicates.
 */
fun applyScreenImport(
    existing: List<ScreenEvent>,
    range: TimeInterval,
    incoming: List<ScreenEvent>,
): List<ScreenEvent> {
    val kept = existing.filter { event ->
        event.timestampMillis < range.startMillis || event.timestampMillis >= range.endMillis
    }
    val fresh = incoming.filter { event ->
        event.timestampMillis >= range.startMillis && event.timestampMillis < range.endMillis
    }
    return (kept + fresh).sortedWith(compareBy({ it.timestampMillis }, { it.ordinal }, { it.type.code }))
}
