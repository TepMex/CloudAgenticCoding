package com.tepmex.zoulushang2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tepmex.zoulushang2.brush.BrushSettings

@Composable
fun BrushToolbar(
    colorArgb: Int,
    thicknessMeters: Float,
    onColorChange: (Int) -> Unit,
    onThicknessChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(end = 4.dp),
                )
                BrushSettings.PALETTE.forEach { paletteColor ->
                    ColorSwatch(
                        colorArgb = paletteColor,
                        selected = paletteColor == colorArgb,
                        onClick = { onColorChange(paletteColor) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Size",
                    style = MaterialTheme.typography.labelMedium,
                )
                Slider(
                    value = thicknessMeters,
                    onValueChange = onThicknessChange,
                    valueRange = BrushSettings.MIN_THICKNESS_METERS..BrushSettings.MAX_THICKNESS_METERS,
                    modifier = Modifier.weight(1f),
                )
                BrushPreview(
                    colorArgb = colorArgb,
                    thicknessMeters = thicknessMeters,
                )
                Text(
                    text = "${thicknessMeters.toInt()} m",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(
    colorArgb: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }
    val borderWidth = if (selected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color(colorArgb))
            .border(BorderStroke(borderWidth, borderColor), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun BrushPreview(
    colorArgb: Int,
    thicknessMeters: Float,
    modifier: Modifier = Modifier,
) {
    val previewRadius = (thicknessMeters / BrushSettings.MAX_THICKNESS_METERS * 14f).coerceIn(3f, 14f)
    Canvas(modifier = modifier.size(32.dp)) {
        drawCircle(
            color = Color(colorArgb),
            radius = previewRadius,
            center = center,
        )
    }
}
