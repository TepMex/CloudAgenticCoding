package com.tepmex.paircompelo.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tepmex.paircompelo.domain.model.ComparisonOutcome

@Entity(
    tableName = "preference_lists",
    indices = [
        Index(value = ["archived_at"]),
        Index(value = ["rating"]),
        Index(value = ["updated_at"]),
    ],
)
data class PreferenceListEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "rating") val rating: Double,
    @ColumnInfo(name = "rating_updated_at") val ratingUpdatedAt: Long,
    @ColumnInfo(name = "comparison_count") val comparisonCount: Int,
)

@Entity(
    tableName = "preference_items",
    foreignKeys = [
        ForeignKey(
            entity = PreferenceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["list_id"]),
        Index(value = ["list_id", "archived_at"]),
        Index(value = ["list_id", "rating"]),
        Index(value = ["list_id", "sort_order"]),
        Index(value = ["archived_at"]),
    ],
)
data class PreferenceItemEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "list_id") val listId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String?,
    @ColumnInfo(name = "notes") val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "archived_at") val archivedAt: Long?,
    @ColumnInfo(name = "rating") val rating: Double,
    @ColumnInfo(name = "rating_updated_at") val ratingUpdatedAt: Long,
    @ColumnInfo(name = "comparison_count") val comparisonCount: Int,
    @ColumnInfo(name = "win_count") val winCount: Int,
    @ColumnInfo(name = "loss_count") val lossCount: Int,
    @ColumnInfo(name = "skip_count") val skipCount: Int,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
)

@Entity(
    tableName = "item_comparisons",
    foreignKeys = [
        ForeignKey(
            entity = PreferenceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["list_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PreferenceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["left_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PreferenceItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["right_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["list_id"]),
        Index(value = ["list_id", "compared_at"]),
        Index(value = ["compared_at"]),
        Index(value = ["left_item_id"]),
        Index(value = ["right_item_id"]),
        Index(value = ["is_reverted"]),
        Index(value = ["list_id", "is_reverted", "compared_at"]),
    ],
)
data class ItemComparisonEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "list_id") val listId: String,
    @ColumnInfo(name = "left_item_id") val leftItemId: String,
    @ColumnInfo(name = "right_item_id") val rightItemId: String,
    @ColumnInfo(name = "winner_item_id") val winnerItemId: String?,
    @ColumnInfo(name = "outcome") val outcome: ComparisonOutcome,
    @ColumnInfo(name = "compared_at") val comparedAt: Long,
    @ColumnInfo(name = "left_rating_before") val leftRatingBefore: Double,
    @ColumnInfo(name = "right_rating_before") val rightRatingBefore: Double,
    @ColumnInfo(name = "left_rating_after") val leftRatingAfter: Double,
    @ColumnInfo(name = "right_rating_after") val rightRatingAfter: Double,
    @ColumnInfo(name = "k_factor_used") val kFactorUsed: Double,
    @ColumnInfo(name = "decay_factor_used") val decayFactorUsed: Double,
    @ColumnInfo(name = "is_reverted") val isReverted: Boolean = false,
)

@Entity(
    tableName = "list_comparisons",
    foreignKeys = [
        ForeignKey(
            entity = PreferenceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["left_list_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PreferenceListEntity::class,
            parentColumns = ["id"],
            childColumns = ["right_list_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["compared_at"]),
        Index(value = ["left_list_id"]),
        Index(value = ["right_list_id"]),
        Index(value = ["is_reverted"]),
        Index(value = ["is_reverted", "compared_at"]),
    ],
)
data class ListComparisonEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "left_list_id") val leftListId: String,
    @ColumnInfo(name = "right_list_id") val rightListId: String,
    @ColumnInfo(name = "winner_list_id") val winnerListId: String?,
    @ColumnInfo(name = "outcome") val outcome: ComparisonOutcome,
    @ColumnInfo(name = "compared_at") val comparedAt: Long,
    @ColumnInfo(name = "left_rating_before") val leftRatingBefore: Double,
    @ColumnInfo(name = "right_rating_before") val rightRatingBefore: Double,
    @ColumnInfo(name = "left_rating_after") val leftRatingAfter: Double,
    @ColumnInfo(name = "right_rating_after") val rightRatingAfter: Double,
    @ColumnInfo(name = "k_factor_used") val kFactorUsed: Double,
    @ColumnInfo(name = "decay_factor_used") val decayFactorUsed: Double,
    @ColumnInfo(name = "is_reverted") val isReverted: Boolean = false,
)
