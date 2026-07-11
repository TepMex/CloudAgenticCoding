package com.tepmex.paircompelo.feature.items

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.repository.PreferenceRepository
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

data class ItemEditUiState(
    val name: String = "",
    val description: String = "",
    val notes: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ItemEditViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val listId = UUID.fromString(checkNotNull(savedStateHandle["listId"]))
    private val itemId: UUID? = savedStateHandle.get<String>("itemId")?.let(UUID::fromString)

    private val _state = MutableStateFlow(ItemEditUiState(loading = itemId != null))
    val state: StateFlow<ItemEditUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>()
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        if (itemId != null) {
            viewModelScope.launch {
                repository.observeItem(itemId).collect { item ->
                    if (item != null) {
                        _state.update {
                            it.copy(
                                name = item.name,
                                description = item.description.orEmpty(),
                                notes = item.notes.orEmpty(),
                                loading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onNotes(v: String) = _state.update { it.copy(notes = v) }

    fun save() {
        viewModelScope.launch {
            runCatching {
                val s = _state.value
                if (itemId == null) {
                    repository.createItem(
                        listId,
                        s.name,
                        s.description.ifBlank { null },
                        s.notes.ifBlank { null },
                    )
                } else {
                    repository.updateItem(
                        itemId,
                        s.name,
                        s.description.ifBlank { null },
                        s.notes.ifBlank { null },
                    )
                }
                _saved.emit(Unit)
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Could not save") }
            }
        }
    }
}
