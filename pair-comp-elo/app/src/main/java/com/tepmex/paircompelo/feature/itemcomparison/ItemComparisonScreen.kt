package com.tepmex.paircompelo.feature.itemcomparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.domain.model.PreferenceItem
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.EmptyState

@Composable
fun ItemComparisonScreen(
    onExit: () -> Unit,
    viewModel: ItemComparisonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppScaffold(title = "Which do you prefer right now?", onBack = onExit) { padding ->
        when {
            state.unavailable -> EmptyState(
                title = "Need two items",
                message = "Add at least two active items in this list to compare.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onExit) { Text("Back") } },
            )
            state.left == null || state.right == null -> EmptyState(
                title = "Loading pair…",
                message = "",
                modifier = Modifier.padding(padding),
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        val landscape = maxWidth > maxHeight
                        if (landscape) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                ChoiceCard(
                                    item = state.left!!,
                                    selected = state.lastWinnerId == state.left!!.id,
                                    showRating = state.settings.showRatingsDuringComparison,
                                    enabled = !state.busy,
                                    onClick = { viewModel.choose(ComparisonOutcome.LEFT_WINS) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize(),
                                )
                                ChoiceCard(
                                    item = state.right!!,
                                    selected = state.lastWinnerId == state.right!!.id,
                                    showRating = state.settings.showRatingsDuringComparison,
                                    enabled = !state.busy,
                                    onClick = { viewModel.choose(ComparisonOutcome.RIGHT_WINS) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize(),
                                )
                            }
                        } else {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                ChoiceCard(
                                    item = state.left!!,
                                    selected = state.lastWinnerId == state.left!!.id,
                                    showRating = state.settings.showRatingsDuringComparison,
                                    enabled = !state.busy,
                                    onClick = { viewModel.choose(ComparisonOutcome.LEFT_WINS) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                )
                                ChoiceCard(
                                    item = state.right!!,
                                    selected = state.lastWinnerId == state.right!!.id,
                                    showRating = state.settings.showRatingsDuringComparison,
                                    enabled = !state.busy,
                                    onClick = { viewModel.choose(ComparisonOutcome.RIGHT_WINS) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.settings.allowDraws) {
                            OutlinedButton(
                                onClick = { viewModel.choose(ComparisonOutcome.DRAW) },
                                enabled = !state.busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) { Text("Draw") }
                        }
                        if (state.settings.allowSkipping) {
                            OutlinedButton(
                                onClick = { viewModel.choose(ComparisonOutcome.SKIPPED) },
                                enabled = !state.busy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) { Text("Skip") }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = viewModel::undo) { Text("Undo last choice") }
                        TextButton(onClick = onExit) { Text("Done") }
                    }
                    state.message?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceCard(
    item: PreferenceItem,
    selected: Boolean,
    showRating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .semantics { contentDescription = "Choose ${item.name}" }
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        tonalElevation = if (selected) 6.dp else 1.dp,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(item.name, style = MaterialTheme.typography.headlineMedium)
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (showRating) {
                    "Rating ${"%.0f".format(item.rating)} · ${item.comparisonCount} comparisons"
                } else {
                    "${item.comparisonCount} comparisons"
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
