package com.tepmex.heilauncher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.tepmex.heilauncher.AppGraph

class GraphViewModelFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val model = when (modelClass) {
            HomeViewModel::class.java -> HomeViewModel(graph)
            SettingsViewModel::class.java -> SettingsViewModel(graph)
            else -> throw IllegalArgumentException("Unknown model ${modelClass.name}")
        }
        return model as T
    }
}
