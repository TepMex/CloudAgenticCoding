package com.tepmex.cornegame.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.topMisses

@Composable
fun ResultsScreen(
    state: TrainUiState,
    onAgain: () -> Unit,
    onLanguage: (Language) -> Unit,
    onOpenProgress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = state.session.stats(state.nowMs)
    val misses = topMisses(state.session.misses)
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Готово", style = MaterialTheme.typography.headlineMedium)
        LanguageSwitch(selected = state.settings.language, onLanguage = onLanguage)
        Text(
            text = formatWpm(stats.wpm),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text("WPM", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Summary("Точность", formatAccuracy(stats.accuracyPercent))
            Summary("Ошибки", stats.errors.toString())
            Summary("Время", formatDuration(stats.elapsedMs))
            Summary("Язык", state.session.language.name)
        }
        Spacer(Modifier.height(8.dp))
        Text("Промахи", style = MaterialTheme.typography.titleLarge)
        Text(
            "Топ-10 символов и что нажималось вместо нужного",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (misses.isEmpty()) {
            Text("Промахов не было")
        } else {
            misses.forEach { bucket ->
                val instead = bucket.pressedInstead.joinToString(", ") { sub ->
                    "${formatChar(sub.actual)} ×${sub.count}"
                }
                Text(
                    text = "${formatChar(bucket.expected)}  ${bucket.total}   вместо: $instead",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAgain, modifier = Modifier.fillMaxWidth()) {
            Text("Ещё раз")
        }
        Button(
            onClick = onOpenProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Прогресс")
        }
    }
}

@Composable
private fun Summary(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
