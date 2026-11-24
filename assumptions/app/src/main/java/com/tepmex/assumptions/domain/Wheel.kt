package com.tepmex.assumptions.domain

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

data class WheelState(
    val focusedIndex: Int? = null,
    /** 0 = only mild sectors, 1 = medium band open, 2 = intense band open. */
    val revealedDepth: Int = 0,
    val selectedId: String? = null,
)

sealed interface WheelHit {
    data object Hub : WheelHit

    data class PrimaryBand(val index: Int, val depth: Int) : WheelHit

    data class Dyad(val id: String) : WheelHit
}

object WheelReducer {
    fun reduce(state: WheelState, hit: WheelHit): WheelState = when (hit) {
        WheelHit.Hub -> WheelState()
        is WheelHit.Dyad -> {
            if (state.focusedIndex == null || Emotions.find(hit.id) == null) {
                state
            } else {
                state.copy(selectedId = hit.id)
            }
        }
        is WheelHit.PrimaryBand -> reducePrimary(state, hit)
    }

    private fun reducePrimary(state: WheelState, hit: WheelHit.PrimaryBand): WheelState {
        if (hit.index !in Emotions.families.indices || hit.depth !in 0..2) return state
        if (state.focusedIndex != hit.index) {
            return WheelState(
                focusedIndex = hit.index,
                revealedDepth = 1,
                selectedId = Emotions.band(hit.index, 0).id,
            )
        }
        val depth = hit.depth.coerceIn(0, state.revealedDepth)
        val nextDepth = if (depth == state.revealedDepth && state.revealedDepth < 2) {
            state.revealedDepth + 1
        } else {
            state.revealedDepth
        }
        return state.copy(
            revealedDepth = nextDepth,
            selectedId = Emotions.band(hit.index, depth).id,
        )
    }
}

data class Wedge(
    val hit: WheelHit,
    val emotionId: String,
    /** Degrees clockwise from the top. Values outside 0..360 are allowed. */
    val startDeg: Float,
    val sweepDeg: Float,
    val inner: Float,
    val outer: Float,
)

object WheelLayout {
    const val HUB: Float = 0.15f
    const val OUTER: Float = 1f
    const val DYAD_INNER: Float = 0.42f

    private const val COUNT = 8
    private const val SLOT = 360f / COUNT
    private const val OPEN_PETAL_SWEEP = 32f
    private const val DYAD_SWEEP = 24f

    fun layout(state: WheelState): List<Wedge> {
        val focused = state.focusedIndex
        if (focused == null || state.revealedDepth <= 0 || focused !in 0 until COUNT) {
            return (0 until COUNT).map { index ->
                primaryWedge(
                    index = index,
                    depth = 0,
                    start = index * SLOT - SLOT / 2f,
                    sweep = SLOT,
                    inner = HUB,
                    outer = OUTER,
                )
            }
        }
        val openDepth = state.revealedDepth.coerceIn(1, 2)
        val center = focused * SLOT
        val petalStart = center - OPEN_PETAL_SWEEP / 2f
        val leftStart = petalStart - DYAD_SWEEP
        val rightStart = petalStart + OPEN_PETAL_SWEEP
        val wedges = ArrayList<Wedge>(4 + COUNT)
        wedges += dyadWedge(Emotions.dyadBefore(focused), leftStart)
        wedges += dyadWedge(Emotions.dyadAfter(focused), rightStart)
        val radii = bandRadii(openDepth)
        for (depth in 0..openDepth) {
            val (inner, outer) = radii[depth]
            wedges += primaryWedge(focused, depth, petalStart, OPEN_PETAL_SWEEP, inner, outer)
        }
        val gapStart = rightStart + DYAD_SWEEP
        val gapEnd = leftStart + 360f
        val others = (1 until COUNT).map { (focused + it) % COUNT }
        val each = (gapEnd - gapStart) / others.size
        others.forEachIndexed { position, index ->
            val start = gapStart + position * each
            val sweep = if (position == others.lastIndex) gapEnd - start else each
            wedges += primaryWedge(index, 0, start, sweep, HUB, OUTER)
        }
        return wedges
    }

    fun hitTest(state: WheelState, widthPx: Float, heightPx: Float, x: Float, y: Float): WheelHit? {
        val polar = toPolar(widthPx, heightPx, x, y) ?: return null
        return hitTestPolar(state, polar.first, polar.second)
    }

    fun hitTestPolar(state: WheelState, angleDeg: Float, radiusFraction: Float): WheelHit? {
        if (radiusFraction > OUTER + 0.02f) return null
        if (radiusFraction <= HUB) return WheelHit.Hub
        return layout(state).firstOrNull { it.contains(angleDeg, radiusFraction) }?.hit
    }

    fun toPolar(widthPx: Float, heightPx: Float, x: Float, y: Float): Pair<Float, Float>? {
        val radius = min(widthPx, heightPx) / 2f
        if (radius <= 0f) return null
        val dx = x - widthPx / 2f
        val dy = y - heightPx / 2f
        val fraction = hypot(dx.toDouble(), dy.toDouble()).toFloat() / radius
        val degrees = normalizeDeg(
            Math.toDegrees(atan2(dx.toDouble(), (-dy).toDouble())).toFloat(),
        )
        return degrees to fraction
    }

    private fun bandRadii(openDepth: Int): List<Pair<Float, Float>> = when (openDepth) {
        1 -> listOf(0.58f to OUTER, HUB to 0.58f)
        else -> listOf(0.74f to OUTER, 0.46f to 0.74f, HUB to 0.46f)
    }

    private fun primaryWedge(
        index: Int,
        depth: Int,
        start: Float,
        sweep: Float,
        inner: Float,
        outer: Float,
    ): Wedge {
        val emotion = Emotions.band(index, depth)
        return Wedge(
            hit = WheelHit.PrimaryBand(index, depth),
            emotionId = emotion.id,
            startDeg = start,
            sweepDeg = sweep,
            inner = inner,
            outer = outer,
        )
    }

    private fun dyadWedge(dyad: EmotionDyad, start: Float): Wedge = Wedge(
        hit = WheelHit.Dyad(dyad.emotion.id),
        emotionId = dyad.emotion.id,
        startDeg = start,
        sweepDeg = DYAD_SWEEP,
        inner = DYAD_INNER,
        outer = OUTER,
    )
}

fun normalizeDeg(degrees: Float): Float {
    val wrapped = degrees % 360f
    return if (wrapped < 0f) wrapped + 360f else wrapped
}

fun Wedge.contains(angleDeg: Float, radiusFraction: Float): Boolean {
    val insideRadius = if (outer >= WheelLayout.OUTER - 0.001f) {
        radiusFraction >= inner && radiusFraction <= outer + 0.001f
    } else {
        radiusFraction >= inner && radiusFraction < outer
    }
    if (!insideRadius) return false
    return normalizeDeg(angleDeg - startDeg) < sweepDeg
}
