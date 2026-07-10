package com.tepmex.paircompelo.feature.lists

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.domain.model.PreferenceList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ListEditUiState(
    val listId: UUID? = null,
    val name: String = "",
    val description: String = "",
    val isArchived: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val existing: PreferenceList? = null,
)

sealed interface ListEditEvent {
    data class Saved(val listId: String) : ListEditEvent
    data object Deleted : ListEditEvent
}

@HiltViewModel
class ListEditViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val listId: UUID? = savedStateHandle.get<String>("listId")?.let(UUID::fromString)

    private val _state = MutableStateFlow(ListEditUiState(listId = listId, loading = listId != null))
    val state: StateFlow<ListEditUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ListEditEvent>()
    val events: SharedFlow<ListEditEvent> = _events.asSharedFlow()

    init {
        if (listId != null) {
            viewModelScope.launch {
                repository.observeList(listId).collect { list ->
                    if (list != null) {
                        _state.update {
                            it.copy(
                                name = list.name,
                                description = list.description.orEmpty(),
                                isArchived = list.isArchived,
                                existing = list,
                                loading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }

    fun save() {
        viewModelScope.launch {
            runCatching {
                val s = _state.value
                if (s.listId == null) {
                    val created = repository.createList(s.name, s.description.ifBlank { null })
                    _events.emit(ListEditEvent.Saved(created.id.toString()))
                } else {
                    repository.updateList(s.listId, s.name, s.description.ifBlank { null })
                    _events.emit(ListEditEvent.Saved(s.listId.toString()))
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Could not save") }
            }
        }
    }

    fun archive() {
        val id = listId ?: return
        viewModelScope.launch { repository.archiveList(id) }
    }

    fun restore() {
        val id = listId ?: return
        viewModelScope.launch { repository.restoreList(id) }
    }

    fun delete() {
        val id = listId ?: return
        viewModelScope.launch {
            repository.deleteList(id)
            _events.emit(ListEditEvent.Deleted)
        }
    }
}
