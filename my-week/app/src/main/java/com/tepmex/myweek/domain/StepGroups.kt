package com.tepmex.myweek.domain

data class StepStanding(
    val rating: Double,
    val comparisonCount: Int,
)

data class StepOption(
    val key: String,
    val emoji: String,
    val label: String,
    val openCount: Int,
    val rating: Double,
    val comparisonCount: Int,
)

object StepGroups {
    fun key(emoji: String, title: String): String =
        "${TaskText.emoji(emoji)}\u0000${StepNames.label(title)}"

    /**
     * One option per emoji plus normalized description. A group stays while any
     * of its tasks this week is still open. Finished tasks add nothing: if
     * "Книга 1" is done and "Книга 2" is not, "Книга" remains.
     */
    fun active(tasks: List<Task>, standings: Map<String, StepStanding> = emptyMap()): List<StepOption> {
        val grouped = tasks.groupBy { key(it.emoji, it.title) }
        return grouped.mapNotNull { (key, members) ->
            val open = members.filterNot { it.done }
            if (open.isEmpty()) return@mapNotNull null
            val sample = open.first()
            val standing = standings[key]
            StepOption(
                key = key,
                emoji = TaskText.emoji(sample.emoji),
                label = StepNames.label(sample.title),
                openCount = open.size,
                rating = standing?.rating ?: StepElo.INITIAL,
                comparisonCount = standing?.comparisonCount ?: 0,
            )
        }.sortedWith(
            compareByDescending<StepOption> { it.rating }
                .thenBy { it.label.lowercase() }
                .thenBy { it.emoji },
        )
    }
}
