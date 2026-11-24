package com.tepmex.assumptions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.assumptions.domain.Expectation
import com.tepmex.assumptions.domain.ExpectationField
import com.tepmex.assumptions.domain.PleasureScale
import com.tepmex.assumptions.domain.clockLabel
import com.tepmex.assumptions.domain.dayLabel
import com.tepmex.assumptions.domain.formatScore
import com.tepmex.assumptions.ui.theme.Ink
import com.tepmex.assumptions.ui.theme.InkSoft
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ExpectationsScreen(
    expectations: List<Expectation>,
    draft: ExpectationDraft?,
    onBeginCreate: () -> Unit,
    onBeginResolve: (Expectation) -> Unit,
    onEvent: (String) -> Unit,
    onOutcome: (String) -> Unit,
    onScore: (Int) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (draft != null) {
        ExpectationEditor(
            draft = draft,
            onEvent = onEvent,
            onOutcome = onOutcome,
            onScore = onScore,
            onNote = onNote,
            onSave = onSave,
            onCancel = onCancel,
            modifier = modifier,
        )
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    var pendingDelete by remember { mutableStateOf<Expectation?>(null) }
    val pending = expectations.filter { it.afterScore == null }
    val resolved = expectations
        .filter { it.afterScore != null }
        .sortedByDescending { it.resolvedAtEpochMs ?: it.createdAtEpochMs }
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ожидания",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onBeginCreate) {
                Text("Новое")
            }
        }
        if (expectations.isEmpty()) {
            Text(
                text = "Нет ожиданий. Запишите событие, которое ещё впереди, и насколько оно приятно или неприятно.",
                style = MaterialTheme.typography.bodyLarge,
                color = InkSoft,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pending.isNotEmpty()) {
                    SectionLabel("Ещё впереди")
                    pending.forEach { item ->
                        ExpectationCard(
                            item = item,
                            zone = zone,
                            today = today,
                            onResolve = { onBeginResolve(item) },
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
                if (resolved.isNotEmpty()) {
                    SectionLabel("Уже произошли")
                    resolved.forEach { item ->
                        ExpectationCard(
                            item = item,
                            zone = zone,
                            today = today,
                            onResolve = null,
                            onDelete = { pendingDelete = item },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    pendingDelete?.let { item ->
        ConfirmDelete(
            title = "Удалить ожидание?",
            body = item.eventText,
            onConfirm = {
                onDelete(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ExpectationCard(
    item: Expectation,
    zone: ZoneId,
    today: LocalDate,
    onResolve: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = item.eventText, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = dayLabel(item.createdAtEpochMs, zone, today) +
                            " · " + clockLabel(item.createdAtEpochMs, zone),
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSoft,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                }
            }
            Text(
                text = item.expectedOutcome,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSoft,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreChip("До", item.beforeScore)
                item.afterScore?.let { ScoreChip("После", it) }
            }
            if (item.afterNote.isNotBlank()) {
                Text(text = item.afterNote, style = MaterialTheme.typography.bodyMedium)
            }
            if (onResolve != null) {
                OutlinedButton(onClick = onResolve) {
                    Text("Случилось")
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(label: String, score: Int) {
    val color = scoreColor(score)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = InkSoft)
        Text(
            text = formatScore(score),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ExpectationEditor(
    draft: ExpectationDraft,
    onEvent: (String) -> Unit,
    onOutcome: (String) -> Unit,
    onScore: (Int) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val creating = draft.mode is DraftMode.Create
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                text = if (creating) "Новое ожидание" else "Событие произошло",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        if (creating) {
            OutlinedTextField(
                value = draft.event,
                onValueChange = onEvent,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Событие") },
                placeholder = { Text("Что должно произойти") },
                isError = ExpectationField.Event in draft.errors,
                supportingText = errorLine(ExpectationField.Event, draft.errors),
                minLines = 2,
            )
            OutlinedTextField(
                value = draft.outcome,
                onValueChange = onOutcome,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Предполагаемый исход") },
                placeholder = { Text("Как, по-вашему, это закончится") },
                isError = ExpectationField.Outcome in draft.errors,
                supportingText = errorLine(ExpectationField.Outcome, draft.errors),
                minLines = 2,
            )
        } else {
            Text(text = draft.event, style = MaterialTheme.typography.titleLarge)
            Text(text = draft.outcome, style = MaterialTheme.typography.bodyLarge, color = InkSoft)
        }
        Text(
            text = if (creating) "Отношение" else "Отношение теперь",
            style = MaterialTheme.typography.titleMedium,
        )
        PleasurePicker(
            selected = draft.score,
            onSelect = onScore,
        )
        if (ExpectationField.Score in draft.errors) {
            Text(
                text = "Выберите значение от −5 до 5",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!creating) {
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNote,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Комментарий") },
                placeholder = { Text("Необязательно") },
                minLines = 2,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel) { Text("Отмена") }
            Button(onClick = onSave) { Text(if (creating) "Создать" else "Сохранить") }
        }
    }
}

@Composable
fun PleasurePicker(
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("неприятно", style = MaterialTheme.typography.labelMedium, color = InkSoft)
            Text("приятно", style = MaterialTheme.typography.labelMedium, color = InkSoft)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            PleasureScale.values.forEach { score ->
                val picked = score == selected
                val color = scoreColor(score)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (picked) color else color.copy(alpha = 0.16f))
                        .clickable { onSelect(score) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = formatScore(score),
                        color = if (picked) Color.White else Ink,
                        fontSize = 11.sp,
                        fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun errorLine(
    field: ExpectationField,
    errors: Set<ExpectationField>,
): @Composable (() -> Unit)? {
    val message = fieldError(field, errors) ?: return null
    return { Text(message) }
}

private fun fieldError(field: ExpectationField, errors: Set<ExpectationField>): String? {
    if (field !in errors) return null
    return when (field) {
        ExpectationField.Event -> "Напишите событие"
        ExpectationField.Outcome -> "Напишите предполагаемый исход"
        ExpectationField.Score -> "Выберите значение от −5 до 5"
    }
}

internal fun scoreColor(score: Int): Color = when {
    score > 0 -> Color(0xFF1B8A3A)
    score < 0 -> Color(0xFFC62828)
    else -> Color(0xFF6F685E)
}
