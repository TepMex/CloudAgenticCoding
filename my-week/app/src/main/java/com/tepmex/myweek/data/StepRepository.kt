package com.tepmex.myweek.data

import androidx.room.withTransaction
import com.tepmex.myweek.domain.StepElo
import com.tepmex.myweek.domain.StepOption
import com.tepmex.myweek.domain.StepPairs
import com.tepmex.myweek.domain.StepRating
import com.tepmex.myweek.domain.StepScore
import com.tepmex.myweek.domain.StepStanding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StepRepository(
    private val db: WeekDatabase,
    private val dao: StepDao,
) {
    fun observeStandings(): Flow<Map<String, StepStanding>> =
        dao.observeRatings().map { rows ->
            rows.associate { row ->
                row.optionKey to StepStanding(row.rating, row.comparisonCount)
            }
        }

    fun observeComparisonCount(): Flow<Int> = dao.observeComparisonCount()

    suspend fun standings(): Map<String, StepStanding> =
        dao.ratings().associate { it.optionKey to StepStanding(it.rating, it.comparisonCount) }

    suspend fun history(): StepPairs.History = StepPairs.History(
        recentPairKeys = dao.recentPairKeys().asReversed(),
        headToHeadCounts = dao.headToHead().associate { it.pairKey to it.n },
    )

    suspend fun record(left: StepOption, right: StepOption, outcome: StepElo.Outcome) {
        db.withTransaction {
            val now = System.currentTimeMillis()
            val storedLeft = (dao.rating(left.key)?.toRating() ?: StepScore.fresh(left))
                .copy(emoji = left.emoji, label = left.label)
            val storedRight = (dao.rating(right.key)?.toRating() ?: StepScore.fresh(right))
                .copy(emoji = right.emoji, label = right.label)
            val (nextLeft, nextRight) = StepScore.apply(storedLeft, storedRight, outcome)
            dao.upsert(nextLeft.toEntity(now))
            dao.upsert(nextRight.toEntity(now))
            dao.insertComparison(
                StepComparisonEntity(
                    leftKey = left.key,
                    rightKey = right.key,
                    pairKey = StepPairs.pairKey(left.key, right.key),
                    outcome = outcome.name,
                    leftRatingBefore = storedLeft.rating,
                    rightRatingBefore = storedRight.rating,
                    leftRatingAfter = nextLeft.rating,
                    rightRatingAfter = nextRight.rating,
                    comparedAt = now,
                ),
            )
        }
    }

    suspend fun undo(): Boolean = db.withTransaction {
        val last = dao.latestComparison() ?: return@withTransaction false
        val left = dao.rating(last.leftKey) ?: return@withTransaction false
        val right = dao.rating(last.rightKey) ?: return@withTransaction false
        val outcome = StepElo.Outcome.valueOf(last.outcome)
        val now = System.currentTimeMillis()
        dao.upsert(
            StepScore.revert(left.toRating(), last.leftRatingBefore, outcome, StepElo.Side.LEFT).toEntity(now),
        )
        dao.upsert(
            StepScore.revert(right.toRating(), last.rightRatingBefore, outcome, StepElo.Side.RIGHT).toEntity(now),
        )
        dao.deleteComparison(last.id)
        true
    }
}

private fun StepRatingEntity.toRating(): StepRating = StepRating(
    key = optionKey,
    emoji = emoji,
    label = label,
    rating = rating,
    comparisonCount = comparisonCount,
    winCount = winCount,
    lossCount = lossCount,
    skipCount = skipCount,
)

private fun StepRating.toEntity(updatedAt: Long): StepRatingEntity = StepRatingEntity(
    optionKey = key,
    emoji = emoji,
    label = label,
    rating = rating,
    comparisonCount = comparisonCount,
    winCount = winCount,
    lossCount = lossCount,
    skipCount = skipCount,
    updatedAt = updatedAt,
)
