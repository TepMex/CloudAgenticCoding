package com.tepmex.zuotasks.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Adjacency-list tree node. Projects cache [subtreeTaskCount] and [subtreeCompletedCount]
 * so completion percentage reads are O(1); updates bubble O(depth) on toggle/insert/delete.
 */
@Entity(
    tableName = "tree_nodes",
    indices = [
        Index(value = ["parentId"]),
        Index(value = ["parentId", "sortOrder"]),
        Index(value = ["isHidden"]),
    ],
)
data class TreeNodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val type: NodeType,
    val name: String,
    val isCompleted: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val subtreeTaskCount: Int = 0,
    val subtreeCompletedCount: Int = 0,
)
