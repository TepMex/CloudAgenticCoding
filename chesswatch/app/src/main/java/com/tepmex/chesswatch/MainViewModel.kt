package com.tepmex.chesswatch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

data class UiState(
    val items: List<TrackedActivity> = emptyList(),
    val selectedId: String? = null,
    val segmentStartMs: Long = System.currentTimeMillis(),
    val nowMs: Long = System.currentTimeMillis(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ActivityStore(application.applicationContext)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var tickJob: Job? = null

    init {
        val loaded = store.load()
        _state.value =
            UiState(
                items = loaded.items,
                selectedId = loaded.selectedId,
                segmentStartMs = loaded.segmentStartMs,
                nowMs = System.currentTimeMillis(),
            )
        startTick()
    }

    private fun startTick() {
        tickJob?.cancel()
        tickJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    val now = System.currentTimeMillis()
                    _state.update { it.copy(nowMs = now) }
                }
            }
    }

    fun select(id: String) {
        val current = _state.value
        if (current.selectedId == id) return
        val now = System.currentTimeMillis()
        val updatedItems =
            current.items.map { item ->
                if (item.id == current.selectedId) {
                    val extra = now - current.segmentStartMs
                    item.copy(accumulatedMs = item.accumulatedMs + extra)
                } else {
                    item
                }
            }
        _state.value =
            UiState(
                items = updatedItems,
                selectedId = id,
                segmentStartMs = now,
                nowMs = now,
            )
        persist()
    }

    fun addActivity(name: String) {
        val trimmed = name.trim().ifEmpty { return }
        val id = UUID.randomUUID().toString()
        val newItem = TrackedActivity(id = id, name = trimmed, accumulatedMs = 0L)
        _state.update { it.copy(items = it.items + newItem) }
        select(id)
    }

    fun deleteActivity(id: String) {
        if (id == ActivityStore.IDLE_ID) return
        val current = _state.value
        val now = System.currentTimeMillis()
        var items =
            current.items.map { item ->
                if (item.id == current.selectedId) {
                    val extra = now - current.segmentStartMs
                    item.copy(accumulatedMs = item.accumulatedMs + extra)
                } else {
                    item
                }
            }
        items = items.filter { it.id != id }
        if (items.isEmpty()) {
            items = listOf(TrackedActivity(ActivityStore.IDLE_ID, "idle", 0L))
        }
        val newSelected =
            when {
                current.selectedId == id -> items.first().id
                items.any { it.id == current.selectedId } -> current.selectedId
                else -> items.first().id
            }
        _state.value =
            UiState(
                items = items,
                selectedId = newSelected,
                segmentStartMs = now,
                nowMs = now,
            )
        persist()
    }

    fun persist() {
        val s = _state.value
        val itemsForStore =
            s.items.map { item ->
                if (item.id == s.selectedId) {
                    val running = s.nowMs - s.segmentStartMs
                    item.copy(accumulatedMs = item.accumulatedMs + running)
                } else {
                    item
                }
            }
        val now = System.currentTimeMillis()
        store.save(
            ActivityStore.PersistedState(
                items = itemsForStore,
                selectedId = s.selectedId,
                segmentStartMs = now,
            ),
        )
        _state.value =
            s.copy(
                items = itemsForStore,
                segmentStartMs = now,
                nowMs = now,
            )
    }

    fun displayMsFor(item: TrackedActivity): Long {
        val s = _state.value
        return if (item.id == s.selectedId) {
            item.accumulatedMs + (s.nowMs - s.segmentStartMs)
        } else {
            item.accumulatedMs
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TICK_MS = 500L
    }
}
