package com.tepmex.heilauncher.domain

/** Time since [lastUsedAt], or null when unknown. Matches `26m`, `1h 38m`, `3h`, `2d`. */
fun formatSince(lastUsedAt: Long?, now: Long): String? {
    if (lastUsedAt == null || lastUsedAt <= 0L) return null
    val delta = now - lastUsedAt
    if (delta < -60_000L) return null
    if (delta < 60_000L) return "<1m"
    val minutes = delta / 60_000L
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val rem = minutes % 60
    if (hours < 24) {
        return if (rem == 0L) "${hours}h" else "${hours}h ${rem}m"
    }
    return "${hours / 24}d"
}
