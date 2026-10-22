package com.tepmex.myweek.domain

object Palette {
    val ids: List<String> = listOf(
        "blue",
        "teal",
        "green",
        "amber",
        "orange",
        "rose",
        "violet",
        "slate",
    )

    fun sanitize(id: String): String = if (id in ids) id else ids.first()

    fun next(used: Collection<String>): String {
        val taken = used.toSet()
        return ids.firstOrNull { it !in taken } ?: ids[used.size % ids.size]
    }
}
