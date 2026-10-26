package com.tepmex.myweek.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.myweek.R
import com.tepmex.myweek.domain.WeekPhase

@Composable
fun WeekApp(viewModel: WeekViewModel, modifier: Modifier = Modifier) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val home by viewModel.home.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val emojiChoices by viewModel.emojiChoices.collectAsStateWithLifecycle()
    val snackbar = androidx.compose.runtime.remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.notices.collect { notice ->
            val text = when (notice) {
                Notice.PlanningClosed -> context.getString(R.string.notice_planning_closed)
                Notice.NotThisWeek -> context.getString(R.string.notice_not_this_week)
                Notice.SaveFailed -> context.getString(R.string.notice_save_failed)
                Notice.AlreadyOrdinary -> context.getString(R.string.notice_already_ordinary)
                Notice.AlreadyReal -> context.getString(R.string.notice_already_real)
            }
            snackbar.showSnackbar(text)
        }
    }
    val planning = (home as? HomeUi.Ready)?.phase == WeekPhase.Planning
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Column {
                if (tab == Tab.Week && planning) {
                    Button(
                        onClick = viewModel::openNewBlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.add_block))
                    }
                }
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Week,
                        onClick = { viewModel.selectTab(Tab.Week) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_week)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Archive,
                        onClick = { viewModel.selectTab(Tab.Archive) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_archive)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Settings,
                        onClick = { viewModel.selectTab(Tab.Settings) },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_settings)) },
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (tab) {
            Tab.Week -> HomeScreen(
                state = home,
                onToggle = viewModel::toggle,
                onEditBlock = viewModel::openBlock,
                onAddTask = viewModel::openNewTask,
                onEditTask = viewModel::openTask,
                modifier = contentModifier,
            )
            Tab.Archive -> ArchiveRoute(viewModel, contentModifier)
            Tab.Settings -> SettingsRoute(viewModel, contentModifier)
        }
        editor?.let { current ->
            key(current.identity) {
                when (current) {
                    is Editor.BlockEditor -> BlockEditorDialog(
                        initialTitle = current.title,
                        initialColor = current.colorKey,
                        initialEmoji = current.emoji,
                        emojiChoices = emojiChoices,
                        canDelete = current.blockId != null,
                        onDismiss = viewModel::dismissEditor,
                        onSave = { title, color, emoji ->
                            viewModel.saveBlock(current.blockId, title, color, emoji)
                        },
                        onDelete = {
                            val id = current.blockId
                            if (id != null) viewModel.deleteBlock(id)
                        },
                    )
                    is Editor.TaskEditor -> TaskEditorDialog(
                        initialEmoji = current.emoji,
                        initialTitle = current.title,
                        emojiChoices = emojiChoices,
                        canDelete = current.taskId != null,
                        onDismiss = viewModel::dismissEditor,
                        onSave = { emoji, title ->
                            viewModel.saveTask(current.taskId, current.blockId, emoji, title)
                        },
                        onDelete = {
                            val id = current.taskId
                            if (id != null) viewModel.deleteTask(id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveRoute(viewModel: WeekViewModel, modifier: Modifier) {
    val archive by viewModel.archive.collectAsStateWithLifecycle()
    ArchiveScreen(state = archive, modifier = modifier)
}

@Composable
private fun SettingsRoute(viewModel: WeekViewModel, modifier: Modifier) {
    val settings by viewModel.settingsUi.collectAsStateWithLifecycle()
    SettingsScreen(
        state = settings,
        onDebug = viewModel::setDebug,
        onNewMonday = viewModel::debugNewMonday,
        onOrdinaryDay = viewModel::debugOrdinaryDay,
        onNextDay = viewModel::debugNextDay,
        onRealTime = viewModel::debugRealTime,
        onClear = viewModel::clearAll,
        modifier = modifier,
    )
}
