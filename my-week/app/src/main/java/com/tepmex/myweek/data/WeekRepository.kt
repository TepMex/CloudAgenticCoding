package com.tepmex.myweek.data

import androidx.room.withTransaction
import com.tepmex.myweek.domain.BlockDraft
import com.tepmex.myweek.domain.Palette
import com.tepmex.myweek.domain.Sprint
import com.tepmex.myweek.domain.SprintHeader
import com.tepmex.myweek.domain.TaskText
import com.tepmex.myweek.domain.WeekRules
import com.tepmex.myweek.domain.toSprints
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WeekRepository(
    private val db: WeekDatabase,
    private val dao: WeekDao,
) {
    private val gate = Mutex()

    fun observeOpenWeek(today: LocalDate): Flow<Sprint?> =
        dao.observeWeek(WeekRules.weekStart(today).toEpochDay())
            .map { rows -> rows.toSprints().firstOrNull() }

    fun observeArchived(): Flow<List<Sprint>> =
        dao.observeArchived().map { rows -> WeekRules.archiveFeed(rows.toSprints()) }

    fun observeEmojiUsage(): Flow<Map<String, Int>> =
        dao.observeEmojiUsage().map { rows -> rows.associate { it.emoji to it.uses } }

    suspend fun emojiUsage(): Map<String, Int> =
        dao.emojiUsage().associate { it.emoji to it.uses }

    suspend fun reconcile(today: LocalDate) = gate.withLock {
        db.withTransaction {
            val headers = dao.headers().map { row ->
                SprintHeader(row.id, LocalDate.ofEpochDay(row.weekStartEpochDay), row.archived)
            }
            val plan = WeekRules.plan(headers, today)
            if (plan.isEmpty) return@withTransaction
            val drafts = plan.create?.copyFromId?.let { id ->
                WeekRules.carry(dao.loadSprintRows(id).toSprints().firstOrNull())
            }
            plan.archiveIds.forEach { id -> dao.setArchived(id, true) }
            plan.unarchiveId?.let { id -> dao.setArchived(id, false) }
            val create = plan.create
            if (create != null) {
                insertTree(create.weekStart, drafts.orEmpty())
            }
        }
    }

    suspend fun addBlock(
        sprintId: Long,
        title: String,
        colorKey: String,
        emoji: String,
        today: LocalDate,
    ): Boolean =
        gate.withLock {
            val sprint = dao.sprint(sprintId) ?: return@withLock false
            if (!allowsPlan(sprint, today)) return@withLock false
            val position = dao.maxBlockPosition(sprintId) + 1
            dao.insertBlock(
                BlockEntity(
                    sprintId = sprintId,
                    title = title,
                    colorKey = Palette.sanitize(colorKey),
                    emoji = TaskText.emoji(emoji),
                    position = position,
                ),
            )
            true
        }

    suspend fun updateBlock(
        blockId: Long,
        title: String,
        colorKey: String,
        emoji: String,
        today: LocalDate,
    ): Boolean =
        gate.withLock {
            val header = dao.headerForBlock(blockId) ?: return@withLock false
            if (!allowsPlan(header, today)) return@withLock false
            dao.updateBlock(blockId, title, Palette.sanitize(colorKey), TaskText.emoji(emoji))
            true
        }

    suspend fun deleteBlock(blockId: Long, today: LocalDate): Boolean = gate.withLock {
        val header = dao.headerForBlock(blockId) ?: return@withLock false
        if (!allowsPlan(header, today)) return@withLock false
        dao.deleteTasksForBlock(blockId)
        dao.deleteBlock(blockId)
        true
    }

    suspend fun addTask(blockId: Long, emoji: String, title: String, today: LocalDate): Boolean =
        gate.withLock {
            val header = dao.headerForBlock(blockId) ?: return@withLock false
            if (!allowsPlan(header, today)) return@withLock false
            val position = dao.maxTaskPosition(blockId) + 1
            dao.insertTask(
                TaskEntity(
                    blockId = blockId,
                    emoji = emoji,
                    title = title,
                    done = false,
                    position = position,
                ),
            )
            true
        }

    suspend fun updateTask(taskId: Long, emoji: String, title: String, today: LocalDate): Boolean =
        gate.withLock {
            val header = dao.headerForTask(taskId) ?: return@withLock false
            if (!allowsPlan(header, today)) return@withLock false
            dao.updateTask(taskId, emoji, title)
            true
        }

    suspend fun deleteTask(taskId: Long, today: LocalDate): Boolean = gate.withLock {
        val header = dao.headerForTask(taskId) ?: return@withLock false
        if (!allowsPlan(header, today)) return@withLock false
        dao.deleteTask(taskId)
        true
    }

    suspend fun setDone(taskId: Long, done: Boolean, today: LocalDate): Boolean = gate.withLock {
        val header = dao.headerForTask(taskId) ?: return@withLock false
        val weekStart = LocalDate.ofEpochDay(header.weekStartEpochDay)
        if (!WeekRules.canCheck(today, weekStart, header.archived)) return@withLock false
        dao.setDone(taskId, done)
        true
    }

    suspend fun clearAll() = gate.withLock {
        db.withTransaction {
            dao.deleteAllTasks()
            dao.deleteAllBlocks()
            dao.deleteAllSprints()
        }
    }

    private suspend fun insertTree(weekStart: LocalDate, blocks: List<BlockDraft>) {
        val sprintId = dao.insertSprint(
            SprintEntity(weekStartEpochDay = weekStart.toEpochDay(), archived = false),
        )
        blocks.forEachIndexed { index, block ->
            val blockId = dao.insertBlock(
                BlockEntity(
                    sprintId = sprintId,
                    title = block.title,
                    colorKey = Palette.sanitize(block.colorKey),
                    emoji = TaskText.emoji(block.emoji),
                    position = index,
                ),
            )
            block.tasks.forEachIndexed { taskIndex, task ->
                dao.insertTask(
                    TaskEntity(
                        blockId = blockId,
                        emoji = task.emoji,
                        title = task.title,
                        done = false,
                        position = taskIndex,
                    ),
                )
            }
        }
    }

    private fun allowsPlan(sprint: SprintEntity, today: LocalDate): Boolean =
        WeekRules.canPlan(today, LocalDate.ofEpochDay(sprint.weekStartEpochDay), sprint.archived)

    private fun allowsPlan(header: SprintHeaderEntity, today: LocalDate): Boolean =
        WeekRules.canPlan(today, LocalDate.ofEpochDay(header.weekStartEpochDay), header.archived)
}
