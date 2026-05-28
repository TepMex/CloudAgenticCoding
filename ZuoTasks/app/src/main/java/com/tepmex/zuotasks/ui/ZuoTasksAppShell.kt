package com.tepmex.zuotasks.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.zuotasks.ui.projects.ProjectsScreen
import com.tepmex.zuotasks.ui.projects.ProjectsViewModel
import com.tepmex.zuotasks.ui.regular.RegularTasksScreen
import com.tepmex.zuotasks.ui.regular.RegularTasksViewModel
import com.tepmex.zuotasks.ui.settings.SettingsScreen
import com.tepmex.zuotasks.ui.settings.SettingsViewModel

private enum class MainTab(val label: String) {
    Projects("Projects"),
    Regular("Regular"),
    Settings("Settings"),
}

@Composable
fun ZuoTasksAppShell(
    factory: ZuoTasksViewModelFactory,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = MainTab.entries

    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    MainTab.Projects -> Icons.Default.Folder
                                    MainTab.Regular -> Icons.Default.EventRepeat
                                    MainTab.Settings -> Icons.Default.Settings
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tabs[selectedTab]) {
            MainTab.Projects -> {
                val vm: ProjectsViewModel = viewModel(factory = factory)
                ProjectsScreen(
                    viewModel = vm,
                    modifier = Modifier.padding(padding),
                )
            }
            MainTab.Regular -> {
                val vm: RegularTasksViewModel = viewModel(factory = factory)
                RegularTasksScreen(
                    viewModel = vm,
                    modifier = Modifier.padding(padding),
                )
            }
            MainTab.Settings -> {
                val vm: SettingsViewModel = viewModel(factory = factory)
                SettingsScreen(
                    viewModel = vm,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}
