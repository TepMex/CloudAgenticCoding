package com.tepmex.paircompelo.feature.listcomparison

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.domain.model.ComparisonOutcome
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.EmptyState

@Composable
fun ListComparisonScreen(
    onExit: () -> Unit,
    viewModel: ListComparisonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppScaffold(title = "Which list do you prefer right now?", onBack = onExit) { padding ->
        when {
            state.unavailable -> EmptyState(
                title = "Need two lists",
                message = "Create at least two active lists to compare them.",
                modifier = Modifier.padding(padding),
                action = { Button(onClick = onExit) { Text("Back") } },
            )
            state.left == null || state.right == null -> EmptyState(
                title = "Loading…",
                message = "",
                modifier = Modifier.padding(padding),
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val landscape = maxWidth > maxHeight
                        if (landscape) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                                ListChoiceCard(state.left!!, state.lastWinnerId == state.left!!.list.id, state.settings.showRatingsDuringComparison, !state.busy,
                                    { viewModel.choose(ComparisonOutcome.LEFT_WINS) }, Modifier.weight(1f).fillMaxSize())
                                ListChoiceCard(state.right!!, state.lastWinnerId == state.right!!.list.id, state.settings.showRatingsDuringComparison, !state.busy,
                                    { viewModel.choose(ComparisonOutcome.RIGHT_WINS) }, Modifier.weight(1f).fillMaxSize())
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                                ListChoiceCard(state.left!!, state.lastWinnerId == state.left!!.list.id, state.settings.showRatingsDuringComparison, !state.busy,
                                    { viewModel.choose(ComparisonOutcome.LEFT_WINS) }, Modifier.weight(1f).fillMaxWidth())
                                ListChoiceCard(state.right!!, state.lastWinnerId == state.right!!.list.id, state.settings.showRatingsDuringComparison, !state.busy,
                                    { viewModel.choose(ComparisonOutcome.RIGHT_WINS) }, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (state.settings.allowDraws) {
                            OutlinedButton(onClick = { viewModel.choose(ComparisonOutcome.DRAW) }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Draw") }
                        }
                        if (state.settings.allowSkipping) {
                            OutlinedButton(onClick = { viewModel.choose(ComparisonOutcome.SKIPPED) }, enabled = !state.busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Skip") }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = viewModel::undo) { Text("Undo last choice") }
                        TextButton(onClick = onExit) { Text("Done") }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}

@Composable
private fun ListChoiceCard(
    info: ListPairInfo,
    selected: Boolean,
    showRating: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.selectable(selected = selected, enabled = enabled, role = Role.Button, onClick = onClick),
        tonalElevation = if (selected) 6.dp else 1.dp,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
            Text(info.list.name, style = MaterialTheme.typography.headlineMedium)
            info.list.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("${info.activeItemCount} items" + (info.topItemName?.let { " · top: $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
            if (showRating) {
                Text("List rating ${"%.0f".format(info.list.rating)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
