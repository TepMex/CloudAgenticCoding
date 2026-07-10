package com.tepmex.paircompelo.feature.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.ConfirmDialog
import com.tepmex.paircompelo.ui.components.LoadingState

@Composable
fun ListEditScreen(
    onBack: () -> Unit,
    onSaved: (String?) -> Unit,
    viewModel: ListEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ListEditEvent.Saved -> onSaved(event.listId)
                ListEditEvent.Deleted -> onSaved(null)
            }
        }
    }

    AppScaffold(
        title = if (state.listId == null) "New list" else "Edit list",
        onBack = onBack,
    ) { contentPadding ->
        if (state.loading) {
            LoadingState()
        } else {
            Column(
                Modifier
                    .padding(contentPadding)
                    .padding(16.dp),
            ) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = state.error != null,
                )
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = { Text("Description (optional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    minLines = 2,
                )
                state.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) { Text("Save") }

                if (state.listId != null) {
                    if (state.isArchived) {
                        TextButton(onClick = viewModel::restore) { Text("Restore list") }
                    } else {
                        TextButton(onClick = viewModel::archive) { Text("Archive list") }
                    }
                    TextButton(onClick = { showDelete = true }) {
                        Text(
                            text = "Delete permanently",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    if (showDelete) {
        ConfirmDialog(
            title = "Delete this list?",
            message = "Its items and comparison history will also be removed. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                showDelete = false
                viewModel.delete()
            },
            onDismiss = { showDelete = false },
            destructive = true,
        )
    }
}
