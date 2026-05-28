package com.tepmex.zuotasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "regular_tasks",
    indices = [Index(value = ["sortOrder"])],
)
data class RegularTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lastPerformedAt: Long? = null,
    val sortOrder: Int = 0,
)
