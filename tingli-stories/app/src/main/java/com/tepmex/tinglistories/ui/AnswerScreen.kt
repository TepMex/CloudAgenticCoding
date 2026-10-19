package com.tepmex.tinglistories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.tinglistories.domain.AudioKind
import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.alignedAnswers
import com.tepmex.tinglistories.domain.nextStoryId
import com.tepmex.tinglistories.domain.showQuestionWording
import com.tepmex.tinglistories.domain.showStoryTitle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnswerScreen(
    state: TingliUiState,
    story: Story,
    onBack: () -> Unit,
    onDraft: (Int, String) -> Unit,
    onSubmit: () -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
    onReplay: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    val progress = state.library.progressOf(story.id)
    val completed = progress.completed
    val answers = if (completed) {
        alignedAnswers(story.questions.size, progress.answers)
    } else {
        alignedAnswers(story.questions.size, state.draftAnswers)
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (completed) "Оценка" else "Ответ") },
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
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showStoryTitle(progress)) {
                Text(story.title, style = MaterialTheme.typography.titleLarge)
            }
            if (!completed) {
                val hint = if (showQuestionWording(story, progress)) {
                    "Можно отвечать по-русски или по-китайски. Эталон откроется после проверки."
                } else {
                    "Можно отвечать по-русски или по-китайски. Формулировки и эталон откроются после проверки."
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val showPrompt = showQuestionWording(story, progress)
            story.questions.forEachIndexed { index, question ->
                QuestionBlock(
                    number = index + 1,
                    prompt = if (showPrompt) question.prompt else null,
                    answer = answers.getOrElse(index) { "" },
                    reference = if (completed) question.reference else null,
                    comment = progress.evaluation?.items?.find { it.index == index + 1 },
                    readOnly = completed,
                    onValue = { onDraft(index, it) },
                )
            }
            if (completed) {
                val grade = progress.evaluation
                if (grade != null) {
                    GradeBlock(grade)
                }
                if (story.text.isNotBlank() || story.hasStoryAudio) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (story.text.isNotBlank()) {
                            Text("Текст истории", style = MaterialTheme.typography.titleMedium)
                        }
                        if (story.hasStoryAudio) {
                            TextButton(onClick = onReplay) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Прослушать ещё раз")
                            }
                        }
                    }
                    if (story.text.isNotBlank()) {
                        SelectionContainer {
                            Text(story.text, style = MaterialTheme.typography.bodyLarge)
                        }
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
                }
                OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
                    Text("Сбросить ответ")
                }
                Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    val next = nextStoryId(state.library.stories, story.id)
                    Text(if (next == null) "К списку" else "Следующая история")
                }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = !state.busy && story.questions.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    if (state.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Проверить")
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun QuestionBlock(
    number: Int,
    prompt: String?,
    answer: String,
    reference: String?,
    comment: com.tepmex.tinglistories.domain.EvaluationItem?,
    readOnly: Boolean,
    onValue: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = if (prompt.isNullOrBlank()) number.toString() else "$number. $prompt",
            style = if (prompt.isNullOrBlank()) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
        )
        OutlinedTextField(
            value = answer,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            minLines = 2,
            label = { Text("Ответ $number") },
        )
        if (reference != null) {
            Text(
                "Эталон: $reference",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (comment != null) {
            val tone = if (comment.correct) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
            val mark = if (comment.correct) "Верно" else "Неверно"
            Text(
                listOf(mark, comment.comment).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = tone,
            )
        }
    }
}

@Composable
private fun GradeBlock(grade: Evaluation) {
    Text(
        "${grade.score} из ${grade.max}",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.secondary,
    )
    if (grade.summary.isNotBlank()) {
        Text(grade.summary, style = MaterialTheme.typography.bodyLarge)
    }
}
