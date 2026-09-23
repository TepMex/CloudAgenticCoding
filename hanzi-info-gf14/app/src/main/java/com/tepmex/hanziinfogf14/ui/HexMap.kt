package com.tepmex.hanziinfogf14.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.hanziinfogf14.data.Branch
import com.tepmex.hanziinfogf14.data.ComponentGlyph
import com.tepmex.hanziinfogf14.domain.Axial
import com.tepmex.hanziinfogf14.domain.HexDirection
import com.tepmex.hanziinfogf14.domain.HexLayout
import com.tepmex.hanziinfogf14.domain.HexMath
import kotlin.math.abs
import kotlin.math.hypot

private val LightBranchColors = listOf(
    Color(0xFF1E4D45),
    Color(0xFF8C3A32),
    Color(0xFF2C5F8A),
    Color(0xFF8A5A12),
    Color(0xFF5A3E78),
    Color(0xFF3E6B32),
)

private val DarkBranchColors = listOf(
    Color(0xFF8FCBBE),
    Color(0xFFE7A59C),
    Color(0xFF9EC4E6),
    Color(0xFFE6C48A),
    Color(0xFFCDB4E4),
    Color(0xFFB7D4A4),
)

@Composable
fun HexMap(
    center: String,
    branches: List<Branch>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val visible = remember(branches) { HexLayout.visibleBranches(branches) }
    val nodes = remember(center, visible) { HexLayout.place(center, visible) }
    val nodeAt = remember(nodes) { nodes.associateBy { it.q to it.r } }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val hexSize = with(density) { 36.dp.toPx() }
    val dark = isSystemInDarkTheme()
    val branchColors = if (dark) DarkBranchColors else LightBranchColors
    val cellFill = if (dark) Color(0xFF2A2622) else Color(0xFFFFFBF5)
    val cellText = if (dark) Color(0xFFF3EBDF) else Color(0xFF1C1915)
    val centerFill = if (dark) Color(0xFF8FCBBE) else Color(0xFF1E4D45)
    val centerText = if (dark) Color(0xFF08332E) else Color.White
    val pan = remember { mutableStateOf(Offset.Zero) }
    val scale = remember { mutableFloatStateOf(1f) }

    LaunchedEffect(center) {
        pan.value = Offset.Zero
    }

    Canvas(
        modifier
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(nodes, hexSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var pastSlop = false
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val event = awaitPointerEvent()
                        val stillDown = event.changes.any { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        val primary = event.changes.firstOrNull { it.id == down.id }
                            ?: event.changes.firstOrNull()
                        val moved = primary?.let { (it.position - down.position).getDistance() } ?: 0f
                        if (!pastSlop && (moved > slop || abs(zoomChange - 1f) > 0.04f)) {
                            pastSlop = true
                        }
                        if (pastSlop && stillDown) {
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val old = scale.floatValue
                            val next = (old * zoomChange).coerceIn(0.35f, 3.5f)
                            val applied = if (old == 0f) 1f else next / old
                            val origin = Offset(size.width / 2f, size.height / 2f)
                            val pan0 = pan.value
                            pan.value = (centroid - origin) * (1f - applied) + pan0 * applied + panChange
                            scale.floatValue = next
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                        if (!stillDown) break
                    }
                    if (!pastSlop) {
                        val origin = Offset(size.width / 2f, size.height / 2f)
                        val axial = screenToAxial(down.position, origin, pan.value, scale.floatValue, hexSize)
                        val hit = nodeAt[axial.q to axial.r]
                        if (hit != null && hit.hanzi != center) onSelect(hit.hanzi)
                    }
                }
            },
    ) {
        val origin = Offset(size.width / 2f, size.height / 2f)
        val drawScale = scale.floatValue
        val drawPan = pan.value
        translate(origin.x + drawPan.x, origin.y + drawPan.y) {
            scale(drawScale, drawScale, pivot = Offset.Zero) {
                val radius = hexSize * 0.90f
                val ends = nodes.filter { it.branchIndex != null }.groupBy { it.branchIndex }
                for ((index, group) in ends) {
                    val end = group.last()
                    val (ex, ey) = HexMath.axialToPixel(end.q, end.r, hexSize)
                    val color = branchColors[index ?: 0]
                    drawLine(
                        color = color.copy(alpha = 0.45f),
                        start = Offset.Zero,
                        end = Offset(ex, ey),
                        strokeWidth = hexSize * 0.045f,
                    )
                }
                for ((index, branch) in visible.withIndex()) {
                    val direction = HexDirection.spokes[index]
                    val anchor = labelAnchor(direction, hexSize)
                    val color = branchColors[index]
                    drawCircle(color = color, radius = radius * 0.34f, center = anchor)
                    val label = ComponentGlyph.display(branch.component)
                    val layout = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = if (dark) Color(0xFF141311) else Color.White,
                            fontSize = (radius * 0.40f / this.density / this.fontScale).sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(
                            anchor.x - layout.size.width / 2f,
                            anchor.y - layout.size.height / 2f,
                        ),
                    )
                }
                for (node in nodes) {
                    val (x, y) = HexMath.axialToPixel(node.q, node.r, hexSize)
                    val isCenter = node.branchIndex == null
                    val stroke = if (isCenter) {
                        centerFill
                    } else {
                        branchColors[node.branchIndex ?: 0]
                    }
                    val path = hexPath(x, y, radius)
                    drawPath(path, color = if (isCenter) centerFill else cellFill)
                    drawPath(path, color = stroke, style = Stroke(width = hexSize * 0.035f))
                    val layout = textMeasurer.measure(
                        text = node.hanzi,
                        style = TextStyle(
                            color = if (isCenter) centerText else cellText,
                            fontSize = (radius * (if (isCenter) 0.78f else 0.70f) / this.density / this.fontScale).sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = if (isCenter) FontWeight.Bold else FontWeight.Medium,
                        ),
                    )
                    drawText(
                        layout,
                        topLeft = Offset(
                            x - layout.size.width / 2f,
                            y - layout.size.height / 2f,
                        ),
                    )
                }
            }
        }
    }
}

private fun screenToAxial(
    screen: Offset,
    origin: Offset,
    pan: Offset,
    scale: Float,
    hexSize: Float,
): Axial {
    val worldX = (screen.x - origin.x - pan.x) / scale
    val worldY = (screen.y - origin.y - pan.y) / scale
    return HexMath.pixelToAxial(worldX, worldY, hexSize)
}

private fun labelAnchor(direction: HexDirection, hexSize: Float): Offset {
    val (x, y) = HexMath.axialToPixel(direction.q, direction.r, hexSize)
    val mid = Offset(x / 2f, y / 2f)
    val length = hypot(x, y).coerceAtLeast(1f)
    val shift = hexSize * 0.78f
    return Offset(mid.x + (-y / length) * shift, mid.y + (x / length) * shift)
}

private fun hexPath(cx: Float, cy: Float, radius: Float): Path {
    val path = Path()
    for (index in 0 until 6) {
        val (x, y) = HexMath.corner(cx, cy, radius, index)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
