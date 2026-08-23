package com.tepmex.idealtiming.ui.clock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.idealtiming.domain.ClockReading
import com.tepmex.idealtiming.domain.DialCueMarkers
import com.tepmex.idealtiming.domain.DialSunMarkers
import com.tepmex.idealtiming.domain.IdealClock
import com.tepmex.idealtiming.ui.theme.Gold
import com.tepmex.idealtiming.ui.theme.GoldBright
import com.tepmex.idealtiming.ui.theme.Ink
import com.tepmex.idealtiming.ui.theme.JewelBlue
import com.tepmex.idealtiming.ui.theme.Parchment
import com.tepmex.idealtiming.ui.theme.ParchmentDeep
import com.tepmex.idealtiming.ui.theme.Sector1
import com.tepmex.idealtiming.ui.theme.Sector2
import com.tepmex.idealtiming.ui.theme.Sector3
import com.tepmex.idealtiming.ui.theme.Sector4
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

data class ClockUiState(
    val reading: ClockReading? = null,
    val syncedAtEpochSec: Long = 0L,
    val sleepScore: Int = 0,
    val syncing: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val sunMarkers: DialSunMarkers? = null,
    val cueMarkers: DialCueMarkers? = null,
    /** Dial progress of today's NFC physical check-in, or null if none yet. */
    val nfcCheckInProgress: Float? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    state: ClockUiState,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onMessageConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(state.statusMessage, state.error) {
        if (state.statusMessage != null || state.error != null) {
            kotlinx.coroutines.delay(3500)
            onMessageConsumed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ideal-timing",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                actions = {
                    IconButton(onClick = onSync, enabled = !state.syncing) {
                        if (state.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = Gold,
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync")
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Ink,
                    actionIconContentColor = Ink,
                ),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFF3E6C8),
                            Parchment,
                            ParchmentDeep.copy(alpha = 0.85f),
                        ),
                    ),
                )
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val reading = state.reading
                if (reading == null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "No wake-up time yet",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Sync with Mi Fitness to load today’s wake from sleep data.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = onSync, enabled = !state.syncing) {
                        Text("Sync now")
                    }
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        reading.sector.labelRu,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Ink,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        reading.sector.labelEn,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    IdealDayDial(
                        reading = reading,
                        sunMarkers = state.sunMarkers,
                        cueMarkers = state.cueMarkers,
                        nfcCheckInProgress = state.nfcCheckInProgress,
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f),
                    )
                    Spacer(Modifier.height(16.dp))
                    MetaRow(reading = reading, syncedAt = state.syncedAtEpochSec)
                    if (reading.frozenAtSixteenHours) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Frozen at 16h — day complete",
                            style = MaterialTheme.typography.labelLarge,
                            color = JewelBlue,
                        )
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Sector3, style = MaterialTheme.typography.bodyMedium)
                }
                state.statusMessage?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Sector1, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(reading: ClockReading, syncedAt: Long) {
    val zone = ZoneId.systemDefault()
    val fmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val wake = Instant.ofEpochSecond(reading.wakeEpochSec).atZone(zone).format(fmt)
    val elapsed = formatElapsed(reading.elapsedSec)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetaChip(label = "Wake", value = wake)
            MetaChip(label = "Elapsed", value = elapsed)
            MetaChip(label = "Sector", value = reading.sector.index.toString())
        }
        if (syncedAt > 0) {
            Spacer(Modifier.height(6.dp))
            val syncLabel = Instant.ofEpochSecond(syncedAt).atZone(zone).format(fmt)
            Text(
                "Last sync $syncLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetaChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

private fun formatElapsed(sec: Long): String {
    val h = sec / 3600
    val m = (sec % 3600) / 60
    return "%d:%02d".format(h, m)
}

@Composable
fun IdealDayDial(
    reading: ClockReading,
    sunMarkers: DialSunMarkers? = null,
    cueMarkers: DialCueMarkers? = null,
    nfcCheckInProgress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val pointer = remember { Animatable(IdealClock.pointerDegrees(reading.progress)) }
    LaunchedEffect(reading.progress) {
        pointer.animateTo(
            IdealClock.pointerDegrees(reading.progress),
            animationSpec = tween(durationMillis = 700),
        )
    }
    val shimmer = rememberInfiniteTransition(label = "jewel")
    val jewelPulse by shimmer.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "jewelPulse",
    )
    val activeGlow by shimmer.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "activeGlow",
    )
    val sunRayPulse by shimmer.animateFloat(
        initialValue = 0.75f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sunRayPulse",
    )

    Canvas(modifier = modifier) {
        val side = min(size.width, size.height)
        val cx = size.width / 2f
        val cy = size.height / 2f
        // Leave rim room for sun / moon pictograms outside the gold circle.
        val outer = side * 0.40f
        val ring = side * 0.035f
        val parchmentRadius = outer + ring * 1.6f
        val markerRadius = outer + ring * 2.35f
        val sectorColors = listOf(Sector1, Sector2, Sector3, Sector4)

        // NFC check-in runner sits *behind* the parchment disc, at the angle the
        // pointer held when the tag was scanned. Inner half is covered by the face.
        nfcCheckInProgress?.let { progress ->
            val deg = IdealClock.pointerDegrees(progress)
            val runnerUnit = outer * 0.12f
            val behindRadius = parchmentRadius + runnerUnit * 0.28f
            drawRunnerPictogram(polarOffset(cx, cy, behindRadius, deg), runnerUnit)
        }

        // Outer parchment disc
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFF6EBD2), Color(0xFFD9C49A)),
                center = Offset(cx, cy),
                radius = outer * 1.15f,
            ),
            radius = parchmentRadius,
            center = Offset(cx, cy),
        )

        // Four sectors — Canvas angles: 0° = 3 o’clock, so start at -90° for 12 o’clock.
        for (i in 0 until 4) {
            val start = -90f + i * 90f
            val active = reading.sector.index == i + 1
            val base = sectorColors[i]
            drawArc(
                color = if (active) base.copy(alpha = 0.92f) else base.copy(alpha = 0.62f),
                startAngle = start,
                sweepAngle = 90f,
                useCenter = true,
                topLeft = Offset(cx - outer, cy - outer),
                size = Size(outer * 2, outer * 2),
            )
            if (active) {
                drawArc(
                    color = Color.White.copy(alpha = activeGlow),
                    startAngle = start,
                    sweepAngle = 90f,
                    useCenter = true,
                    topLeft = Offset(cx - outer, cy - outer),
                    size = Size(outer * 2, outer * 2),
                )
            }
        }

        // Gold rim
        drawCircle(
            color = Gold,
            radius = outer,
            center = Offset(cx, cy),
            style = Stroke(width = ring),
        )
        drawCircle(
            color = GoldBright.copy(alpha = 0.55f),
            radius = outer + ring * 0.55f,
            center = Offset(cx, cy),
            style = Stroke(width = ring * 0.35f),
        )

        // Sector divider ticks
        for (i in 0 until 4) {
            val deg = -90.0 + i * 90.0
            val rad = Math.toRadians(deg)
            val x1 = cx + cos(rad).toFloat() * (outer - ring)
            val y1 = cy + sin(rad).toFloat() * (outer - ring)
            val x2 = cx + cos(rad).toFloat() * (outer + ring * 0.2f)
            val y2 = cy + sin(rad).toFloat() * (outer + ring * 0.2f)
            drawLine(GoldBright, Offset(x1, y1), Offset(x2, y2), strokeWidth = ring * 0.35f, cap = StrokeCap.Round)
        }

        // Hour marks every 4h already covered; add mid-sector pips (2h)
        for (i in 0 until 8) {
            val deg = -90.0 + i * 45.0
            val rad = Math.toRadians(deg)
            val x1 = cx + cos(rad).toFloat() * (outer - ring * 0.15f)
            val y1 = cy + sin(rad).toFloat() * (outer - ring * 0.15f)
            val x2 = cx + cos(rad).toFloat() * (outer + ring * 0.05f)
            val y2 = cy + sin(rad).toFloat() * (outer + ring * 0.05f)
            drawLine(Gold.copy(alpha = 0.7f), Offset(x1, y1), Offset(x2, y2), strokeWidth = 2f)
        }

        // Sector numbers near mid-arc
        for (i in 0 until 4) {
            val mid = -90.0 + i * 90.0 + 45.0
            val rad = Math.toRadians(mid)
            val nx = cx + cos(rad).toFloat() * (outer * 0.62f)
            val ny = cy + sin(rad).toFloat() * (outer * 0.62f)
            drawCircle(Gold.copy(alpha = 0.9f), radius = outer * 0.07f, center = Offset(nx, ny))
            drawCircle(Ink.copy(alpha = 0.15f), radius = outer * 0.045f, center = Offset(nx, ny))
        }

        // Center jewel
        drawCircle(Gold, radius = outer * 0.14f, center = Offset(cx, cy))
        drawCircle(
            color = JewelBlue.copy(alpha = jewelPulse),
            radius = outer * 0.075f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.35f * jewelPulse),
            radius = outer * 0.03f,
            center = Offset(cx - outer * 0.02f, cy - outer * 0.025f),
        )

        // Sunrise / sunset pictograms outside the rim at dial angles the hand reaches.
        sunMarkers?.sunriseProgress?.let { progress ->
            val deg = IdealClock.pointerDegrees(progress)
            val center = polarOffset(cx, cy, markerRadius, deg)
            drawSunrisePictogram(center, outer * 0.085f, sunRayPulse)
        }
        sunMarkers?.sunsetProgress?.let { progress ->
            val deg = IdealClock.pointerDegrees(progress)
            val center = polarOffset(cx, cy, markerRadius, deg)
            drawSunsetPictogram(center, outer * 0.085f)
        }

        // Meals sit on the face (wake+30m / +6h / +11h). Dog walk is 19:00 wall-clock
        // and sits outside the rim so it stays distinct when it shares an angle with dinner.
        val mealRadius = outer - ring * 1.4f
        cueMarkers?.let { cues ->
            val burger = outer * 0.078f
            drawHamburgerPictogram(polarOffset(cx, cy, mealRadius, IdealClock.pointerDegrees(cues.breakfastProgress)), burger)
            drawHamburgerPictogram(polarOffset(cx, cy, mealRadius, IdealClock.pointerDegrees(cues.lunchProgress)), burger)
            drawHamburgerPictogram(polarOffset(cx, cy, mealRadius, IdealClock.pointerDegrees(cues.dinnerProgress)), burger)
            cues.dogWalkProgress?.let { progress ->
                val deg = IdealClock.pointerDegrees(progress)
                drawDogPictogram(polarOffset(cx, cy, markerRadius, deg), outer * 0.10f)
            }
        }

        // Pointer (from center toward rim), 0 progress = 12 o’clock
        rotate(degrees = pointer.value, pivot = Offset(cx, cy)) {
            val tip = Offset(cx, cy - outer * 0.88f)
            drawLine(GoldBright, Offset(cx, cy), tip, strokeWidth = outer * 0.035f, cap = StrokeCap.Round)
            drawCircle(Gold, radius = outer * 0.04f, center = Offset(cx, cy))
            drawCircle(JewelBlue, radius = outer * 0.028f, center = tip)
        }
    }
}

