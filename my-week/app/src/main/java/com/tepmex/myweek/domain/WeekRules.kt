package com.tepmex.myweek.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Monday is the only planning day, and it ends at local midnight (the date becomes Tuesday).
 * A later Monday archives the open sprint and copies its structure with ticks cleared.
 * Opening the app after Monday still performs that rollover, but planning stays closed.
 */
object WeekRules {
    fun weekStart(today: LocalDate): LocalDate =
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun canPlan(today: LocalDate, weekStart: LocalDate, archived: Boolean): Boolean =
        !archived && today.dayOfWeek == DayOfWeek.MONDAY && weekStart == weekStart(today)

    fun canCheck(today: LocalDate, weekStart: LocalDate, archived: Boolean): Boolean =
        !archived && weekStart == weekStart(today)

    fun phase(today: LocalDate, sprint: Sprint?): WeekPhase = when {
        sprint == null -> WeekPhase.NoSprint
        canPlan(today, sprint.weekStart, sprint.archived) -> WeekPhase.Planning
        else -> WeekPhase.Tracking
    }

    fun plan(headers: List<SprintHeader>, today: LocalDate): ReconcilePlan {
        val monday = weekStart(today)
        val archiveIds = headers
            .filter { !it.archived && it.weekStart < monday }
            .map { it.id }
            .sorted()
        val current = headers.firstOrNull { it.weekStart == monday }
        val unarchiveId = current?.takeIf { it.archived }?.id
        val create = if (current == null) {
            val past = headers.filter { it.weekStart < monday }
            if (past.isNotEmpty() || today.dayOfWeek == DayOfWeek.MONDAY) {
                val template = past.maxWithOrNull(compareBy({ it.weekStart }, { it.id }))
                NewSprint(monday, template?.id)
            } else {
                null
            }
        } else {
            null
        }
        return ReconcilePlan(archiveIds, unarchiveId, create)
    }

    /** Blocks in position order. Ticks are dropped; the caller stores new rows as not done. */
    fun carry(sprint: Sprint?): List<BlockDraft> {
        if (sprint == null) return emptyList()
        return sprint.blocks
            .sortedBy { it.position }
            .map { block ->
                BlockDraft(
                    title = block.title,
                    colorKey = Palette.sanitize(block.colorKey),
                    tasks = block.tasks
                        .sortedBy { it.position }
                        .map { task -> TaskDraft(task.emoji, task.title) },
                )
            }
    }

    /** Oldest week first, so the newest sprint is the last row in the archive feed. */
    fun archiveFeed(sprints: List<Sprint>): List<Sprint> =
        sprints.filter { it.archived }.sortedBy { it.weekStart }
}
