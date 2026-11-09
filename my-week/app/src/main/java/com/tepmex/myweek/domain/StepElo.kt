package com.tepmex.myweek.domain

import kotlin.math.pow

/**
 * Elo update used by pair-comp-elo, without time decay.
 * expected = 1 / (1 + 10 ^ ((other - rating) / scale))
 * next = rating + K * (actual - expected)
 * Win is 1, draw is 0.5, loss is 0. A skip does not move the rating.
 */
object StepElo {
    const val INITIAL = 1000.0
    const val K = 32.0
    const val SCALE = 400.0

    enum class Outcome { LEFT, RIGHT, DRAW, SKIP }

    enum class Side { LEFT, RIGHT }

    data class Update(val left: Double, val right: Double)

    fun apply(left: Double, right: Double, outcome: Outcome): Update? {
        if (outcome == Outcome.SKIP) return null
        val actualLeft = when (outcome) {
            Outcome.LEFT -> 1.0
            Outcome.RIGHT -> 0.0
            Outcome.DRAW -> 0.5
            Outcome.SKIP -> return null
        }
        val expectedLeft = 1.0 / (1.0 + 10.0.pow((right - left) / SCALE))
        val expectedRight = 1.0 - expectedLeft
        return Update(
            left = left + K * (actualLeft - expectedLeft),
            right = right + K * ((1.0 - actualLeft) - expectedRight),
        )
    }
}

data class StepRating(
    val key: String,
    val emoji: String,
    val label: String,
    val rating: Double,
    val comparisonCount: Int,
    val winCount: Int,
    val lossCount: Int,
    val skipCount: Int,
)

object StepScore {
    fun fresh(option: StepOption): StepRating = StepRating(
        key = option.key,
        emoji = option.emoji,
        label = option.label,
        rating = StepElo.INITIAL,
        comparisonCount = 0,
        winCount = 0,
        lossCount = 0,
        skipCount = 0,
    )

    fun apply(left: StepRating, right: StepRating, outcome: StepElo.Outcome): Pair<StepRating, StepRating> {
        val update = StepElo.apply(left.rating, right.rating, outcome)
        var nextLeft = left.copy(
            rating = update?.left ?: left.rating,
            comparisonCount = left.comparisonCount + 1,
        )
        var nextRight = right.copy(
            rating = update?.right ?: right.rating,
            comparisonCount = right.comparisonCount + 1,
        )
        when (outcome) {
            StepElo.Outcome.LEFT -> {
                nextLeft = nextLeft.copy(winCount = nextLeft.winCount + 1)
                nextRight = nextRight.copy(lossCount = nextRight.lossCount + 1)
            }
            StepElo.Outcome.RIGHT -> {
                nextRight = nextRight.copy(winCount = nextRight.winCount + 1)
                nextLeft = nextLeft.copy(lossCount = nextLeft.lossCount + 1)
            }
            StepElo.Outcome.DRAW -> Unit
            StepElo.Outcome.SKIP -> {
                nextLeft = nextLeft.copy(skipCount = nextLeft.skipCount + 1)
                nextRight = nextRight.copy(skipCount = nextRight.skipCount + 1)
            }
        }
        return nextLeft to nextRight
    }

    fun revert(rating: StepRating, before: Double, outcome: StepElo.Outcome, side: StepElo.Side): StepRating {
        val won = (outcome == StepElo.Outcome.LEFT && side == StepElo.Side.LEFT) ||
            (outcome == StepElo.Outcome.RIGHT && side == StepElo.Side.RIGHT)
        val lost = (outcome == StepElo.Outcome.LEFT && side == StepElo.Side.RIGHT) ||
            (outcome == StepElo.Outcome.RIGHT && side == StepElo.Side.LEFT)
        return rating.copy(
            rating = before,
            comparisonCount = (rating.comparisonCount - 1).coerceAtLeast(0),
            winCount = (rating.winCount - if (won) 1 else 0).coerceAtLeast(0),
            lossCount = (rating.lossCount - if (lost) 1 else 0).coerceAtLeast(0),
            skipCount = (rating.skipCount - if (outcome == StepElo.Outcome.SKIP) 1 else 0).coerceAtLeast(0),
        )
    }
}
