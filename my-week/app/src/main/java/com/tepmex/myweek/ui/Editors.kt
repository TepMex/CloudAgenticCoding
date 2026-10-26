package com.tepmex.myweek.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tepmex.myweek.R
import com.tepmex.myweek.domain.Palette
import com.tepmex.myweek.domain.TaskText

@Composable
fun BlockEditorDialog(
    initialTitle: String,
    initialColor: String,
    initialEmoji: String,
    emojiChoices: List<String>,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var color by rememberSaveable { mutableStateOf(Palette.sanitize(initialColor)) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val valid = TaskText.blockTitle(title) != null
    EditorDialog(onDismiss) {
        Text(
            text = stringResource(if (canDelete) R.string.edit_block_title else R.string.new_block),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (confirming) {
            Text(
                text = stringResource(R.string.delete_block_confirm, title.ifBlank { "…" }),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            DialogActions(
                onCancel = { confirming = false },
                onConfirm = onDelete,
                confirmLabel = stringResource(R.string.delete),
                confirmEnabled = true,
            )
        } else {
            NameField(
                title = title,
                onTitle = { title = it },
                onDone = { if (valid) onSave(title, color, emoji) },
            )
            EmojiPicker(
                emoji = emoji,
                choices = emojiChoices,
                onEmoji = { emoji = it },
                label = stringResource(R.string.block_emoji),
                help = stringResource(R.string.block_emoji_help),
            )
            Text(
                text = stringResource(R.string.pick_color),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            ColorRow(selected = color, onPick = { color = it })
            DialogActions(
                onCancel = onDismiss,
                onConfirm = { onSave(title, color, emoji) },
                confirmLabel = stringResource(R.string.save),
                confirmEnabled = valid,
            )
            if (canDelete) {
                TextButton(onClick = { confirming = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun TaskEditorDialog(
    initialEmoji: String,
    initialTitle: String,
    emojiChoices: List<String>,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var emoji by rememberSaveable { mutableStateOf(initialEmoji) }
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    val valid = TaskText.task(emoji, title) != null
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    EditorDialog(onDismiss) {
        Text(
            text = stringResource(if (canDelete) R.string.edit_task_title else R.string.new_task),
            style = MaterialTheme.typography.headlineSmall,
        )
        if (confirming) {
            Text(
                text = stringResource(R.string.delete_task_confirm),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            DialogActions(
                onCancel = { confirming = false },
                onConfirm = onDelete,
                confirmLabel = stringResource(R.string.delete),
                confirmEnabled = true,
            )
        } else {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text(stringResource(R.string.task_description)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .focusRequester(focus),
                minLines = 1,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            EmojiPicker(
                emoji = emoji,
                choices = emojiChoices,
                onEmoji = { emoji = it },
                label = stringResource(R.string.emoji),
            )
            DialogActions(
                onCancel = onDismiss,
                onConfirm = { onSave(emoji, title) },
                confirmLabel = stringResource(R.string.save),
                confirmEnabled = valid,
            )
            if (canDelete) {
                TextButton(onClick = { confirming = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun EditorDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .imePadding()
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                Modifier
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun NameField(title: String, onTitle: (String) -> Unit, onDone: () -> Unit) {
    val focus = androidx.compose.runtime.remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = title,
        onValueChange = { onTitle(it.take(80)) },
        label = { Text(stringResource(R.string.block_name)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .focusRequester(focus),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun ColorRow(selected: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Palette.ids.forEach { id ->
            val label = colorName(id)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .border(
                        width = if (id == selected) 3.dp else 1.dp,
                        color = if (id == selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(BlockTints.base(id))
                    .clickable(onClickLabel = label) { onPick(id) },
            )
        }
    }
}

@Composable
private fun EmojiPicker(
    emoji: String,
    choices: List<String>,
    onEmoji: (String) -> Unit,
    label: String,
    help: String? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = if (help == null) 8.dp else 4.dp),
    )
    if (help != null) {
        Text(
            text = help,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    EmojiRow(choices = choices, selected = emoji, onPick = onEmoji)
    OutlinedTextField(
        value = emoji,
        onValueChange = { onEmoji(it.take(TaskText.EMOJI_LIMIT)) },
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        singleLine = true,
    )
}

@Composable
private fun EmojiRow(choices: List<String>, selected: String, onPick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        choices.forEach { emoji ->
            val picked = emoji == selected
            Text(
                text = emoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (picked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                    )
                    .clickable { onPick(emoji) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DialogActions(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.cancel))
        }
        TextButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(confirmLabel)
        }
    }
}

@Composable
private fun colorName(id: String): String = stringResource(
    when (id) {
        "teal" -> R.string.color_teal
        "green" -> R.string.color_green
        "amber" -> R.string.color_amber
        "orange" -> R.string.color_orange
        "rose" -> R.string.color_rose
        "violet" -> R.string.color_violet
        "slate" -> R.string.color_slate
        else -> R.string.color_blue
    },
)
