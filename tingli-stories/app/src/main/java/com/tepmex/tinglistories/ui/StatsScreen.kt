package com.tepmex.tinglistories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tepmex.tinglistories.domain.countedListenCount
import com.tepmex.tinglistories.domain.formatStat
import com.tepmex.tinglistories.domain.listenScore
import com.tepmex.tinglistories.domain.listensLabel
import com.tepmex.tinglistories.domain.meanIndicator
import com.tepmex.tinglistories.domain.StoryProgress
import com.tepmex.tinglistories.domain.showStoryTitle
import com.tepmex.tinglistories.domain.statsAccounting
import com.tepmex.tinglistories.domain.storyStats
import com.tepmex.tinglistories.domain.unansweredListenCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(state: TingliUiState, onBack: () -> Unit) {
    val library = state.library
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Статистика") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )
        if (library.stories.isEmpty()) {
            Text(
                "Импортируйте архив, чтобы увидеть статистику.",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            val points = storyStats(library)
            val mean = meanIndicator(points)
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    if (mean != null) {
                        Text(
                            "Средний показатель ${formatStat(mean)}",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                    Text(
                        statsAccounting(
                            countedListenCount(library),
                            points.size,
                            unansweredListenCount(library),
                        ),
                        style = if (mean == null) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        color = if (mean == null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        "Пройдено ${library.completedCount} из ${library.stories.size}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Показатель — процент верных ответов, делённый на число прослушиваний. История без ответа в расчёт не входит.",
                        modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (points.isNotEmpty()) {
                        StatsChart(
                            points = points,
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                    }
                }
                itemsIndexed(library.stories, key = { _, story -> story.id }) { index, story ->
                    val progress = library.progressOf(story.id)
                    val score = listenScore(progress)
                    val excluded = progress.evaluation == null
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            modifier = Modifier.padding(end = 12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (showStoryTitle(progress)) {
                            Text(
                                story.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            statsRowValue(progress, score),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (excluded || score == null) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private fun statsRowValue(progress: StoryProgress, score: Double?): String {
    if (score != null) return "${formatStat(score)} · ${listensLabel(progress.listenCount)}"
    if (progress.evaluation == null) {
        return if (progress.listenCount > 0) {
            "${listensLabel(progress.listenCount)}, без ответа"
        } else {
            "без ответа"
        }
    }
    return if (progress.listenCount > 0) {
        "${listensLabel(progress.listenCount)}, нет процента"
    } else {
        "нет показателя"
    }
}
