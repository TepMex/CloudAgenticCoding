package com.tepmex.myweek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tepmex.myweek.R

internal object BlockTints {
    private val base = mapOf(
        "blue" to Color(0xFF3B82F6),
        "teal" to Color(0xFF14B8A6),
        "green" to Color(0xFF22C55E),
        "amber" to Color(0xFFEAB308),
        "orange" to Color(0xFFF97316),
        "rose" to Color(0xFFF43F5E),
        "violet" to Color(0xFF8B5CF6),
        "slate" to Color(0xFF64748B),
    )

    fun base(id: String): Color = base[id] ?: base.getValue("blue")

    @Composable
    fun fill(id: String): Color {
        val alpha = if (isSystemInDarkTheme()) 0.38f else 0.20f
        return base(id).copy(alpha = alpha)
    }
}

internal fun pieceShape(top: Boolean, bottom: Boolean): Shape = when {
    top && bottom -> RoundedCornerShape(16.dp)
    top -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    bottom -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    else -> RoundedCornerShape(0.dp)
}

@Composable
internal fun ChecklistHeader(
    emoji: String,
    title: String,
    colorKey: String,
    top: Boolean,
    bottom: Boolean,
    onAdd: (() -> Unit)?,
    onEdit: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(pieceShape(top, bottom))
            .background(BlockTints.fill(colorKey))
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$emoji  $title",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onAdd != null) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_task))
            }
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Create, contentDescription = stringResource(R.string.edit_block))
            }
        }
    }
}

@Composable
internal fun ChecklistTask(
    emoji: String,
    title: String,
    done: Boolean,
    colorKey: String,
    bottom: Boolean,
    onToggle: (() -> Unit)?,
    onEdit: (() -> Unit)?,
) {
    val doneLabel = stringResource(R.string.done_state)
    val openLabel = stringResource(R.string.not_done_state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(pieceShape(top = false, bottom = bottom))
            .background(BlockTints.fill(colorKey))
            .then(
                if (onToggle != null) {
                    Modifier.clickable(role = Role.Checkbox, onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .heightIn(min = 48.dp)
            .padding(start = 4.dp, end = 4.dp)
            .semantics {
                stateDescription = if (done) doneLabel else openLabel
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = done, onCheckedChange = null)
        Text(
            text = emoji,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (done) 0.55f else 1f),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 4,
        )
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Create, contentDescription = stringResource(R.string.edit_task))
            }
        }
    }
}
