package com.tepmex.zuotasks.ui.regular

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.zuotasks.data.RegularTaskEntity
import com.tepmex.zuotasks.data.ZuoTasksRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RegularTaskRow(
    val id: Long,
    val name: String,
    val lastPerformedLabel: String,
)

data class RegularTasksUiState(
    val tasks: List<RegularTaskRow> = emptyList(),
    val showAddDialog: Boolean = false,
    val pendingDeleteId: Long? = null,
)

class RegularTasksViewModel(
    private val repository: ZuoTasksRepository,
) : ViewModel() {

    private val showAddDialog = MutableStateFlow(false)
    private val pendingDeleteId = MutableStateFlow<Long?>(null)

    private val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    val uiState: StateFlow<RegularTasksUiState> = combine(
        repository.observeRegularTasks(),
        showAddDialog,
        pendingDeleteId,
    ) { entities, addDialog, pendingDelete ->
        RegularTasksUiState(
            tasks = entities.map { it.toRow(formatter) },
            showAddDialog = addDialog,
            pendingDeleteId = pendingDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RegularTasksUiState(),
    )

    fun openAddDialog() {
        showAddDialog.value = true
    }

    fun dismissDialogs() {
        showAddDialog.value = false
    }

    fun addTask(name: String) {
        viewModelScope.launch {
            repository.addRegularTask(name)
            showAddDialog.value = false
        }
    }

    fun markPerformed(id: Long) {
        viewModelScope.launch {
            repository.markRegularTaskPerformed(id)
        }
    }

    fun requestDelete(id: Long) {
        pendingDeleteId.value = id
    }

    fun confirmDelete() {
        val id = pendingDeleteId.value ?: return
        viewModelScope.launch {
            repository.deleteRegularTask(id)
            pendingDeleteId.value = null
        }
    }

    fun cancelDelete() {
        pendingDeleteId.value = null
    }

    private fun RegularTaskEntity.toRow(formatter: DateTimeFormatter): RegularTaskRow {
        val label = lastPerformedAt?.let { formatter.format(Instant.ofEpochMilli(it)) }
            ?: "Never yet"
        return RegularTaskRow(id = id, name = name, lastPerformedLabel = label)
    }
}
