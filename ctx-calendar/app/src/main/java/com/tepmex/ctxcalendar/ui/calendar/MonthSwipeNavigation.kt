package com.tepmex.ctxcalendar.ui.calendar

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Swipe horizontally on the month grid to change months.
 * Swipe right → previous month; swipe left → next month.
 * Only triggers when horizontal movement clearly dominates vertical movement.
 */
fun Modifier.monthSwipeNavigation(
    onSwipeToPreviousMonth: () -> Unit,
    onSwipeToNextMonth: () -> Unit,
    swipeThreshold: Dp = 72.dp,
): Modifier = composed {
    val thresholdPx = with(LocalDensity.current) { swipeThreshold.toPx() }
    Modifier.pointerInput(onSwipeToPreviousMonth, onSwipeToNextMonth, thresholdPx) {
        var totalX = 0f
        var totalY = 0f
        detectDragGestures(
            onDragStart = {
                totalX = 0f
                totalY = 0f
            },
            onDragEnd = {
                if (abs(totalX) > abs(totalY) && abs(totalX) >= thresholdPx) {
                    if (totalX > 0) {
                        onSwipeToPreviousMonth()
                    } else {
                        onSwipeToNextMonth()
                    }
                }
                totalX = 0f
                totalY = 0f
            },
            onDragCancel = {
                totalX = 0f
                totalY = 0f
            },
            onDrag = { _, dragAmount ->
                totalX += dragAmount.x
                totalY += dragAmount.y
            },
        )
    }
}
