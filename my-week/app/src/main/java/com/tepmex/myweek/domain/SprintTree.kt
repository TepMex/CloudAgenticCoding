package com.tepmex.myweek.domain

import java.time.LocalDate

fun List<SprintJoinRow>.toSprints(): List<Sprint> {
    if (isEmpty()) return emptyList()
    return groupBy { it.sprintId }
        .values
        .map { rows ->
            val head = rows.first()
            val blocks = rows
                .filter { it.blockId != null }
                .groupBy { it.blockId }
                .values
                .map { blockRows ->
                    val block = blockRows.first()
                    Block(
                        id = block.blockId!!,
                        title = block.blockTitle.orEmpty(),
                        colorKey = Palette.sanitize(block.colorKey ?: ""),
                        position = block.blockPosition ?: 0,
                        tasks = blockRows
                            .filter { it.taskId != null }
                            .distinctBy { it.taskId }
                            .sortedBy { it.taskPosition ?: 0 }
                            .map { row ->
                                Task(
                                    id = row.taskId!!,
                                    emoji = row.emoji.orEmpty(),
                                    title = row.taskTitle.orEmpty(),
                                    done = row.done == true,
                                    position = row.taskPosition ?: 0,
                                )
                            },
                    )
                }
                .sortedBy { it.position }
            Sprint(
                id = head.sprintId,
                weekStart = LocalDate.ofEpochDay(head.weekStartEpochDay),
                archived = head.archived,
                blocks = blocks,
            )
        }
        .sortedBy { it.weekStart }
}
