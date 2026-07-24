package com.tepmex.zuotasks.ui.projects

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.zuotasks.domain.TreeNodeItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.navigationStack.size > 1) {
        viewModel.navigateUp()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.currentTitle) },
                navigationIcon = {
                    if (state.navigationStack.size > 1) {
                        IconButton(onClick = { viewModel.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.toggleShowHidden() }) {
                        Icon(
                            imageVector = if (state.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                        Text(if (state.showHidden) "Hide hidden" else "Show hidden")
                    }
                },
            )
        },
        floatingActionButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(onClick = { viewModel.openAddProjectDialog() }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "Add project")
                }
                FloatingActionButton(onClick = { viewModel.openAddTaskDialog() }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        },
    ) { padding ->
        if (state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No items yet. Add a project or task.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(
                    items = state.items,
                    key = { it.id },
                    contentType = { it.type.name },
                ) { item ->
                    ProjectTreeRow(
                        item = item,
                        onTaskClick = { viewModel.toggleTask(item.id) },
                        onProjectLongClick = { viewModel.enterProject(item.id) },
                        onHideSwipe = { viewModel.requestHideProject(item.id) },
                    )
                }
            }
        }
    }

    if (state.showAddProjectDialog) {
        NameInputDialog(
            title = "New project",
            onDismiss = { viewModel.dismissDialogs() },
            onConfirm = { viewModel.addProject(it) },
        )
    }
    if (state.showAddTaskDialog) {
        NameInputDialog(
            title = "New task",
            onDismiss = { viewModel.dismissDialogs() },
            onConfirm = { viewModel.addTask(it) },
        )
    }
    state.pendingHideProjectId?.let {
        AlertDialog(
            onDismissRequest = { viewModel.cancelHideProject() },
            title = { Text("Hide project?") },
            text = { Text("The project will be hidden from the list. You can show hidden projects from the top bar.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmHideProject() }) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelHideProject() }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ProjectTreeRow(
    item: TreeNodeItem,
    onTaskClick: () -> Unit,
    onProjectLongClick: () -> Unit,
    onHideSwipe: () -> Unit,
) {
    if (item.isProject) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onHideSwipe()
                }
                false
            },
        )
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text("Hide", color = MaterialTheme.colorScheme.error)
                }
            },
            content = {
                ProjectListItem(
                    item = item,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onProjectLongClick,
                    ),
                )
            },
        )
    } else {
        ProjectListItem(
            item = item,
            modifier = Modifier.combinedClickable(onClick = onTaskClick),
        )
    }
}

@Composable
private fun ProjectListItem(
    item: TreeNodeItem,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.fillMaxWidth(),
        headlineContent = {
            Text(
                text = item.name,
                softWrap = true,
                textDecoration = if (item.isTask && item.isCompleted) {
                    TextDecoration.LineThrough
                } else {
                    null
                },
            )
        },
        supportingContent = {
            if (item.isProject) {
                Column {
                    LinearProgressIndicator(
                        progress = { (item.completionPercent ?: 0) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    Text(
                        "${item.completionPercent ?: 0}% complete",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        leadingContent = {
            if (item.isTask) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = null,
                )
            } else {
                Icon(Icons.Default.Folder, contentDescription = null)
            }
        },
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
