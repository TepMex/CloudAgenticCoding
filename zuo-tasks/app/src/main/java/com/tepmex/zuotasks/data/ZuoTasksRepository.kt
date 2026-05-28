package com.tepmex.zuotasks.data

import com.tepmex.zuotasks.domain.TreeNodeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ZuoTasksRepository(
    private val treeDao: TreeNodeDao,
    private val regularDao: RegularTaskDao,
) {
    fun observeProjectChildren(parentId: Long?, showHidden: Boolean): Flow<List<TreeNodeItem>> =
        treeDao.observeChildren(parentId, showHidden).map { list ->
            list.map(TreeNodeItem::from)
        }

    fun observeProject(parentId: Long): Flow<TreeNodeItem?> =
        treeDao.observeById(parentId).map { entity ->
            entity?.let(TreeNodeItem::from)
        }

    suspend fun addProject(parentId: Long?, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val sortOrder = treeDao.nextSortOrder(parentId)
        treeDao.insert(
            TreeNodeEntity(
                parentId = parentId,
                type = NodeType.PROJECT,
                name = trimmed,
                sortOrder = sortOrder,
            ),
        )
    }

    suspend fun addTask(parentId: Long?, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val sortOrder = treeDao.nextSortOrder(parentId)
        treeDao.insert(
            TreeNodeEntity(
                parentId = parentId,
                type = NodeType.TASK,
                name = trimmed,
                sortOrder = sortOrder,
            ),
        )
        bubbleSubtreeDelta(parentId, taskDelta = 1, completedDelta = 0)
    }

    suspend fun toggleTask(taskId: Long) {
        val node = treeDao.getById(taskId) ?: return
        if (node.type != NodeType.TASK) return
        val newCompleted = !node.isCompleted
        treeDao.setTaskCompleted(taskId, newCompleted)
        val delta = if (newCompleted) 1 else -1
        bubbleSubtreeDelta(node.parentId, taskDelta = 0, completedDelta = delta)
    }

    suspend fun hideProject(projectId: Long) {
        val node = treeDao.getById(projectId) ?: return
        if (node.type != NodeType.PROJECT) return
        treeDao.hideProject(projectId)
    }

    private suspend fun bubbleSubtreeDelta(
        startParentId: Long?,
        taskDelta: Int,
        completedDelta: Int,
    ) {
        if (taskDelta == 0 && completedDelta == 0) return
        var parentId = startParentId
        while (parentId != null) {
            treeDao.adjustSubtreeCounts(parentId, taskDelta, completedDelta)
            parentId = treeDao.getById(parentId)?.parentId
        }
    }

    fun observeRegularTasks(): Flow<List<RegularTaskEntity>> = regularDao.observeAll()

    suspend fun addRegularTask(name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty())
        val sortOrder = regularDao.nextSortOrder()
        regularDao.insert(RegularTaskEntity(name = trimmed, sortOrder = sortOrder))
    }

    suspend fun markRegularTaskPerformed(id: Long, timestamp: Long = System.currentTimeMillis()) {
        regularDao.setLastPerformed(id, timestamp)
    }

    suspend fun deleteRegularTask(id: Long) {
        regularDao.delete(id)
    }

    suspend fun exportBackup(): String = BackupCodec.encode(
        treeDao.getAll(),
        regularDao.getAll(),
    )

    suspend fun importBackup(text: String) {
        val (tree, regular) = BackupCodec.decode(text)
        treeDao.deleteAll()
        regularDao.deleteAll()
        if (tree.isNotEmpty()) treeDao.insertAll(tree)
        if (regular.isNotEmpty()) regularDao.insertAll(regular)
    }
}
