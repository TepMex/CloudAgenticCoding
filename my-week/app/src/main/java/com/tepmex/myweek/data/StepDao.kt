package com.tepmex.myweek.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StepDao {
    @Query("SELECT * FROM step_ratings")
    fun observeRatings(): Flow<List<StepRatingEntity>>

    @Query("SELECT * FROM step_ratings")
    suspend fun ratings(): List<StepRatingEntity>

    @Query("SELECT * FROM step_ratings WHERE optionKey = :key")
    suspend fun rating(key: String): StepRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: StepRatingEntity)

    @Insert
    suspend fun insertComparison(entity: StepComparisonEntity)

    @Query("SELECT * FROM step_comparisons ORDER BY comparedAt DESC, id DESC LIMIT 1")
    suspend fun latestComparison(): StepComparisonEntity?

    @Query("SELECT pairKey FROM step_comparisons ORDER BY comparedAt DESC, id DESC LIMIT 20")
    suspend fun recentPairKeys(): List<String>

    @Query("SELECT pairKey AS pairKey, COUNT(*) AS n FROM step_comparisons GROUP BY pairKey")
    suspend fun headToHead(): List<PairCount>

    @Query("SELECT COUNT(*) FROM step_comparisons")
    fun observeComparisonCount(): Flow<Int>

    @Query("DELETE FROM step_comparisons WHERE id = :id")
    suspend fun deleteComparison(id: Long)

    @Query("DELETE FROM step_ratings")
    suspend fun deleteAllRatings()

    @Query("DELETE FROM step_comparisons")
    suspend fun deleteAllComparisons()
}
