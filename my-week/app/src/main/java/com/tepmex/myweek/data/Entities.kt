package com.tepmex.myweek.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sprints",
    indices = [Index(value = ["weekStartEpochDay"], unique = true)],
)
data class SprintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekStartEpochDay: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "blocks",
    foreignKeys = [
        ForeignKey(
            entity = SprintEntity::class,
            parentColumns = ["id"],
            childColumns = ["sprintId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sprintId", "position"])],
)
data class BlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sprintId: Long,
    val title: String,
    val colorKey: String,
    val emoji: String,
    val position: Int,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = BlockEntity::class,
            parentColumns = ["id"],
            childColumns = ["blockId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["blockId", "position"])],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val blockId: Long,
    val emoji: String,
    val title: String,
    val done: Boolean,
    val position: Int,
)

data class SprintHeaderEntity(
    val id: Long,
    val weekStartEpochDay: Long,
    val archived: Boolean,
)

data class EmojiUsage(
    val emoji: String,
    val uses: Int,
)
