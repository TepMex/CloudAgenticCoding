package com.tepmex.tinglistories.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TingliApp(viewModel: TingliViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFromUri(uri)
    }
    LaunchedEffect(state.message) {
        val text = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        viewModel.dismissMessage()
    }
    BackHandler(enabled = state.screen != Screen.Library || state.confirmReplace || state.confirmReset) {
        viewModel.back()
    }
    if (state.confirmReplace) {
        AlertDialog(
            onDismissRequest = viewModel::cancelReplace,
            title = { Text("Заменить набор?") },
            text = {
                Text(
                    "Текущие истории будут заменены. Число прослушиваний сохранится у историй с тем же номером. Ответ и оценка сохранятся, только если текст и вопросы не изменились.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmReplace) { Text("Заменить") } },
            dismissButton = { TextButton(onClick = viewModel::cancelReplace) { Text("Отмена") } },
        )
    }
    if (state.confirmReset) {
        AlertDialog(
            onDismissRequest = viewModel::cancelReset,
            title = { Text("Сбросить ответ?") },
            text = { Text("Оценка исчезнет, историю можно будет ответить заново. Счётчик прослушиваний останется.") },
            confirmButton = { TextButton(onClick = viewModel::confirmReset) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = viewModel::cancelReset) { Text("Отмена") } },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                if (!state.ready) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    when (val screen = state.screen) {
                        Screen.Library -> LibraryScreen(
                            state = state,
                            onOpen = viewModel::openStory,
                            onImport = {
                                importZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                            },
                            onSample = viewModel::importSample,
                            onStats = viewModel::openStats,
                            onSettings = viewModel::openSettings,
                        )
                        is Screen.Listen -> StoryPage(state, screen.storyId, missing = { viewModel.back() }) { story ->
                            ListenScreen(
                                state = state,
                                story = story,
                                onBack = viewModel::back,
                                onPlay = { viewModel.play(story.id, it) },
                                onSeek = viewModel::seekPlayback,
                                onAnswer = { viewModel.openAnswer(story.id) },
                                onReset = viewModel::requestReset,
                                onNext = { viewModel.goNext(story.id) },
                            )
                        }
                        is Screen.Answer -> StoryPage(state, screen.storyId, missing = { viewModel.back() }) { story ->
                            AnswerScreen(
                                state = state,
                                story = story,
                                onBack = viewModel::back,
                                onDraft = viewModel::onDraftChange,
                                onSubmit = { viewModel.submit(story.id) },
                                onReset = viewModel::requestReset,
                                onNext = { viewModel.goNext(story.id) },
                            )
                        }
                        Screen.Stats -> StatsScreen(state = state, onBack = viewModel::back)
                        Screen.Settings -> SettingsScreen(
                            settings = state.settings,
                            showToken = state.showToken,
                            onToggleToken = viewModel::toggleToken,
                            onBack = viewModel::back,
                            onSave = viewModel::saveSettings,
                        )
                    }
                }
                if (state.busy) {
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryPage(
    state: TingliUiState,
    storyId: Int,
    missing: () -> Unit,
    content: @Composable (com.tepmex.tinglistories.domain.Story) -> Unit,
) {
    val story = state.library.story(storyId)
    if (story == null) {
        LaunchedEffect(storyId) { missing() }
    } else {
        content(story)
    }
}
