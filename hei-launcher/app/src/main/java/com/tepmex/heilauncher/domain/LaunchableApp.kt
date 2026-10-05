package com.tepmex.heilauncher.domain

import java.text.Collator

data class LaunchableApp(
    val component: String,
    val packageName: String,
    val label: String,
)

fun sortApps(
    apps: List<LaunchableApp>,
    collator: Collator = Collator.getInstance(),
): List<LaunchableApp> {
    return apps.sortedWith { a, b ->
        val byScript = scriptBucket(a.label).compareTo(scriptBucket(b.label))
        if (byScript != 0) {
            byScript
        } else {
            val byLabel = collator.compare(a.label, b.label)
            if (byLabel != 0) byLabel else a.component.compareTo(b.component)
        }
    }
}

fun resolveFavourites(order: List<String>, apps: List<LaunchableApp>): List<LaunchableApp> {
    if (order.isEmpty() || apps.isEmpty()) return emptyList()
    val byId = apps.associateBy { it.component }
    return order.mapNotNull { byId[it] }
}

fun toggleFavourite(ids: List<String>, component: String): List<String> {
    return if (component in ids) ids.filterNot { it == component } else ids + component
}

fun moveFavourite(ids: List<String>, component: String, delta: Int): List<String> {
    val index = ids.indexOf(component)
    if (index < 0 || delta == 0) return ids
    val target = (index + delta).coerceIn(0, ids.lastIndex)
    if (target == index) return ids
    val mutable = ids.toMutableList()
    val item = mutable.removeAt(index)
    mutable.add(target, item)
    return mutable
}
