package com.tepmex.assumptions.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.assumptions.domain.Emotions
import com.tepmex.assumptions.domain.WheelHit
import com.tepmex.assumptions.domain.WheelLayout
import com.tepmex.assumptions.domain.WheelState
import com.tepmex.assumptions.domain.Wedge
import com.tepmex.assumptions.domain.labelArgb
import com.tepmex.assumptions.domain.normalizeDeg
import com.tepmex.assumptions.ui.theme.Ink
import com.tepmex.assumptions.ui.theme.PaperRaised
import com.tepmex.assumptions.ui.theme.argb
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun EmotionWheel(
    state: WheelState,
    onHit: (WheelHit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val stateRef = rememberUpdatedState(state)
    val onHitRef = rememberUpdatedState(onHit)
    val selectedName = state.selectedId?.let { Emotions.find(it)?.name }
    val description = if (selectedName == null) {
        "Колесо эмоций"
    } else {
        "Выбрано: $selectedName"
    }
    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun dispatch(position: Offset) {
                        val hit = WheelLayout.hitTest(
                            stateRef.value,
                            size.width.toFloat(),
                            size.height.toFloat(),
                            position.x,
                            position.y,
                        ) ?: return
                        onHitRef.value(hit)
                    }
                    dispatch(down.position)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        dispatch(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        drawWheel(state, textMeasurer)
    }
}

private fun DrawScope.drawWheel(state: WheelState, textMeasurer: TextMeasurer) {
    val radius = size.minDimension / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val wedges = WheelLayout.layout(state)
    val focused = state.focusedIndex
    val ordered = wedges.sortedBy { wedge ->
        val band = wedge.hit as? WheelHit.PrimaryBand
        when {
            band == null -> 1
            band.index != focused -> 0
            else -> 2 + band.depth
        }
    }
    for (wedge in ordered) {
        val path = wedgePath(wedge, center, radius)
        val emotion = Emotions.find(wedge.emotionId) ?: continue
        val dimmed = state.selectedId != null && state.selectedId != wedge.emotionId
        val fill = argb(emotion.colorArgb).copy(alpha = if (dimmed) 0.55f else 1f)
        drawPath(path, fill)
        if (state.selectedId == wedge.emotionId) {
            drawPath(path, PaperRaised.copy(alpha = 0.22f))
            drawPath(
                path,
                Ink.copy(alpha = 0.72f),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        clipPath(path) {
            drawWedgeLabel(wedge, pathCenter(wedge, center, radius), textMeasurer)
        }
    }
    drawCircle(
        color = PaperRaised,
        radius = radius * WheelLayout.HUB,
        center = center,
    )
    if (focused != null) {
        val arm = radius * 0.045f
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawLine(Ink.copy(alpha = 0.75f), center + Offset(-arm, -arm), center + Offset(arm, arm), stroke.width, cap = StrokeCap.Round)
        drawLine(Ink.copy(alpha = 0.75f), center + Offset(-arm, arm), center + Offset(arm, -arm), stroke.width, cap = StrokeCap.Round)
    }
    drawCircle(
        color = Ink.copy(alpha = 0.2f),
        radius = radius * 0.975f,
        center = center,
        style = Stroke(width = 1.5.dp.toPx()),
    )
}

private fun wedgePath(wedge: Wedge, center: Offset, radius: Float): Path {
    val inset = 0.7f
    val start = wedge.startDeg + inset
    val sweep = (wedge.sweepDeg - inset * 2f).coerceAtLeast(1f)
    val innerFrac = if (wedge.inner <= WheelLayout.HUB + 0.001f) {
        WheelLayout.HUB + 0.012f
    } else {
        wedge.inner + 0.012f
    }
    val outerFrac = if (wedge.outer >= WheelLayout.OUTER - 0.001f) 0.968f else wedge.outer - 0.012f
    val inner = radius * innerFrac
    val outer = radius * outerFrac
    val path = Path()
    val outerStart = polar(center, outer, start)
    val innerEnd = polar(center, inner, start + sweep)
    path.moveTo(outerStart.x, outerStart.y)
    path.arcTo(
        rect = Rect(center, outer),
        startAngleDegrees = start - 90f,
        sweepAngleDegrees = sweep,
        forceMoveTo = false,
    )
    path.lineTo(innerEnd.x, innerEnd.y)
    path.arcTo(
        rect = Rect(center, inner),
        startAngleDegrees = start + sweep - 90f,
        sweepAngleDegrees = -sweep,
        forceMoveTo = false,
    )
    path.close()
    return path
}

private fun pathCenter(wedge: Wedge, center: Offset, radius: Float): Offset {
    val midDeg = wedge.startDeg + wedge.sweepDeg / 2f
    val midFrac = (wedge.inner + wedge.outer) / 2f
    return polar(center, radius * midFrac, midDeg)
}

private fun DrawScope.drawWedgeLabel(wedge: Wedge, anchor: Offset, textMeasurer: TextMeasurer) {
    val emotion = Emotions.find(wedge.emotionId) ?: return
    val radius = size.minDimension / 2f
    val midFrac = (wedge.inner + wedge.outer) / 2f
    val midRadius = radius * midFrac
    val sweepRad = Math.toRadians((wedge.sweepDeg / 2f).toDouble())
    val chord = (2.0 * midRadius * sin(sweepRad)).toFloat()
    val thickness = radius * (wedge.outer - wedge.inner) * 0.82f
    val horizontal = wedge.sweepDeg >= 30f
    val limit = if (horizontal) chord * 0.88f else thickness * 0.88f
    val fontCap = if (horizontal) minOf(thickness * 0.42f, 16.sp.toPx()) else minOf(chord * 0.55f, 13.sp.toPx())
    if (fontCap < 8.sp.toPx() || limit < 8.sp.toPx()) return
    val styleColor = argb(labelArgb(emotion.colorArgb))
    var fontPx = fontCap
    var layout = textMeasurer.measure(
        text = emotion.name,
        style = TextStyle(
            color = styleColor,
            fontSize = fontPx.toSp(),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        softWrap = false,
    )
    if (layout.size.width > limit) {
        fontPx *= limit / layout.size.width.toFloat()
        if (fontPx < 8.sp.toPx()) return
        layout = textMeasurer.measure(
            text = emotion.name,
            style = TextStyle(
                color = styleColor,
                fontSize = fontPx.toSp(),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
    val topLeft = Offset(
        anchor.x - layout.size.width / 2f,
        anchor.y - layout.size.height / 2f,
    )
    if (horizontal) {
        drawText(layout, topLeft = topLeft)
        return
    }
    val spin = normalizeDeg(wedge.startDeg + wedge.sweepDeg / 2f)
    val rotation = if (spin > 90f && spin < 270f) spin + 90f else spin - 90f
    rotate(rotation, anchor) {
        drawText(layout, topLeft = topLeft)
    }
}

private fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(
        x = center.x + (radius * sin(radians)).toFloat(),
        y = center.y - (radius * cos(radians)).toFloat(),
    )
}

private operator fun Offset.plus(other: Offset): Offset = Offset(x + other.x, y + other.y)

private fun minOf(a: Float, b: Float): Float = kotlin.math.min(a, b)
