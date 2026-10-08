package com.tepmex.cornegame.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.cornegame.domain.Finger
import com.tepmex.cornegame.domain.KEY_COLS
import com.tepmex.cornegame.domain.KEY_COUNT
import com.tepmex.cornegame.domain.KEY_ROWS
import com.tepmex.cornegame.domain.KeyFace
import com.tepmex.cornegame.domain.KeyboardModel
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.ui.theme.HighlightYellow

private val ErrorRed = Color(0xFFE53935)
private val KeyInk = Color(0xFF1C1C1C)

/** Доля высоты клавиши, на которую колонка опущена. Как в info.json Cornedeon 2M. */
internal val COLUMN_STAGGER = floatArrayOf(0.30f, 0.30f, 0.10f, 0.00f, 0.10f, 0.20f)

/** Пастельные зоны. Жёлтый оставлен подсветке следующей клавиши, не пальцу. */
fun Finger.zoneColor(): Color = when (this) {
    Finger.LEFT_PINKY -> Color(0xFFEF9A9A)
    Finger.LEFT_RING -> Color(0xFFFFCC80)
    Finger.LEFT_MIDDLE -> Color(0xFFA5D6A7)
    Finger.LEFT_INDEX -> Color(0xFF90CAF9)
    Finger.RIGHT_INDEX -> Color(0xFFCE93D8)
    Finger.RIGHT_MIDDLE -> Color(0xFF80CBC4)
    Finger.RIGHT_RING -> Color(0xFFF48FB1)
    Finger.RIGHT_PINKY -> Color(0xFFBCAAA4)
    Finger.LEFT_THUMB -> Color(0xFF9FA8DA)
    Finger.RIGHT_THUMB -> Color(0xFFC5E1A5)
}

fun fingerLabel(finger: Finger): String = when (finger) {
    Finger.LEFT_PINKY -> "ЛМ"
    Finger.LEFT_RING -> "ЛБ"
    Finger.LEFT_MIDDLE -> "ЛС"
    Finger.LEFT_INDEX -> "ЛУ"
    Finger.RIGHT_INDEX -> "ПУ"
    Finger.RIGHT_MIDDLE -> "ПС"
    Finger.RIGHT_RING -> "ПБ"
    Finger.RIGHT_PINKY -> "ПМ"
    Finger.LEFT_THUMB -> "ЛБП"
    Finger.RIGHT_THUMB -> "ПБП"
}

/** Размеры в тех же единицах, что и вход (в UI это dp). Клавиатура не выше и не шире ящика. */
internal data class KeyboardFit(
    val key: Float,
    val width: Float,
    val height: Float,
)

internal fun fitKeyboard(
    maxWidth: Float,
    maxHeight: Float,
    keyScale: Float,
    gap: Float = 4f,
    centerGap: Float = 16f,
): KeyboardFit {
    val scale = keyScale.coerceIn(0.65f, 1f)
    val stagger = COLUMN_STAGGER.max()
    val halfWidth = (maxWidth - centerGap) / 2f
    val keyFromWidth = (halfWidth - gap * (KEY_COLS - 1)) / KEY_COLS
    val keyFromHeight = (maxHeight - gap * (KEY_ROWS - 1)) / (KEY_ROWS + stagger)
    val key = minOf(keyFromWidth, keyFromHeight).coerceAtLeast(1f) * scale
    val width = (key * KEY_COLS + gap * (KEY_COLS - 1)) * 2f + centerGap
    val height = key * (KEY_ROWS + stagger) + gap * (KEY_ROWS - 1)
    return KeyboardFit(key, width, height)
}

/**
 * Обе половинки, все 48 клавиш, включая L1 и L2.
 * [keyScale] только уменьшает уже вписанный размер, за край экран не вылезает.
 */
