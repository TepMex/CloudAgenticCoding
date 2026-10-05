package com.tepmex.cornegame.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.tepmex.cornegame.domain.Board
import com.tepmex.cornegame.domain.Finger
import com.tepmex.cornegame.domain.KeyCell
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.ui.theme.HighlightYellow

private val ErrorRed = Color(0xFFE53935)
private val KeyInk = Color(0xFF1C1C1C)

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
    val thumbWidth: Float,
    val thumbHeight: Float,
    val width: Float,
    val height: Float,
)

internal fun fitKeyboard(
    maxWidth: Float,
    maxHeight: Float,
    keyScale: Float,
    gap: Float = 4f,
    centerGap: Float = 22f,
): KeyboardFit {
    val scale = keyScale.coerceIn(0.65f, 1f)
    val halfWidth = (maxWidth - centerGap) / 2f
    val keyFromWidth = (halfWidth - gap * 5f) / 6f
    val thumbFactor = 0.9f
    val keyFromHeight = (maxHeight - gap * 3f) / (3f + thumbFactor)
    val key = minOf(keyFromWidth, keyFromHeight).coerceAtLeast(1f) * scale
    val thumbHeight = key * thumbFactor
    val thumbWidth = key * 2f + gap
    val width = (key * 6f + gap * 5f) * 2f + centerGap
    val height = key * 3f + thumbHeight + gap * 3f
    return KeyboardFit(key, thumbWidth, thumbHeight, width, height)
}

/**
 * Обе половинки вписываются в доступный прямоугольник.
 * [keyScale] только уменьшает уже вписанный размер, за край экран не вылезает.
 */
@Composable
fun CorneKeyboard(
    board: Board,
    nextChar: Char?,
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
            .drawWithContent {
                drawContent()
                if (flashAlpha > 0f) {
                    drawRect(ErrorRed.copy(alpha = flashAlpha * 0.35f))
                }
            },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val gap = 4.dp
            val centerGap = 22.dp
            val fit = fitKeyboard(
                maxWidth = maxWidth.value,
                maxHeight = maxHeight.value,
                keyScale = keyScale,
                gap = gap.value,
                centerGap = centerGap.value,
            )
            val key = fit.key.dp
            val thumbHeight = fit.thumbHeight.dp
            val thumbWidth = fit.thumbWidth.dp
            val highlight = if (highlightEnabled && nextChar != null) {
                board.highlightCell(nextChar)
            } else {
                null
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(centerGap)) {
                    KeyboardHalf(
                        rows = board.leftRows,
                        thumbs = board.leftThumbs,
                        keySize = key,
                        thumbWidth = thumbWidth,
                        thumbHeight = thumbHeight,
                        gap = gap,
                        highlight = highlight,
                        fingerColors = fingerColors,
                        flashAlpha = flashAlpha,
                        lastWrong = lastWrong,
                        onAction = onAction,
                    )
                    KeyboardHalf(
                        rows = board.rightRows,
                        thumbs = board.rightThumbs,
                        keySize = key,
                        thumbWidth = thumbWidth,
                        thumbHeight = thumbHeight,
                        gap = gap,
                        highlight = highlight,
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
    rows: List<List<KeyCell>>,
    thumbs: List<KeyCell>,
    keySize: Dp,
    thumbWidth: Dp,
    thumbHeight: Dp,
    gap: Dp,
    highlight: KeyCell?,
    fingerColors: Boolean,
    flashAlpha: Float,
    lastWrong: Char?,
    onAction: (TrainerAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { cell ->
                    KeyButton(
                        cell = cell,
                        width = keySize,
                        height = keySize,
                        highlighted = cell == highlight,
                        fingerColors = fingerColors,
                        flashAlpha = flashAlpha,
                        lastWrong = lastWrong,
                        onAction = onAction,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            thumbs.forEach { cell ->
                KeyButton(
                    cell = cell,
                    width = thumbWidth,
                    height = thumbHeight,
                    highlighted = cell == highlight,
                    fingerColors = fingerColors,
                    flashAlpha = flashAlpha,
                    lastWrong = lastWrong,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun KeyButton(
    cell: KeyCell,
    width: Dp,
    height: Dp,
    highlighted: Boolean,
    fingerColors: Boolean,
    flashAlpha: Float,
    lastWrong: Char?,
    onAction: (TrainerAction) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val base = when {
        fingerColors -> cell.finger.zoneColor()
        else -> scheme.surfaceVariant
    }
    val wrong = lastWrong != null &&
        (cell.action as? TrainerAction.Character)?.value == lastWrong &&
        flashAlpha > 0f
    val background = when {
        highlighted -> HighlightYellow
        wrong -> lerp(base, ErrorRed, flashAlpha.coerceIn(0f, 1f))
        else -> base
    }
    val foreground = if (highlighted || fingerColors) KeyInk else scheme.onSurface
    val shape = RoundedCornerShape(8.dp)
    val action = cell.action
    val labelSize = (
        height.value * if (cell.legend.length <= 1) 0.42f else 0.28f
        ).coerceIn(8f, 22f).sp
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(shape)
            .background(background)
            .border(
                width = if (highlighted) 2.dp else 1.dp,
                color = if (highlighted) Color(0xFF8A6A00) else scheme.outline.copy(alpha = 0.45f),
                shape = shape,
            )
            .semantics { contentDescription = "Клавиша ${cell.legend}" }
            .then(
                if (action != null) {
                    Modifier.clickable { onAction(action) }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = cell.legend,
            color = if (action == null) foreground.copy(alpha = 0.55f) else foreground,
            fontSize = labelSize,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
