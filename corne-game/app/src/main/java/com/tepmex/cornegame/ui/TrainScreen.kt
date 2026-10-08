package com.tepmex.cornegame.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.LiveStats
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.keyboardModel
import com.tepmex.cornegame.ui.theme.typedColor

@Composable
fun TrainScreen(
    state: TrainUiState,
    onAction: (TrainerAction) -> Unit,
    onLanguage: (Language) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Corne",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.width(8.dp))
            LanguageSwitch(
                selected = state.settings.language,
                onLanguage = onLanguage,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onOpenProgress) { Text("Прогресс") }
            TextButton(onClick = onOpenSettings) { Text("Настройки") }
        }
        TargetLine(
            target = state.session.target,
            index = state.session.index,
        )
        Spacer(Modifier.height(4.dp))
        StatsRow(stats = state.session.stats(state.nowMs))
        val model = keyboardModel(state.session)
        Text(
            text = model.caption,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        CorneKeyboard(
            model = model,
            highlightEnabled = state.settings.highlight,
            fingerColors = state.settings.fingerColors,
            keyScale = state.settings.keyScale,
            errorNonce = state.errorNonce,
            lastWrong = state.lastWrong,
            onAction = onAction,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
    }
}

@Composable
fun LanguageSwitch(
    selected: Language,
    onLanguage: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(
            selected = selected == Language.EN,
            onClick = { onLanguage(Language.EN) },
            label = { Text("EN") },
        )
        FilterChip(
            selected = selected == Language.RU,
            onClick = { onLanguage(Language.RU) },
            label = { Text("RU") },
        )
    }
}

@Composable
private fun TargetLine(target: String, index: Int) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val fontSize = 22.sp
    val typed = typedColor()
    val pending = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    LaunchedEffect(index) {
        val charPx = with(density) { fontSize.toPx() } * 0.62f
        val destination = (index * charPx - 24f).toInt().coerceAtLeast(0)
        scroll.animateScrollTo(destination)
    }
    val safeIndex = index.coerceIn(0, target.length)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = typed, fontWeight = FontWeight.SemiBold)) {
                append(target.substring(0, safeIndex))
            }
            withStyle(SpanStyle(color = pending)) {
                append(target.substring(safeIndex))
            }
        },
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .horizontalScroll(scroll)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun StatsRow(stats: LiveStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("WPM", formatWpm(stats.wpm), Modifier.weight(1f))
        Stat("Точность", formatAccuracy(stats.accuracyPercent), Modifier.weight(1f))
        Stat("Ошибки", stats.errors.toString(), Modifier.weight(1f))
        Stat("Время", formatDuration(stats.elapsedMs), Modifier.weight(1f))
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
