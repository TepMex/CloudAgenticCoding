package com.tepmex.inbox.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.tepmex.inbox.InboxApp
import com.tepmex.inbox.data.CaptureRepository
import com.tepmex.inbox.domain.Capture
import com.tepmex.inbox.domain.CaptureActions
import com.tepmex.inbox.domain.CaptureCounts
import com.tepmex.inbox.domain.ScheduleAttempt
import com.tepmex.inbox.notify.InboxSettings
import com.tepmex.inbox.notify.ReminderAlarms
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Notice {
    SaveFailed,
    TimePassed,
    Copied,
}

sealed interface LaunchEffect {
    data object OpenArchive : LaunchEffect
    data class SharedText(val text: String) : LaunchEffect
}

class InboxViewModel(
    private val savedState: SavedStateHandle,
    private val repository: CaptureRepository,
    private val alarms: ReminderAlarms,
    private val settings: InboxSettings,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val draft: StateFlow<String> = savedState.getStateFlow(KEY_DRAFT, "")
    val inbox: StateFlow<List<Capture>> = repository.observeInbox()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val inboxReady: StateFlow<Boolean> = repository.observeInbox()
        .map { true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val reminders: StateFlow<List<Capture>> = repository.observeReminders()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archive: StateFlow<List<Capture>> = repository.observeArchive()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val counts: StateFlow<CaptureCounts> = repository.observeCounts()
        .stateIn(viewModelScope, SharingStarted.Eagerly, CaptureCounts(0, 0, 0))

    val haptic = settings.haptic
    val openKeyboard = settings.openKeyboard
    val showOnLockScreen = settings.showOnLockScreen

    private val _notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val notices = _notices.asSharedFlow()

    fun updateDraft(value: String) {
        savedState[KEY_DRAFT] = value
    }

    fun send(): Boolean {
        val created = CaptureActions.newCapture(draft.value, clock()) ?: return false
        savedState[KEY_DRAFT] = ""
        viewModelScope.launch {
            try {
                repository.insert(created)
            } catch (_: Exception) {
                if (draft.value.isBlank()) savedState[KEY_DRAFT] = created.text
                _notices.emit(Notice.SaveFailed)
            }
        }
        return true
    }

    fun captureIncoming(text: String) {
        val created = CaptureActions.newCapture(text, clock()) ?: return
        viewModelScope.launch {
            try {
                repository.insert(created)
            } catch (_: Exception) {
                _notices.emit(Notice.SaveFailed)
            }
        }
    }

    fun markProcessed(id: Long) {
        viewModelScope.launch {
            repository.markProcessed(id, clock())
        }
    }

    fun schedule(id: Long, atMillis: Long): Boolean {
        if (atMillis <= clock()) {
            _notices.tryEmit(Notice.TimePassed)
            return false
        }
        viewModelScope.launch {
            when (val result = repository.schedule(id, atMillis, clock())) {
                is ScheduleAttempt.Scheduled -> alarms.schedule(result.capture.id, atMillis)
                ScheduleAttempt.Past -> _notices.emit(Notice.TimePassed)
                ScheduleAttempt.Missing -> Unit
            }
        }
        return true
    }

    fun cancelReminder(id: Long) {
        viewModelScope.launch {
            repository.cancelReminder(id)
            alarms.cancel(id)
        }
    }

    fun clearArchive() {
        viewModelScope.launch {
            repository.clearArchive()
        }
    }

    fun copied() {
        _notices.tryEmit(Notice.Copied)
    }

    fun setHaptic(value: Boolean) = settings.setHaptic(value)

    fun setOpenKeyboard(value: Boolean) = settings.setOpenKeyboard(value)

    fun setShowOnLockScreen(value: Boolean) = settings.setShowOnLockScreen(value)

    companion object {
        private const val KEY_DRAFT = "draft"
    }
}

class InboxViewModelFactory(private val app: InboxApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return InboxViewModel(
            savedState = extras.createSavedStateHandle(),
            repository = app.repository,
            alarms = app.alarms,
            settings = app.settings,
        ) as T
    }
}
