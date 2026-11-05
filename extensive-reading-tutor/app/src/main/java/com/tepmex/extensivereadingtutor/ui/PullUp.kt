package com.tepmex.extensivereadingtutor.ui

/** How long the finger must stay past the end before a release loads the next analysis. */
const val PULL_HOLD_MILLIS = 450L

/**
 * A release loads analysis only when the page is already at its end, the finger has
 * pulled further up past [thresholdPx], and that pull has been held long enough to arm.
 */
fun pullUpRelease(atEnd: Boolean, pullPx: Float, armed: Boolean, thresholdPx: Float): Boolean {
    if (!atEnd || !armed) return false
    if (!pullPx.isFinite() || !thresholdPx.isFinite()) return false
    if (thresholdPx <= 0f) return false
    return pullPx >= thresholdPx
}
