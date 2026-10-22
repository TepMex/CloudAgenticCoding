package com.tepmex.myweek.domain

import java.time.LocalDate

data class Sprint(
    val id: Long,
    val weekStart: LocalDate,
    val archived: Boolean,
    val blocks: List<Block>,
)

data class Block(
    val id: Long,
    val title: String,
    val colorKey: String,
    val position: Int,
    val tasks: List<Task>,
)

data class Task(
    val id: Long,
    val emoji: String,
    val title: String,
    val done: Boolean,
    val position: Int,
)

data class SprintHeader(
    val id: Long,
    val weekStart: LocalDate,
    val archived: Boolean,
)

data class NewSprint(
    val weekStart: LocalDate,
    val copyFromId: Long?,
)

data class ReconcilePlan(
    val archiveIds: List<Long> = emptyList(),
    val unarchiveId: Long? = null,
    val create: NewSprint? = null,
) {
    val isEmpty: Boolean
        get() = archiveIds.isEmpty() && unarchiveId == null && create == null
}

data class TaskDraft(
    val emoji: String,
    val title: String,
)

data class BlockDraft(
    val title: String,
    val colorKey: String,
    val tasks: List<TaskDraft>,
)

enum class WeekPhase {
    NoSprint,
    Planning,
    Tracking,
}

/** Flat Room projection. One row per task, or one row for a sprint/block that has no children. */
data class SprintJoinRow(
    val sprintId: Long,
    val weekStartEpochDay: Long,
    val archived: Boolean,
    val blockId: Long? = null,
    val blockTitle: String? = null,
    val colorKey: String? = null,
    val blockPosition: Int? = null,
    val taskId: Long? = null,
    val emoji: String? = null,
    val taskTitle: String? = null,
    val done: Boolean? = null,
    val taskPosition: Int? = null,
)
