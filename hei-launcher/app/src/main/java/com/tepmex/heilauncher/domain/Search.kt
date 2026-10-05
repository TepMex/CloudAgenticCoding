package com.tepmex.heilauncher.domain

import java.text.Collator

/** Null when [query] matches neither the label nor the package. Lower is better. */
fun matchRank(app: LaunchableApp, query: String): Int? {
    val q = query.trim()
    if (q.isEmpty()) return 0
    if (app.label.startsWith(q, ignoreCase = true)) return 0
    if (words(app.label).any { it.startsWith(q, ignoreCase = true) }) return 1
    if (app.label.contains(q, ignoreCase = true)) return 2
    if (app.packageName.contains(q, ignoreCase = true)) return 3
    return null
}

fun filterApps(apps: List<LaunchableApp>, query: String): List<LaunchableApp> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    val collator = Collator.getInstance()
    return apps
        .mapNotNull { app ->
            val rank = matchRank(app, q) ?: return@mapNotNull null
            rank to app
        }
        .sortedWith { a, b ->
            val byRank = a.first.compareTo(b.first)
            if (byRank != 0) {
                byRank
            } else {
                val byLabel = collator.compare(a.second.label, b.second.label)
                if (byLabel != 0) byLabel else a.second.component.compareTo(b.second.component)
            }
        }
        .map { it.second }
}

private fun words(label: String): List<String> {
    return label.split(' ', '\u00A0', '-', '_', '.').filter { it.isNotEmpty() }
}
