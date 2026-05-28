package com.tepmex.zuotasks.domain

import com.tepmex.zuotasks.data.NodeType
import com.tepmex.zuotasks.data.TreeNodeEntity

data class TreeNodeItem(
    val id: Long,
    val parentId: Long?,
    val type: NodeType,
    val name: String,
    val isCompleted: Boolean,
    val isHidden: Boolean,
    val completionPercent: Int?,
) {
    val isProject: Boolean get() = type == NodeType.PROJECT
    val isTask: Boolean get() = type == NodeType.TASK

    companion object {
        fun from(entity: TreeNodeEntity): TreeNodeItem {
            val percent = if (entity.type == NodeType.PROJECT) {
                if (entity.subtreeTaskCount == 0) {
                    0
                } else {
                    ((entity.subtreeCompletedCount * 100L) / entity.subtreeTaskCount).toInt()
                }
            } else {
                null
            }
            return TreeNodeItem(
                id = entity.id,
                parentId = entity.parentId,
                type = entity.type,
                name = entity.name,
                isCompleted = entity.isCompleted,
                isHidden = entity.isHidden,
                completionPercent = percent,
            )
        }
    }
}
