package com.tepmex.zuotasks.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TreeNodeDao {

    @Query(
        """
        SELECT * FROM tree_nodes
        WHERE parentId IS :parentId
        AND (:showHidden = 1 OR isHidden = 0)
        ORDER BY sortOrder ASC, id ASC
        """,
    )
    fun observeChildren(parentId: Long?, showHidden: Boolean): Flow<List<TreeNodeEntity>>

    @Query("SELECT * FROM tree_nodes WHERE id = :id")
    suspend fun getById(id: Long): TreeNodeEntity?

    @Query("SELECT * FROM tree_nodes WHERE id = :id")
    fun observeById(id: Long): Flow<TreeNodeEntity?>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM tree_nodes WHERE parentId IS :parentId")
    suspend fun nextSortOrder(parentId: Long?): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(node: TreeNodeEntity): Long

    @Update
    suspend fun update(node: TreeNodeEntity)

    @Query("UPDATE tree_nodes SET isCompleted = :completed WHERE id = :id AND type = 'TASK'")
    suspend fun setTaskCompleted(id: Long, completed: Boolean)

    @Query("UPDATE tree_nodes SET isHidden = 1 WHERE id = :id AND type = 'PROJECT'")
    suspend fun hideProject(id: Long)

    @Query(
        """
        UPDATE tree_nodes
        SET subtreeTaskCount = subtreeTaskCount + :taskDelta,
            subtreeCompletedCount = subtreeCompletedCount + :completedDelta
        WHERE id = :id AND type = 'PROJECT'
        """,
    )
    suspend fun adjustSubtreeCounts(id: Long, taskDelta: Int, completedDelta: Int)

    @Query("SELECT * FROM tree_nodes ORDER BY id ASC")
    suspend fun getAll(): List<TreeNodeEntity>

    @Query("DELETE FROM tree_nodes")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<TreeNodeEntity>)
}
