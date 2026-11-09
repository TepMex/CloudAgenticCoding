package com.tepmex.myweek.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tepmex.myweek.R
import com.tepmex.myweek.domain.StepElo

@Composable
fun StepScreen(
    state: StepUi,
    onPane: (StepPane) -> Unit,
    onChoose: (String, String, StepElo.Outcome) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .heightIn(min = 48.dp),
        ) {
            SegmentedButton(
                selected = state.pane == StepPane.Compare,
                onClick = { onPane(StepPane.Compare) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {},
                label = { Text(stringResource(R.string.step_compare)) },
            )
            SegmentedButton(
                selected = state.pane == StepPane.Rating,
                onClick = { onPane(StepPane.Rating) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {},
                label = { Text(stringResource(R.string.step_rating)) },
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Spacer(Modifier.fillMaxSize())
                state.pane == StepPane.Rating && state.cards.isEmpty() ->
                    StepMessage(stringResource(R.string.step_empty))
                state.pane == StepPane.Rating -> RatingList(state.cards)
                state.cards.isEmpty() -> StepMessage(stringResource(R.string.step_empty))
                state.cards.size < 2 -> StepMessage(stringResource(R.string.step_need_two))
                state.left == null || state.right == null -> Spacer(Modifier.fillMaxSize())
                else -> ComparePane(
                    left = state.left,
                    right = state.right,
                    enabled = !state.busy,
                    canUndo = state.canUndo,
                    onChoose = onChoose,
                    onUndo = onUndo,
                )
            }
        }
    }
}

@Composable
private fun StepMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp),
    )
}

@Composable
private fun ComparePane(
    left: StepCard,
    right: StepCard,
    enabled: Boolean,
    canUndo: Boolean,
    onChoose: (String, String, StepElo.Outcome) -> Unit,
    onUndo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.step_prompt),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ChoiceCard(
            card = left,
            enabled = enabled,
            onClick = { onChoose(left.key, right.key, StepElo.Outcome.LEFT) },
        )
        Spacer(Modifier.height(12.dp))
        ChoiceCard(
            card = right,
            enabled = enabled,
            onClick = { onChoose(left.key, right.key, StepElo.Outcome.RIGHT) },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onChoose(left.key, right.key, StepElo.Outcome.DRAW) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.step_draw))
            }
            OutlinedButton(
                onClick = { onChoose(left.key, right.key, StepElo.Outcome.SKIP) },
                enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.step_skip))
            }
        }
        if (canUndo) {
            TextButton(
                onClick = onUndo,
                enabled = enabled,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.step_undo))
            }
        }
    }
}

@Composable
private fun ChoiceCard(card: StepCard, enabled: Boolean, onClick: () -> Unit) {
    val caption = stepCaption(card)
    val description = stringResource(R.string.step_choose, caption)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .semantics { contentDescription = description }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(text = card.emoji, style = MaterialTheme.typography.displaySmall)
            if (card.label.isNotEmpty()) {
                Text(text = card.label, style = MaterialTheme.typography.headlineSmall)
            }
            if (card.openCount > 1) {
                Text(
                    text = stringResource(R.string.step_open_count, card.openCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RatingList(cards: List<StepCard>) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        items(items = cards, key = { it.key }, contentType = { "step" }) { card ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "${card.rank}. ${stepCaption(card)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.step_rating_meta, card.rating, card.comparisonCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (card.openCount > 1) {
                    Text(
                        text = stringResource(R.string.step_open_count, card.openCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun stepCaption(card: StepCard): String =
    if (card.label.isEmpty()) card.emoji else "${card.emoji} ${card.label}"
