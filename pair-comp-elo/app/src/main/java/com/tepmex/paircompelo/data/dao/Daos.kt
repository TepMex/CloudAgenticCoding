package com.tepmex.paircompelo.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.tepmex.paircompelo.data.db.ItemComparisonEntity
import com.tepmex.paircompelo.data.db.ListComparisonEntity
import com.tepmex.paircompelo.data.db.PreferenceItemEntity
import com.tepmex.paircompelo.data.db.PreferenceListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceListDao {
    @Query("SELECT * FROM preference_lists WHERE archived_at IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActive(): Flow<List<PreferenceListEntity>>

    @Query("SELECT * FROM preference_lists WHERE archived_at IS NOT NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeArchived(): Flow<List<PreferenceListEntity>>

    @Query("SELECT * FROM preference_lists ORDER BY rating DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<PreferenceListEntity>>

    @Query("SELECT * FROM preference_lists WHERE id = :id")
    fun observeById(id: String): Flow<PreferenceListEntity?>

    @Query("SELECT * FROM preference_lists WHERE id = :id")
    suspend fun getById(id: String): PreferenceListEntity?

    @Query("SELECT * FROM preference_lists WHERE archived_at IS NULL")
    suspend fun getActive(): List<PreferenceListEntity>

    @Query("SELECT * FROM preference_lists")
    suspend fun getAll(): List<PreferenceListEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PreferenceListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PreferenceListEntity>)

    @Update
    suspend fun update(entity: PreferenceListEntity)

    @Update
    suspend fun updateAll(entities: List<PreferenceListEntity>)

    @Query("DELETE FROM preference_lists WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM preference_lists")
    suspend fun deleteAll()
}

@Dao
interface PreferenceItemDao {
    @Query(
        """
        SELECT * FROM preference_items
        WHERE list_id = :listId AND archived_at IS NULL
        ORDER BY sort_order ASC, name COLLATE NOCASE ASC
        """,
    )
    fun observeActiveByList(listId: String): Flow<List<PreferenceItemEntity>>

    @Query(
        """
        SELECT * FROM preference_items
        WHERE list_id = :listId
        ORDER BY sort_order ASC, name COLLATE NOCASE ASC
        """,
    )
    fun observeByList(listId: String): Flow<List<PreferenceItemEntity>>

    @Query("SELECT * FROM preference_items WHERE id = :id")
    fun observeById(id: String): Flow<PreferenceItemEntity?>

    @Query("SELECT * FROM preference_items WHERE id = :id")
    suspend fun getById(id: String): PreferenceItemEntity?

    @Query("SELECT * FROM preference_items WHERE list_id = :listId AND archived_at IS NULL")
    suspend fun getActiveByList(listId: String): List<PreferenceItemEntity>

    @Query("SELECT * FROM preference_items WHERE list_id = :listId")
    suspend fun getByList(listId: String): List<PreferenceItemEntity>

    @Query("SELECT * FROM preference_items")
    suspend fun getAll(): List<PreferenceItemEntity>

    @Query(
        """
        SELECT COUNT(*) FROM preference_items
        WHERE list_id = :listId AND archived_at IS NULL
        """,
    )
    fun observeActiveCount(listId: String): Flow<Int>

    @Query(
        """
        SELECT * FROM preference_items
        WHERE list_id = :listId AND archived_at IS NULL
        ORDER BY rating DESC, comparison_count DESC, name COLLATE NOCASE ASC
        LIMIT 1
        """,
    )
    suspend fun getTopRated(listId: String): PreferenceItemEntity?

    @Query(
        """
        SELECT * FROM preference_items
        WHERE list_id = :listId AND archived_at IS NULL
        ORDER BY rating DESC, comparison_count DESC, name COLLATE NOCASE ASC
        LIMIT 1
        """,
    )
    fun observeTopRated(listId: String): Flow<PreferenceItemEntity?>

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM preference_items WHERE list_id = :listId")
    suspend fun maxSortOrder(listId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: PreferenceItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<PreferenceItemEntity>)

    @Update
    suspend fun update(entity: PreferenceItemEntity)

    @Update
    suspend fun updateAll(entities: List<PreferenceItemEntity>)

    @Query("DELETE FROM preference_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM preference_items")
    suspend fun deleteAll()
}

@Dao
interface ItemComparisonDao {
    @Query(
        """
        SELECT * FROM item_comparisons
        WHERE list_id = :listId AND is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeByList(listId: String, limit: Int, offset: Int): Flow<List<ItemComparisonEntity>>

    @Query(
        """
        SELECT * FROM item_comparisons
        WHERE is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeAll(limit: Int, offset: Int): Flow<List<ItemComparisonEntity>>

    @Query("SELECT * FROM item_comparisons WHERE id = :id")
    suspend fun getById(id: String): ItemComparisonEntity?

    @Query(
        """
        SELECT * FROM item_comparisons
        WHERE list_id = :listId AND is_reverted = 0
        ORDER BY compared_at ASC
        """,
    )
    suspend fun getActiveByListChronological(listId: String): List<ItemComparisonEntity>

    @Query(
        """
        SELECT * FROM item_comparisons
        WHERE is_reverted = 0
        ORDER BY compared_at ASC
        """,
    )
    suspend fun getAllActiveChronological(): List<ItemComparisonEntity>

    @Query("SELECT * FROM item_comparisons ORDER BY compared_at ASC")
    suspend fun getAll(): List<ItemComparisonEntity>

    @Query(
        """
        SELECT * FROM item_comparisons
        WHERE list_id = :listId AND is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestByList(listId: String): ItemComparisonEntity?

    @Query(
        """
        SELECT COUNT(*) FROM item_comparisons
        WHERE list_id = :listId AND is_reverted = 0
        """,
    )
    fun observeCountByList(listId: String): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM item_comparisons
        WHERE list_id = :listId AND is_reverted = 0
          AND ((left_item_id = :a AND right_item_id = :b) OR (left_item_id = :b AND right_item_id = :a))
        """,
    )
    suspend fun headToHeadCount(listId: String, a: String, b: String): Int

    @Query(
        """
        SELECT * FROM item_comparisons
        WHERE list_id = :listId AND is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentByList(listId: String, limit: Int): List<ItemComparisonEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ItemComparisonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ItemComparisonEntity>)

    @Update
    suspend fun update(entity: ItemComparisonEntity)

    @Update
    suspend fun updateAll(entities: List<ItemComparisonEntity>)

    @Query("UPDATE item_comparisons SET is_reverted = 1 WHERE id = :id")
    suspend fun markReverted(id: String)

    @Query("DELETE FROM item_comparisons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM item_comparisons")
    suspend fun deleteAll()
}

@Dao
interface ListComparisonDao {
    @Query(
        """
        SELECT * FROM list_comparisons
        WHERE is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observeAll(limit: Int, offset: Int): Flow<List<ListComparisonEntity>>

    @Query("SELECT * FROM list_comparisons WHERE id = :id")
    suspend fun getById(id: String): ListComparisonEntity?

    @Query(
        """
        SELECT * FROM list_comparisons
        WHERE is_reverted = 0
        ORDER BY compared_at ASC
        """,
    )
    suspend fun getAllActiveChronological(): List<ListComparisonEntity>

    @Query("SELECT * FROM list_comparisons ORDER BY compared_at ASC")
    suspend fun getAll(): List<ListComparisonEntity>

    @Query(
        """
        SELECT * FROM list_comparisons
        WHERE is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLatest(): ListComparisonEntity?

    @Query(
        """
        SELECT * FROM list_comparisons
        WHERE is_reverted = 0
        ORDER BY compared_at DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecent(limit: Int): List<ListComparisonEntity>

    @Query(
        """
        SELECT COUNT(*) FROM list_comparisons
        WHERE is_reverted = 0
          AND ((left_list_id = :a AND right_list_id = :b) OR (left_list_id = :b AND right_list_id = :a))
        """,
    )
    suspend fun headToHeadCount(a: String, b: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ListComparisonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<ListComparisonEntity>)

    @Update
    suspend fun update(entity: ListComparisonEntity)

    @Update
    suspend fun updateAll(entities: List<ListComparisonEntity>)

    @Query("UPDATE list_comparisons SET is_reverted = 1 WHERE id = :id")
    suspend fun markReverted(id: String)

    @Query("DELETE FROM list_comparisons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM list_comparisons")
    suspend fun deleteAll()
}
