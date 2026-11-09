package com.tepmex.myweek.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "step_ratings")
data class StepRatingEntity(
    @PrimaryKey val optionKey: String,
    val emoji: String,
    val label: String,
    val rating: Double,
    val comparisonCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val skipCount: Int,
    val updatedAt: Long,
)

@Entity(tableName = "step_comparisons")
data class StepComparisonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val leftKey: String,
    val rightKey: String,
    val pairKey: String,
    val outcome: String,
    val leftRatingBefore: Double,
    val rightRatingBefore: Double,
    val leftRatingAfter: Double,
    val rightRatingAfter: Double,
    val comparedAt: Long,
)

data class PairCount(
    val pairKey: String,
    val n: Int,
)
