package com.tepmex.hanziinfogf14.domain

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class Axial(val q: Int, val r: Int)

enum class HexDirection(val q: Int, val r: Int) {
    EAST(1, 0),
    WEST(-1, 0),
    NORTHEAST(1, -1),
    SOUTHWEST(-1, 1),
    NORTHWEST(0, -1),
    SOUTHEAST(0, 1),
    ;

    companion object {
        /** Horizontal rays first, then the four diagonals. At most six spokes. */
        val spokes: List<HexDirection> = listOf(
            EAST,
            WEST,
            NORTHEAST,
            SOUTHWEST,
            NORTHWEST,
            SOUTHEAST,
        )
    }
}

object HexMath {
    private val SQRT3 = sqrt(3.0).toFloat()

    fun axialToPixel(q: Int, r: Int, size: Float): Pair<Float, Float> {
        val x = size * (SQRT3 * q + SQRT3 / 2f * r)
        val y = size * (3f / 2f * r)
        return x to y
    }

    fun pixelToAxial(x: Float, y: Float, size: Float): Axial {
        val q = (SQRT3 / 3f * x - 1f / 3f * y) / size
        val r = (2f / 3f * y) / size
        return roundAxial(q, r)
    }

    fun roundAxial(q: Float, r: Float): Axial {
        val s = -q - r
        var rq = q.roundToInt()
        var rr = r.roundToInt()
        val rs = s.roundToInt()
        val qDiff = abs(rq - q)
        val rDiff = abs(rr - r)
        val sDiff = abs(rs - s)
        if (qDiff > rDiff && qDiff > sDiff) {
            rq = -rr - rs
        } else if (rDiff > sDiff) {
            rr = -rq - rs
        }
        return Axial(rq, rr)
    }

    fun corner(cx: Float, cy: Float, radius: Float, index: Int): Pair<Float, Float> {
        val angle = Math.toRadians(60.0 * index - 30.0)
        return (cx + radius * cos(angle).toFloat()) to (cy + radius * sin(angle).toFloat())
    }
}