/** Dial degrees clockwise from 12 o’clock → canvas offset. */
private fun polarOffset(cx: Float, cy: Float, radius: Float, dialDegrees: Float): Offset {
    val rad = Math.toRadians(dialDegrees.toDouble() - 90.0)
    return Offset(
        cx + cos(rad).toFloat() * radius,
        cy + sin(rad).toFloat() * radius,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunrisePictogram(
    center: Offset,
    disk: Float,
    rayPulse: Float,
) {
    val sunCore = Color(0xFFFFC107)
    val sunHot = Color(0xFFFFF176)
    val rayLen = disk * (1.35f + 0.25f * rayPulse)
    for (i in 0 until 8) {
        val rad = Math.toRadians(i * 45.0)
        val inner = disk * 1.15f
        val x1 = center.x + cos(rad).toFloat() * inner
        val y1 = center.y + sin(rad).toFloat() * inner
        val x2 = center.x + cos(rad).toFloat() * rayLen
        val y2 = center.y + sin(rad).toFloat() * rayLen
        drawLine(
            color = sunCore.copy(alpha = 0.85f),
            start = Offset(x1, y1),
            end = Offset(x2, y2),
            strokeWidth = disk * 0.22f,
            cap = StrokeCap.Round,
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(sunHot, sunCore, Color(0xFFFF8F00)),
            center = center,
            radius = disk,
        ),
        radius = disk,
        center = center,
    )
    drawCircle(Color.White.copy(alpha = 0.45f), radius = disk * 0.35f, center = Offset(center.x - disk * 0.25f, center.y - disk * 0.28f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSunsetPictogram(
    center: Offset,
    disk: Float,
) {
    val moonFill = Color(0xFFE8EEF8)
    val moonShade = Color(0xFF9AA8BC)
    // Crescent via even-odd: full disk minus offset disk.
    val crescent = Path().apply {
        addOval(
            androidx.compose.ui.geometry.Rect(
                center.x - disk,
                center.y - disk,
                center.x + disk,
                center.y + disk,
            ),
        )
        addOval(
            androidx.compose.ui.geometry.Rect(
                center.x - disk * 0.35f,
                center.y - disk * 1.05f,
                center.x + disk * 1.45f,
                center.y + disk * 0.95f,
            ),
        )
        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
    }
    drawPath(
        path = crescent,
        brush = Brush.radialGradient(
            colors = listOf(Color.White, moonFill, moonShade),
            center = Offset(center.x - disk * 0.2f, center.y - disk * 0.15f),
            radius = disk * 1.2f,
        ),
    )
}

/** Stacked hamburger: buns, patty, cheese, lettuce — readable at dial-marker size. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHamburgerPictogram(
    center: Offset,
    unit: Float,
) {
    val bun = Color(0xFFE8A84A)
    val bunDark = Color(0xFFC4842E)
    val patty = Color(0xFF4E2A14)
    val cheese = Color(0xFFFFC107)
    val lettuce = Color(0xFF7CB342)
    val sesame = Color(0xFFFFF8E1)
    val w = unit * 1.15f
    val x = center.x
    val y = center.y

    drawCircle(Color(0xFFF6EBD2).copy(alpha = 0.72f), radius = unit * 1.45f, center = center)

    drawOval(
        color = bunDark,
        topLeft = Offset(x - w, y + unit * 0.28f),
        size = Size(w * 2f, unit * 0.55f),
    )
    drawOval(
        color = patty,
        topLeft = Offset(x - w * 0.95f, y + unit * 0.08f),
        size = Size(w * 1.9f, unit * 0.32f),
    )
    val cheesePath = Path().apply {
        moveTo(x - w * 0.92f, y + unit * 0.04f)
        lineTo(x + w * 0.92f, y - unit * 0.02f)
        lineTo(x + w * 0.55f, y + unit * 0.22f)
        lineTo(x + w * 0.15f, y + unit * 0.08f)
        lineTo(x - w * 0.25f, y + unit * 0.24f)
        close()
    }
    drawPath(cheesePath, cheese)
    val lettucePath = Path().apply {
        moveTo(x - w * 0.98f, y - unit * 0.02f)
        quadraticTo(x - w * 0.55f, y - unit * 0.22f, x - w * 0.1f, y - unit * 0.04f)
        quadraticTo(x + w * 0.35f, y - unit * 0.2f, x + w * 0.98f, y - unit * 0.02f)
        quadraticTo(x + w * 0.4f, y + unit * 0.12f, x, y + unit * 0.02f)
        quadraticTo(x - w * 0.45f, y + unit * 0.14f, x - w * 0.98f, y - unit * 0.02f)
        close()
    }
    drawPath(lettucePath, lettuce)
    drawOval(
        color = bun,
        topLeft = Offset(x - w, y - unit * 0.72f),
        size = Size(w * 2f, unit * 0.95f),
    )
    drawOval(
        color = Color(0xFFF3C36A),
        topLeft = Offset(x - w * 0.72f, y - unit * 0.68f),
        size = Size(w * 1.15f, unit * 0.42f),
    )
    val seeds = listOf(
        Offset(x - w * 0.42f, y - unit * 0.38f),
        Offset(x - w * 0.08f, y - unit * 0.48f),
        Offset(x + w * 0.28f, y - unit * 0.36f),
        Offset(x + w * 0.08f, y - unit * 0.22f),
    )
    for (seed in seeds) {
        drawCircle(sesame, radius = unit * 0.07f, center = seed)
    }
}

/**
 * Sitting pet dog (floppy ears + collar) — not an @ glyph.
 * Drawn upright so the animal stays readable at any dial angle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDogPictogram(
    center: Offset,
    unit: Float,
) {
    val fur = Color(0xFFB07A43)
    val furDark = Color(0xFF6D4C2F)
    val belly = Color(0xFFE8D2B0)
    val collar = Color(0xFFC62828)
    val tag = Color(0xFFFFD54F)
    val nose = Color(0xFF3E2723)
    val x = center.x
    val y = center.y

    drawCircle(Color(0xFFF6EBD2).copy(alpha = 0.78f), radius = unit * 1.55f, center = center)

    val tail = Path().apply {
        moveTo(x + unit * 0.55f, y + unit * 0.15f)
        quadraticTo(x + unit * 1.15f, y - unit * 0.05f, x + unit * 1.05f, y - unit * 0.55f)
        quadraticTo(x + unit * 0.75f, y - unit * 0.15f, x + unit * 0.42f, y + unit * 0.05f)
        close()
    }
    drawPath(tail, furDark)

    drawOval(
        color = fur,
        topLeft = Offset(x - unit * 0.55f, y - unit * 0.05f),
        size = Size(unit * 1.15f, unit * 0.95f),
    )
    drawOval(
        color = belly,
        topLeft = Offset(x - unit * 0.28f, y + unit * 0.12f),
        size = Size(unit * 0.55f, unit * 0.62f),
    )

    drawOval(
        color = furDark,
        topLeft = Offset(x - unit * 0.62f, y + unit * 0.55f),
        size = Size(unit * 0.32f, unit * 0.42f),
    )
    drawOval(
        color = furDark,
        topLeft = Offset(x + unit * 0.12f, y + unit * 0.55f),
        size = Size(unit * 0.32f, unit * 0.42f),
    )
    drawOval(
        color = fur,
        topLeft = Offset(x - unit * 0.58f, y + unit * 0.82f),
        size = Size(unit * 0.28f, unit * 0.16f),
    )
    drawOval(
        color = fur,
        topLeft = Offset(x + unit * 0.16f, y + unit * 0.82f),
        size = Size(unit * 0.28f, unit * 0.16f),
    )

    drawCircle(fur, radius = unit * 0.42f, center = Offset(x - unit * 0.12f, y - unit * 0.42f))
    drawOval(
        color = belly,
        topLeft = Offset(x - unit * 0.28f, y - unit * 0.38f),
        size = Size(unit * 0.38f, unit * 0.28f),
    )

    val leftEar = Path().apply {
        moveTo(x - unit * 0.48f, y - unit * 0.62f)
        quadraticTo(x - unit * 0.85f, y - unit * 0.35f, x - unit * 0.42f, y - unit * 0.18f)
        quadraticTo(x - unit * 0.28f, y - unit * 0.42f, x - unit * 0.48f, y - unit * 0.62f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(x + unit * 0.18f, y - unit * 0.68f)
        quadraticTo(x + unit * 0.52f, y - unit * 0.28f, x + unit * 0.12f, y - unit * 0.22f)
        quadraticTo(x + unit * 0.02f, y - unit * 0.48f, x + unit * 0.18f, y - unit * 0.68f)
        close()
    }
    drawPath(leftEar, furDark)
    drawPath(rightEar, furDark)

    drawRoundRect(
        color = collar,
        topLeft = Offset(x - unit * 0.38f, y - unit * 0.08f),
        size = Size(unit * 0.62f, unit * 0.14f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(unit * 0.06f, unit * 0.06f),
    )
    drawCircle(tag, radius = unit * 0.09f, center = Offset(x - unit * 0.02f, y + unit * 0.08f))

    drawCircle(Color.White, radius = unit * 0.09f, center = Offset(x - unit * 0.22f, y - unit * 0.48f))
    drawCircle(nose, radius = unit * 0.045f, center = Offset(x - unit * 0.20f, y - unit * 0.47f))
    drawCircle(nose, radius = unit * 0.08f, center = Offset(x - unit * 0.02f, y - unit * 0.32f))
    drawCircle(Color.White.copy(alpha = 0.45f), radius = unit * 0.03f, center = Offset(x - unit * 0.04f, y - unit * 0.34f))
}

/**
 * Side-view running person, drawn upright so the figure stays readable at any dial angle.
 * Placed behind the parchment disc; the outer half peeks out at the check-in angle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRunnerPictogram(
    center: Offset,
    unit: Float,
) {
    val skin = Color(0xFFC68642)
    val hair = Color(0xFF3E2723)
    val shirt = JewelBlue
    val shorts = Color(0xFF2A2118)
    val shoe = Color(0xFF1A120C)
    val x = center.x
    val y = center.y

    drawCircle(Color(0xFFF6EBD2).copy(alpha = 0.62f), radius = unit * 1.7f, center = center)

    // Motion streaks behind the runner (left).
    drawLine(
        color = Ink.copy(alpha = 0.28f),
        start = Offset(x - unit * 1.35f, y - unit * 0.15f),
        end = Offset(x - unit * 0.75f, y - unit * 0.05f),
        strokeWidth = unit * 0.08f,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Ink.copy(alpha = 0.22f),
        start = Offset(x - unit * 1.28f, y + unit * 0.12f),
        end = Offset(x - unit * 0.72f, y + unit * 0.18f),
        strokeWidth = unit * 0.07f,
        cap = StrokeCap.Round,
    )

    val backLeg = Path().apply {
        moveTo(x - unit * 0.08f, y + unit * 0.18f)
        quadraticTo(x - unit * 0.55f, y + unit * 0.35f, x - unit * 0.85f, y + unit * 0.85f)
        lineTo(x - unit * 0.62f, y + unit * 0.90f)
        quadraticTo(x - unit * 0.32f, y + unit * 0.42f, x + unit * 0.08f, y + unit * 0.22f)
        close()
    }
    drawPath(backLeg, skin)
    drawOval(
        color = shoe,
        topLeft = Offset(x - unit * 1.05f, y + unit * 0.78f),
        size = Size(unit * 0.42f, unit * 0.18f),
    )

    val frontLeg = Path().apply {
        moveTo(x - unit * 0.02f, y + unit * 0.12f)
        quadraticTo(x + unit * 0.22f, y + unit * 0.55f, x + unit * 0.55f, y + unit * 0.42f)
        lineTo(x + unit * 0.82f, y + unit * 0.88f)
        lineTo(x + unit * 0.62f, y + unit * 0.95f)
        quadraticTo(x + unit * 0.18f, y + unit * 0.52f, x - unit * 0.12f, y + unit * 0.22f)
        close()
    }
    drawPath(frontLeg, skin)
    drawOval(
        color = shoe,
        topLeft = Offset(x + unit * 0.58f, y + unit * 0.82f),
        size = Size(unit * 0.42f, unit * 0.18f),
    )

    val backArm = Path().apply {
        moveTo(x - unit * 0.05f, y - unit * 0.22f)
        quadraticTo(x - unit * 0.55f, y - unit * 0.05f, x - unit * 0.72f, y + unit * 0.28f)
        lineTo(x - unit * 0.55f, y + unit * 0.34f)
        quadraticTo(x - unit * 0.32f, y - unit * 0.02f, x + unit * 0.08f, y - unit * 0.12f)
        close()
    }
    drawPath(backArm, skin)

    drawOval(
        color = shirt,
        topLeft = Offset(x - unit * 0.28f, y - unit * 0.42f),
        size = Size(unit * 0.52f, unit * 0.72f),
    )
    drawOval(
        color = shorts,
        topLeft = Offset(x - unit * 0.22f, y + unit * 0.12f),
        size = Size(unit * 0.38f, unit * 0.28f),
    )

    val frontArm = Path().apply {
        moveTo(x + unit * 0.12f, y - unit * 0.22f)
        quadraticTo(x + unit * 0.48f, y - unit * 0.38f, x + unit * 0.78f, y - unit * 0.18f)
        lineTo(x + unit * 0.70f, y - unit * 0.04f)
        quadraticTo(x + unit * 0.38f, y - unit * 0.22f, x + unit * 0.05f, y - unit * 0.08f)
        close()
    }
    drawPath(frontArm, skin)
    drawCircle(skin, radius = unit * 0.09f, center = Offset(x + unit * 0.78f, y - unit * 0.12f))
    drawCircle(skin, radius = unit * 0.09f, center = Offset(x - unit * 0.70f, y + unit * 0.30f))

    drawCircle(skin, radius = unit * 0.22f, center = Offset(x + unit * 0.08f, y - unit * 0.58f))
    drawOval(
        color = hair,
        topLeft = Offset(x - unit * 0.08f, y - unit * 0.80f),
        size = Size(unit * 0.38f, unit * 0.28f),
    )
    drawCircle(Ink, radius = unit * 0.04f, center = Offset(x + unit * 0.16f, y - unit * 0.58f))
}
