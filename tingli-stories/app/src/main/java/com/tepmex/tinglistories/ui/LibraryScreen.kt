package com.tepmex.tinglistories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.tinglistories.domain.audioCoverageNote
import com.tepmex.tinglistories.domain.listensLabel
import com.tepmex.tinglistories.domain.showStoryTitle
import com.tepmex.tinglistories.domain.storiesLabel
import com.tepmex.tinglistories.domain.storySubtitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: TingliUiState,
    onOpen: (Int) -> Unit,
    onImport: () -> Unit,
    onSample: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val library = state.library
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("听力") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            actions = {
                IconButton(onClick = onImport, enabled = !state.busy) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = "Импорт zip")
                }
                IconButton(onClick = onSample, enabled = !state.busy) {
                    Icon(Icons.Filled.School, contentDescription = "Пример HSK 4")
                }
                IconButton(onClick = onStats) {
                    Icon(Icons.Filled.BarChart, contentDescription = "Статистика")
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                }
            },
        )
        if (library.stories.isEmpty()) {
            EmptyLibrary(onImport = onImport, onSample = onSample, enabled = !state.busy)
        } else {
            val note = audioCoverageNote(library)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text(
                        text = listOfNotNull(
                            library.level.ifBlank { null },
                            storiesLabel(library.stories.size),
                            "пройдено ${library.completedCount}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (note != null) {
                        Text(
                            text = note,
                            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                }
                itemsIndexed(library.stories, key = { _, story -> story.id }) { index, story ->
                    val progress = library.progressOf(story.id)
                    Surface(
                        onClick = { onOpen(story.id) },
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                color = if (progress.completed) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                if (showStoryTitle(progress)) {
                                    Text(story.title, style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    storySubtitle(story, progress),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                listensLabel(progress.listenCount),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onImport: () -> Unit, onSample: () -> Unit, enabled: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("听", style = TextStyle(fontSize = 72.sp), color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            "Импортируйте zip с историями, вопросами и аудио.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onImport, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Импорт zip")
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onSample, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text("Пример HSK 4")
        }
    }
}
