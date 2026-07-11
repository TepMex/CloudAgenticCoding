package com.tepmex.paircompelo.data.mapper

import com.tepmex.paircompelo.data.db.ItemComparisonEntity
import com.tepmex.paircompelo.data.db.ListComparisonEntity
import com.tepmex.paircompelo.data.db.PreferenceItemEntity
import com.tepmex.paircompelo.data.db.PreferenceListEntity
import com.tepmex.paircompelo.domain.model.ItemComparison
import com.tepmex.paircompelo.domain.model.ListComparison
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.domain.model.PreferenceList
import java.time.Instant
import java.util.UUID

fun PreferenceListEntity.toDomain(): PreferenceList = PreferenceList(
    id = UUID.fromString(id),
    name = name,
    description = description,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
    rating = rating,
    ratingUpdatedAt = Instant.ofEpochMilli(ratingUpdatedAt),
    comparisonCount = comparisonCount,
)

fun PreferenceList.toEntity(): PreferenceListEntity = PreferenceListEntity(
    id = id.toString(),
    name = name,
    description = description,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
    rating = rating,
    ratingUpdatedAt = ratingUpdatedAt.toEpochMilli(),
    comparisonCount = comparisonCount,
)

fun PreferenceItemEntity.toDomain(): PreferenceItem = PreferenceItem(
    id = UUID.fromString(id),
    listId = UUID.fromString(listId),
    name = name,
    description = description,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
    rating = rating,
    ratingUpdatedAt = Instant.ofEpochMilli(ratingUpdatedAt),
    comparisonCount = comparisonCount,
    winCount = winCount,
    lossCount = lossCount,
    skipCount = skipCount,
    sortOrder = sortOrder,
)

fun PreferenceItem.toEntity(): PreferenceItemEntity = PreferenceItemEntity(
    id = id.toString(),
    listId = listId.toString(),
    name = name,
    description = description,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
    rating = rating,
    ratingUpdatedAt = ratingUpdatedAt.toEpochMilli(),
    comparisonCount = comparisonCount,
    winCount = winCount,
    lossCount = lossCount,
    skipCount = skipCount,
    sortOrder = sortOrder,
)

fun ItemComparisonEntity.toDomain(): ItemComparison = ItemComparison(
    id = UUID.fromString(id),
    listId = UUID.fromString(listId),
    leftItemId = UUID.fromString(leftItemId),
    rightItemId = UUID.fromString(rightItemId),
    winnerItemId = winnerItemId?.let(UUID::fromString),
    outcome = outcome,
    comparedAt = Instant.ofEpochMilli(comparedAt),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

fun ItemComparison.toEntity(): ItemComparisonEntity = ItemComparisonEntity(
    id = id.toString(),
    listId = listId.toString(),
    leftItemId = leftItemId.toString(),
    rightItemId = rightItemId.toString(),
    winnerItemId = winnerItemId?.toString(),
    outcome = outcome,
    comparedAt = comparedAt.toEpochMilli(),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

fun ListComparisonEntity.toDomain(): ListComparison = ListComparison(
    id = UUID.fromString(id),
    leftListId = UUID.fromString(leftListId),
    rightListId = UUID.fromString(rightListId),
    winnerListId = winnerListId?.let(UUID::fromString),
    outcome = outcome,
    comparedAt = Instant.ofEpochMilli(comparedAt),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)

fun ListComparison.toEntity(): ListComparisonEntity = ListComparisonEntity(
    id = id.toString(),
    leftListId = leftListId.toString(),
    rightListId = rightListId.toString(),
    winnerListId = winnerListId?.toString(),
    outcome = outcome,
    comparedAt = comparedAt.toEpochMilli(),
    leftRatingBefore = leftRatingBefore,
    rightRatingBefore = rightRatingBefore,
    leftRatingAfter = leftRatingAfter,
    rightRatingAfter = rightRatingAfter,
    kFactorUsed = kFactorUsed,
    decayFactorUsed = decayFactorUsed,
    isReverted = isReverted,
)
