package com.tepmex.instantpinyin.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LabelFacingTest {
    @Test
    fun snapHoldRoundsToTheNearestQuadrant() {
        assertEquals(0, LabelFacing.snapHold(20))
        assertEquals(90, LabelFacing.snapHold(80))
        assertEquals(270, LabelFacing.snapHold(300))
        assertEquals(-1, LabelFacing.snapHold(-1))
    }

    @Test
    fun matchingWindowAndHoldLeaveTextUnrotated() {
        assertEquals(0, LabelFacing.textRotation(windowDegrees = 0, holdDegrees = 0))
        assertEquals(0, LabelFacing.textRotation(windowDegrees = 270, holdDegrees = 90))
        assertEquals(0, LabelFacing.textRotation(windowDegrees = 90, holdDegrees = 270))
    }

    @Test
    fun aPortraitWindowWithALandscapeHoldRotatesTheLabel() {
        assertEquals(270, LabelFacing.textRotation(windowDegrees = 0, holdDegrees = 90))
        assertEquals(90, LabelFacing.textRotation(windowDegrees = 0, holdDegrees = 270))
    }
}
