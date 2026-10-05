package com.tepmex.tinglistories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tepmex.tinglistories.domain.AudioKind
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.formatPlayback
import com.tepmex.tinglistories.domain.listensLabel
import com.tepmex.tinglistories.domain.nextStoryId
import com.tepmex.tinglistories.domain.showStoryTitle
import com.tepmex.tinglistories.domain.showTextBeforeAnswer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenScreen(
    state: TingliUiState,
    story: Story,
    onBack: () -> Unit,
    onPlay: (AudioKind) -> Unit,
    onSeek: (Int) -> Unit,
    onAnswer: () -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = state.library.progressOf(story.id)
    val index = state.library.stories.indexOfFirst { it.id == story.id }
    val position = if (index < 0) "" else "${index + 1} / ${state.library.stories.size}"
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(position) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showStoryTitle(progress)) {
                Text(story.title, style = MaterialTheme.typography.titleLarge)
            }
            Text(
                listensLabel(progress.listenCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PlayRow(
                title = "история",
                enabled = story.hasStoryAudio,
                playing = state.playing == Playing(story.id, AudioKind.STORY),
                onClick = { onPlay(AudioKind.STORY) },
            )
            if (!story.hasStoryAudio) {
                Text(
                    "В архиве нет аудио истории — текст открыт.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PlayRow(
                title = "вопросы",
                enabled = story.hasQuestionsAudio,
                playing = state.playing == Playing(story.id, AudioKind.QUESTIONS),
                onClick = { onPlay(AudioKind.QUESTIONS) },
            )
            if (!story.hasQuestionsAudio) {
                Text(
                    "В архиве нет аудио вопросов. Формулировки откроются по кнопке «Ответить».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val playingNow = state.playing?.takeIf { it.storyId == story.id }
            if (playingNow != null) {
                PlaybackBar(
                    caption = if (playingNow.kind == AudioKind.STORY) "История" else "Вопросы",
                    positionMs = state.playbackPositionMs,
                    durationMs = state.playbackDurationMs,
                    onSeek = onSeek,
                )
            }
            if (showTextBeforeAnswer(story) && story.text.isNotBlank()) {
                SelectionContainer {
                    Text(story.text, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (progress.completed) {
                val grade = progress.evaluation
                Text(
                    "Пройдена · ${grade?.score ?: 0} из ${grade?.max ?: story.questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                if (!grade?.summary.isNullOrBlank()) {
                    Text(grade?.summary.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Button(
                onClick = onAnswer,
                enabled = story.questions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(if (progress.completed) "Ответ и оценка" else "Ответить")
            }
            if (progress.completed) {
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("Сбросить ответ")
                }
                OutlinedButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    val next = nextStoryId(state.library.stories, story.id)
                    Text(if (next == null) "К списку" else "Следующая история")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private const val SEEK_STEP_MS = 10_000

@Composable
private fun PlaybackBar(
    caption: String,
    positionMs: Int,
    durationMs: Int,
    onSeek: (Int) -> Unit,
) {
    var dragging by remember(caption) { mutableStateOf(false) }
    var dragPosition by remember(caption) { mutableIntStateOf(positionMs) }
    val shown = if (dragging) dragPosition else positionMs
    val rangeEnd = durationMs.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            caption,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalIconButton(onClick = { onSeek(shown - SEEK_STEP_MS) }) {
                Icon(Icons.Filled.Replay10, contentDescription = "На 10 секунд назад")
            }
            Text(
                "${formatPlayback(shown)} / ${formatPlayback(durationMs)}",
                style = MaterialTheme.typography.titleMedium,
            )
            FilledTonalIconButton(onClick = { onSeek(shown + SEEK_STEP_MS) }) {
                Icon(Icons.Filled.Forward10, contentDescription = "На 10 секунд вперёд")
            }
        }
        Slider(
            value = shown.coerceIn(0, rangeEnd).toFloat(),
            onValueChange = { next ->
                dragging = true
                dragPosition = next.toInt()
            },
            onValueChangeFinished = {
                onSeek(dragPosition)
                dragging = false
            },
            valueRange = 0f..rangeEnd.toFloat(),
            enabled = durationMs > 0,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Место воспроизведения" },
        )
    }
}

@Composable
private fun PlayRow(title: String, enabled: Boolean, playing: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(64.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Text(if (playing) "Play · $title · играет" else "Play · $title")
        }
    }
}