@Composable
fun CorneKeyboard(
    model: KeyboardModel,
    highlightEnabled: Boolean,
    fingerColors: Boolean,
    keyScale: Float,
    errorNonce: Int,
    lastWrong: Char?,
    onAction: (TrainerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flash = remember { Animatable(0f) }
    LaunchedEffect(errorNonce) {
        if (errorNonce == 0) return@LaunchedEffect
        flash.snapTo(0.42f)
        flash.animateTo(0f, animationSpec = tween(durationMillis = 220))
    }
    val flashAlpha = flash.value
    Box(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Клавиатура $KEY_COUNT" }
            .drawWithContent {
                drawContent()
                if (flashAlpha > 0f) {
                    drawRect(ErrorRed.copy(alpha = flashAlpha * 0.35f))
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = 3.dp
            val centerGap = 12.dp
            val fit = fitKeyboard(
                maxWidth = maxWidth.value,
                maxHeight = maxHeight.value,
                keyScale = keyScale,
                gap = gap.value,
                centerGap = centerGap.value,
            )
            val key = fit.key.dp
            val stagger = (COLUMN_STAGGER.max() * fit.key).dp
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(centerGap)) {
                    KeyboardHalf(
                        rows = model.leftRows,
                        keySize = key,
                        stagger = stagger,
                        gap = gap,
                        highlightEnabled = highlightEnabled,
                        fingerColors = fingerColors,
                        flashAlpha = flashAlpha,
                        lastWrong = lastWrong,
                        onAction = onAction,
                    )
                    KeyboardHalf(
                        rows = model.rightRows,
                        keySize = key,
                        stagger = stagger,
                        gap = gap,
                        highlightEnabled = highlightEnabled,
                        fingerColors = fingerColors,
                        flashAlpha = flashAlpha,
                        lastWrong = lastWrong,
                        onAction = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyboardHalf(
    rows: List<List<KeyFace>>,
    keySize: Dp,
    stagger: Dp,
    gap: Dp,
    highlightEnabled: Boolean,
    fingerColors: Boolean,
    flashAlpha: Float,
    lastWrong: Char?,
    onAction: (TrainerAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.Top,
            ) {
                row.forEach { face ->
                    val drop = (COLUMN_STAGGER[face.column] * keySize.value).dp
                    Box(Modifier.size(keySize, keySize + stagger)) {
                        KeyButton(
                            face = face,
                            highlighted = highlightEnabled && face.primary,
                            hinted = highlightEnabled && face.hinted,
                            fingerColors = fingerColors,
                            flashAlpha = flashAlpha,
                            lastWrong = lastWrong,
                            onAction = onAction,
                            modifier = Modifier
                                .offset(y = drop)
                                .size(keySize),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    face: KeyFace,
    highlighted: Boolean,
    hinted: Boolean,
    fingerColors: Boolean,
    flashAlpha: Float,
    lastWrong: Char?,
    onAction: (TrainerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val base = when {
        fingerColors -> face.finger.zoneColor()
        else -> scheme.surfaceVariant
    }
    val wrong = lastWrong != null && flashAlpha > 0f && faceProduces(face, lastWrong)
    val background = when {
        highlighted -> HighlightYellow
        hinted -> lerp(base, HighlightYellow, 0.55f)
        wrong -> lerp(base, ErrorRed, flashAlpha.coerceIn(0f, 1f))
        else -> base
    }
    val foreground = if (highlighted || hinted || fingerColors) KeyInk else scheme.onSurface
    val shape = RoundedCornerShape(6.dp)
    val labelSize = (
        when {
            face.legend.length <= 1 -> 0.42f
            face.legend.length <= 3 -> 0.30f
            else -> 0.20f
        } * if (face.holdLegend != null) 0.82f else 1f
        ).coerceIn(7f, 22f).sp
    val layerKey = face.legend == "L1" || face.legend == "L2"
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = when {
                    highlighted -> Color(0xFF8A6A00)
                    hinted -> Color(0xFF8A6A00).copy(alpha = 0.7f)
                    else -> scheme.outline.copy(alpha = 0.45f)
                },
                shape = shape,
            )
            .semantics { contentDescription = "Клавиша ${face.legend.ifEmpty { face.id.toString() }}" }
            .combinedClickable(
                onClick = { onAction(face.tap) },
                onLongClick = face.longPress?.let { action -> { onAction(action) } },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = face.legend,
                color = foreground,
                fontSize = labelSize,
                fontWeight = if (highlighted || layerKey) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (face.holdLegend != null) {
                Text(
                    text = face.holdLegend,
                    color = if (face.holdCue) Color(0xFF8A6A00) else foreground.copy(alpha = 0.72f),
                    fontSize = (labelSize.value * 0.72f).coerceAtLeast(7f).sp,
                    fontWeight = if (face.holdCue) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun faceProduces(face: KeyFace, char: Char): Boolean {
    val tap = (face.tap as? TrainerAction.Character)?.value
    val hold = (face.longPress as? TrainerAction.Character)?.value
    return tap == char || hold == char
}
