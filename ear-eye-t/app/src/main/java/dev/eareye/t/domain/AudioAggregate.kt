package dev.eareye.t.domain

/**
 * Stable AudioAttributes usage values. The Android layer passes
 * AudioAttributes.getUsage() through unchanged.
 */
object AudioUsages {
    const val UNKNOWN: Int = 0
    const val MEDIA: Int = 1
    const val VOICE_COMMUNICATION: Int = 2
    const val ALARM: Int = 4
    const val NOTIFICATION: Int = 5
    const val RINGTONE: Int = 6
    const val ACCESSIBILITY: Int = 11
    const val NAVIGATION: Int = 12
    const val SONIFICATION: Int = 13
    const val GAME: Int = 14
    const val ASSISTANT: Int = 16
}

data class AudioCategorySelection(
    val media: Boolean = true,
    val game: Boolean = true,
    val assistant: Boolean = false,
    val navigation: Boolean = false,
    val accessibility: Boolean = false,
) {
    fun allows(usage: Int): Boolean = when (usage) {
        AudioUsages.MEDIA -> media
        AudioUsages.GAME -> game
        AudioUsages.ASSISTANT -> assistant
        AudioUsages.NAVIGATION -> navigation
        AudioUsages.ACCESSIBILITY -> accessibility
        else -> false
    }
}

fun qualifyingUsageCount(usages: List<Int>, selection: AudioCategorySelection): Int =
    usages.count { selection.allows(it) }

fun describeUsages(usages: List<Int>, selection: AudioCategorySelection): String {
    val qualifying = usages.filter { selection.allows(it) }
    val other = usages.filterNot { selection.allows(it) }
    return "подходящие=${qualifying.joinToString(",")}, прочие=${other.joinToString(",")}"
}

/**
 * Collapses simultaneous players into one aggregate. ACTIVE while the count
 * stays positive does not open a new interval. INACTIVE while already inactive
 * is ignored. The reducer is single-threaded; the monitor thread owns it.
 */
class AudioAggregateReducer {
    private var active: Boolean = false

    fun reset() {
        active = false
    }

    fun onCount(count: Int): Boolean? {
        val nowActive = count > 0
        if (nowActive == active) return null
        active = nowActive
        return nowActive
    }

    fun isActive(): Boolean = active
}
