package com.tepmex.cornegame.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.cornegame.domain.BoardLive
import com.tepmex.cornegame.domain.DrillKind
import com.tepmex.cornegame.domain.FirmwareState
import com.tepmex.cornegame.domain.KeyModifier
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.LiveStats
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.acceptanceLine
import com.tepmex.cornegame.domain.holdRecommendation
import com.tepmex.cornegame.domain.keyboardModel
import com.tepmex.cornegame.domain.physicalLayerLabel
import com.tepmex.cornegame.domain.toFocus
import com.tepmex.cornegame.domain.virtualLayerName
import com.tepmex.cornegame.ui.theme.typedColor

@Composable
fun TrainScreen(
    state: TrainUiState,
    onLanguage: (Language) -> Unit,
    onDrill: (DrillKind) -> Unit,
    onOpenProgress: () -> Unit,
    onOpenSettings: () -> Unit,
    onContact: (TrainerAction, Boolean) -> Unit,
    onPointer: (String, Int, String, TrainerAction, Boolean?) -> Unit,
    onScreenKey: (Int, TrainerAction) -> Unit,
    onReleaseHolds: () -> Unit = {},
    firmware: FirmwareState = FirmwareState(),
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { onReleaseHolds() }
    }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Corne",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            LanguageSwitch(
                selected = state.settings.language,
                onLanguage = onLanguage,
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onOpenProgress,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Прогресс", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
            TextButton(
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("Настройки", maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
            }
        }
        DrillSwitch(selected = state.drill, onDrill = onDrill)
        val action = state.actionSession
        val drillStep = if (state.drill == DrillKind.TEXT) null else action?.current
        if (drillStep == null) {
            TargetLine(
                target = state.session.target,
                index = state.session.index,
            )
            Spacer(Modifier.height(4.dp))
            StatsRow(stats = state.session.stats(state.nowMs), textMode = true)
        } else {
            Text(drillStep.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${action?.index?.plus(1) ?: 1}/${action?.steps?.size ?: 0} · ${drillStep.hint}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            drillStep.interceptNote?.let { note ->
                Text(note, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
            val pace = action?.stats(state.nowMs)
            if (pace != null) {
                ActionStatsRow(pace)
            }
            val outcome = when (action?.lastAccepted) {
                true -> acceptanceLine(drillStep.recommendedLayer, firmware)
                false -> "Неверная клавиша или сочетание."
                null -> "Удерживайте модификаторы и нажмите основную клавишу."
            }
            Text(outcome, style = MaterialTheme.typography.labelMedium)
        }
        Text(
            "Удержано: ${heldModifierLabel(state.board)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LayerStatus(state = state, firmware = firmware)
        if (state.settings.inputDiagnostics) {
            val resolution = state.lastResolution
            if (resolution == null) {
                Text(
                    "Диагностика: событий ещё не было.",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                ResolutionPanel(resolution)
            }
        }
        val model = keyboardModel(
            state.session,
            state.board,
            state.legends,
            drillStep?.toFocus(),
        )
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
            onAction = onScreenKey,
            onContact = onContact,
            onPointer = onPointer,
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
private fun DrillSwitch(selected: DrillKind, onDrill: (DrillKind) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = selected == DrillKind.TEXT,
            onClick = { onDrill(DrillKind.TEXT) },
            label = { Text("Текст") },
        )
        FilterChip(
            selected = selected == DrillKind.SERVICE,
            onClick = { onDrill(DrillKind.SERVICE) },
            label = { Text("Клавиши") },
        )
        FilterChip(
            selected = selected == DrillKind.SHORTCUT,
            onClick = { onDrill(DrillKind.SHORTCUT) },
            label = { Text("Сочетания") },
        )
    }
}

@Composable
private fun StatsRow(stats: LiveStats, textMode: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (textMode) {
            Stat("WPM", formatWpm(stats.wpm), Modifier.weight(1f))
        }
        Stat("Точность", formatAccuracy(stats.accuracyPercent), Modifier.weight(1f))
        Stat("Ошибки", stats.errors.toString(), Modifier.weight(1f))
        Stat("Время", formatDuration(stats.elapsedMs), Modifier.weight(1f))
    }
}

@Composable
private fun ActionStatsRow(stats: com.tepmex.cornegame.domain.ActionStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Stat("Скорость", formatWpm(stats.perMinute), Modifier.weight(1f))
        Stat("Точность", formatAccuracy(stats.accuracyPercent), Modifier.weight(1f))
        Stat("Ошибки", stats.errors.toString(), Modifier.weight(1f))
        Stat("Время", formatDuration(stats.elapsedMs), Modifier.weight(1f))
    }
}

@Composable
private fun ResolutionPanel(resolution: InputResolution) {
    Text(
        text = listOf(
            "Source: ${resolution.source}",
            "Device ID: ${resolution.deviceId}",
            "Android KeyCode: ${resolution.keyCode} ${resolution.keyLabel}",
            "ScanCode: ${resolution.scanCode}",
            "Unicode: ${showUnicode(resolution.unicodeChar)}",
            "Characters: ${resolution.characters ?: "-"}",
            "Modifiers: ${resolution.modifiers.sorted().joinToString("+").ifEmpty { "-" }}",
            "Selected language: ${resolution.language}",
            "Resolved action: ${resolution.action}",
            "Matched exercise step: ${resolution.matched}",
        ).joinToString("\n"),
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun LayerStatus(state: TrainUiState, firmware: FirmwareState) {
    val board = state.board
    val goal = state.session.nextChar?.let { if (it == '\u21C4') "⇄" else it.toString() } ?: "—"
    val lines = listOf(
        "Цель: $goal",
        holdRecommendation(state.session, board).ifEmpty { "Рекомендация появится на следующем шаге" },
        "Экранная L1: ${if (board.screenLower) "нажата" else "отпущена"}",
        "Экранная L2: ${if (board.screenUpper) "нажата" else "отпущена"}",
        "Экранный слой: ${virtualLayerName(board)}",
        "Физический слой: ${physicalLayerLabel(firmware)}",
    )
    Text(
        text = lines.joinToString("\n"),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun heldModifierLabel(board: BoardLive): String {
    val names = buildList {
        if (board.hardwareShift || board.screenShift) add(KeyModifier.SHIFT.name)
        if (board.hardwareCtrl || board.screenCtrl) add(KeyModifier.CTRL.name)
        if (board.hardwareAlt || board.screenAlt) add(KeyModifier.ALT.name)
        if (board.hardwareMeta || board.screenMeta) add(KeyModifier.META.name)
    }
    return names.joinToString("+").ifEmpty { "—" }
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
