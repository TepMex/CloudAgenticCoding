package com.tepmex.myweek.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepEloTest {
    @Test
    fun equalRatingsMoveByHalfTheKFactor() {
        val update = StepElo.apply(1000.0, 1000.0, StepElo.Outcome.LEFT)!!
        assertEquals(1016.0, update.left, 0.001)
        assertEquals(984.0, update.right, 0.001)
    }

    @Test
    fun skipDoesNotMoveTheRating() {
        assertNull(StepElo.apply(1000.0, 1100.0, StepElo.Outcome.SKIP))
    }

    @Test
    fun drawKeepsEqualRatings() {
        val update = StepElo.apply(1000.0, 1000.0, StepElo.Outcome.DRAW)!!
        assertEquals(1000.0, update.left, 0.001)
        assertEquals(1000.0, update.right, 0.001)
    }

    @Test
    fun scoreAndUndoRoundTrip() {
        val left = StepScore.fresh(option("a"))
        val right = StepScore.fresh(option("b"))
        val (nextLeft, nextRight) = StepScore.apply(left, right, StepElo.Outcome.LEFT)
        assertEquals(1, nextLeft.winCount)
        assertEquals(1, nextRight.lossCount)
        assertEquals(1, nextLeft.comparisonCount)
        val undoneLeft = StepScore.revert(nextLeft, left.rating, StepElo.Outcome.LEFT, StepElo.Side.LEFT)
        val undoneRight = StepScore.revert(nextRight, right.rating, StepElo.Outcome.LEFT, StepElo.Side.RIGHT)
        assertEquals(left.rating, undoneLeft.rating, 0.001)
        assertEquals(0, undoneLeft.winCount)
        assertEquals(0, undoneLeft.comparisonCount)
        assertEquals(0, undoneRight.lossCount)
        assertEquals(1000.0, undoneRight.rating, 0.001)
    }

    private fun option(key: String) = StepOption(
        key = key,
        emoji = "•",
        label = key,
        openCount = 1,
        rating = StepElo.INITIAL,
        comparisonCount = 0,
    )
}
