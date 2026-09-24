package com.tepmex.instantpinyin.domain

/**
 * How overlay text should face the reader.
 *
 * [snapHold] quantizes the gravity orientation (0 = portrait, 90 = the device's
 * left side is up, 180 = upside down, 270 = the right side is up).
 * [textRotation] is the clockwise degrees to rotate a label drawn in the current
 * window so the baseline stays horizontal for that hold. A window that already
 * follows the hold returns 0.
 */
object LabelFacing {
    fun snapHold(orientation: Int): Int {
        if (orientation < 0) return -1
        return ((orientation + 45) / 90 * 90) % 360
    }

    fun textRotation(windowDegrees: Int, holdDegrees: Int): Int {
        if (holdDegrees < 0) return 0
        val hold = ((holdDegrees % 360) + 360) % 360
        val uprightWindow = when (hold) {
            90 -> 270
            270 -> 90
            else -> hold
        }
        val window = ((windowDegrees % 360) + 360) % 360
        return (uprightWindow - window + 360) % 360
    }

    /** Swap a horizontal pill so a ±90° rotation still fits the same string. */
    fun face(pill: PxBox, rotation: Int): PxBox {
        if (rotation != 90 && rotation != 270) return pill
        val halfW = pill.height / 2f
        val halfH = pill.width / 2f
        return PxBox(
            left = pill.centerX - halfW,
            top = pill.centerY - halfH,
            right = pill.centerX + halfW,
            bottom = pill.centerY + halfH,
        )
    }
}
