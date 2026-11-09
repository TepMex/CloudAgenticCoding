package com.tepmex.myweek.domain

import java.time.format.DateTimeFormatter
import java.util.Locale

/** Plain-text copy of one week's plan, with emoji and done / not-done status. */
object PlanText {
    private val date = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

    fun render(sprint: Sprint, done: String = "выполнено", notDone: String = "не выполнено"): String =
        buildString {
            appendLine(sprint.weekStart.format(date))
            sprint.blocks.forEachIndexed { index, block ->
                if (index > 0) appendLine()
                append(block.emoji)
                append(' ')
                appendLine(block.title)
                block.tasks.forEach { task ->
                    append(task.emoji)
                    append(' ')
                    append(task.title)
                    append(" — ")
                    appendLine(if (task.done) done else notDone)
                }
            }
        }.trimEnd()
}
