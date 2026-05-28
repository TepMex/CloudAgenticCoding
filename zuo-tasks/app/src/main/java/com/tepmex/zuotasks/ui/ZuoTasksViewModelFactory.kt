package com.tepmex.zuotasks.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tepmex.zuotasks.data.ZuoTasksRepository
import com.tepmex.zuotasks.ui.projects.ProjectsViewModel
import com.tepmex.zuotasks.ui.regular.RegularTasksViewModel
import com.tepmex.zuotasks.ui.settings.SettingsViewModel

class ZuoTasksViewModelFactory(
    private val repository: ZuoTasksRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ProjectsViewModel::class.java) ->
            ProjectsViewModel(repository) as T
        modelClass.isAssignableFrom(RegularTasksViewModel::class.java) ->
            RegularTasksViewModel(repository) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(repository) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}
