@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tepmex.myweek.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.myweek.MyWeekApp
import com.tepmex.myweek.data.WeekRepository
import com.tepmex.myweek.data.WeekSettings
import com.tepmex.myweek.domain.DebugCalendar
import com.tepmex.myweek.domain.Palette
import com.tepmex.myweek.domain.Sprint
import com.tepmex.myweek.domain.TaskText
import com.tepmex.myweek.domain.WeekPhase
import com.tepmex.myweek.domain.WeekRules
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tab { Week, Archive, Settings }

enum class Notice { PlanningClosed, NotThisWeek, SaveFailed, AlreadyOrdinary, AlreadyReal }

sealed interface Editor {
    val identity: String

    data class BlockEditor(
        val blockId: Long?,
        val title: String,
        val colorKey: String,
    ) : Editor {
        override val identity: String = "block-${blockId ?: "new"}"
    }

    data class TaskEditor(
        val taskId: Long?,
        val blockId: Long,
        val emoji: String,
        val title: String,
    ) : Editor {
        override val identity: String = "task-${taskId ?: "new-$blockId"}"
    }
}

sealed interface HomeUi {
    data object Loading : HomeUi

    data class Ready(
        val today: LocalDate,
        val phase: WeekPhase,
        val sprint: Sprint?,
        val debug: Boolean,
        val usingOverride: Boolean,
    ) : HomeUi
}

sealed interface ArchiveUi {
    data object Loading : ArchiveUi

    data class Ready(val sprints: List<Sprint>) : ArchiveUi
}

data class SettingsUi(
    val debug: Boolean,
    val today: LocalDate,
    val usingOverride: Boolean,
    val planning: Boolean,
    val version: String,
)

class WeekViewModel(
    private val repository: WeekRepository,
    private val settings: WeekSettings,
    versionName: String,
) : ViewModel() {
    private val clock = Channel<LocalDate>(Channel.CONFLATED)
    private val todayState = MutableStateFlow<LocalDate?>(null)

    val tab = MutableStateFlow(Tab.Week)
    val editor = MutableStateFlow<Editor?>(null)
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)

    val home: StateFlow<HomeUi> = todayState
        .flatMapLatest { today ->
            if (today == null) {
                flowOf(HomeUi.Loading)
            } else {
                combine(
                    repository.observeOpenWeek(today),
                    settings.debug,
                    settings.overrideDate,
                ) { sprint, debug, override ->
                    HomeUi.Ready(
                        today = today,
                        phase = WeekRules.phase(today, sprint),
                        sprint = sprint,
                        debug = debug,
                        usingOverride = debug && override != null,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi.Loading)

    val archive: StateFlow<ArchiveUi> = repository.observeArchived()
        .flatMapLatest { sprints -> flowOf(ArchiveUi.Ready(sprints)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUi.Loading)

    val settingsUi: StateFlow<SettingsUi> = combine(settings.debug, settings.overrideDate, home) { debug, override, homeUi ->
        val ready = homeUi as? HomeUi.Ready
        SettingsUi(
            debug = debug,
            today = ready?.today ?: settings.today(),
            usingOverride = debug && override != null,
            planning = ready?.phase == WeekPhase.Planning,
            version = versionName,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUi(
            debug = settings.debug.value,
            today = settings.today(),
            usingOverride = settings.debug.value && settings.overrideDate.value != null,
            planning = false,
            version = versionName,
        ),
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (today in clock) {
                try {
                    repository.reconcile(today)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "reconcile failed", e)
                    notices.tryEmit(Notice.SaveFailed)
                }
                todayState.value = today
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(millisUntilTomorrow())
                if (!settings.debug.value || settings.overrideDate.value == null) {
                    publishClock()
                }
            }
        }
        publishClock()
    }

    fun onVisible() = publishClock()

    fun selectTab(next: Tab) {
        tab.value = next
        editor.value = null
    }

    fun dismissEditor() {
        editor.value = null
    }

    fun setDebug(enabled: Boolean) {
        settings.setDebug(enabled)
        publishClock()
    }

    fun debugNewMonday() {
        if (!settings.debug.value) return
        settings.setOverride(DebugCalendar.nextMonday(settings.today()))
        publishClock()
    }

    fun debugOrdinaryDay() {
        if (!settings.debug.value) return
        val today = settings.today()
        val next = DebugCalendar.ordinaryDay(today)
        if (next == today) {
            notices.tryEmit(Notice.AlreadyOrdinary)
            return
        }
        settings.setOverride(next)
        publishClock()
    }

    fun debugNextDay() {
        if (!settings.debug.value) return
        settings.setOverride(DebugCalendar.nextDay(settings.today()))
        publishClock()
    }

    fun debugRealTime() {
        if (!settings.debug.value) return
        if (settings.overrideDate.value == null) {
            notices.tryEmit(Notice.AlreadyReal)
            return
        }
        settings.setOverride(null)
        publishClock()
    }

    fun clearAll() {
        if (!settings.debug.value) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.clearAll()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "clear failed", e)
                notices.tryEmit(Notice.SaveFailed)
            }
            publishClock()
        }
    }

    fun openNewBlock() {
        val ready = home.value as? HomeUi.Ready ?: return
        if (ready.phase != WeekPhase.Planning) return
        val color = Palette.next(ready.sprint?.blocks?.map { it.colorKey }.orEmpty())
        editor.value = Editor.BlockEditor(blockId = null, title = "", colorKey = color)
    }

    fun openBlock(blockId: Long) {
        val ready = planningReady() ?: return
        val block = ready.sprint?.blocks?.firstOrNull { it.id == blockId } ?: return
        editor.value = Editor.BlockEditor(block.id, block.title, block.colorKey)
    }

    fun openNewTask(blockId: Long) {
        if (planningReady() == null) return
        editor.value = Editor.TaskEditor(taskId = null, blockId = blockId, emoji = TaskText.DEFAULT_EMOJI, title = "")
    }

    fun openTask(taskId: Long) {
        val ready = planningReady() ?: return
        val task = ready.sprint?.blocks?.asSequence()?.flatMap { it.tasks.asSequence() }?.firstOrNull { it.id == taskId }
            ?: return
        val blockId = ready.sprint.blocks.first { block -> block.tasks.any { it.id == taskId } }.id
        editor.value = Editor.TaskEditor(task.id, blockId, task.emoji, task.title)
    }

    fun saveBlock(blockId: Long?, title: String, colorKey: String) {
        val ready = planningReady() ?: return
        val sprint = ready.sprint ?: return
        val normalized = TaskText.blockTitle(title) ?: return
        val today = ready.today
        editor.value = null
        write {
            val ok = if (blockId == null) {
                repository.addBlock(sprint.id, normalized, colorKey, today)
            } else {
                repository.updateBlock(blockId, normalized, colorKey, today)
            }
            if (!ok) notices.tryEmit(Notice.PlanningClosed)
        }
    }

    fun deleteBlock(blockId: Long) {
        val ready = planningReady() ?: return
        val today = ready.today
        editor.value = null
        write {
            if (!repository.deleteBlock(blockId, today)) notices.tryEmit(Notice.PlanningClosed)
        }
    }

    fun saveTask(taskId: Long?, blockId: Long, emoji: String, title: String) {
        val ready = planningReady() ?: return
        val draft = TaskText.task(emoji, title) ?: return
        val today = ready.today
        editor.value = null
        write {
            val ok = if (taskId == null) {
                repository.addTask(blockId, draft.emoji, draft.title, today)
            } else {
                repository.updateTask(taskId, draft.emoji, draft.title, today)
            }
            if (!ok) notices.tryEmit(Notice.PlanningClosed)
        }
    }

    fun deleteTask(taskId: Long) {
        val ready = planningReady() ?: return
        val today = ready.today
        editor.value = null
        write {
            if (!repository.deleteTask(taskId, today)) notices.tryEmit(Notice.PlanningClosed)
        }
    }

    fun toggle(taskId: Long, done: Boolean) {
        val ready = home.value as? HomeUi.Ready ?: return
        if (ready.phase == WeekPhase.NoSprint) return
        val today = ready.today
        write {
            if (!repository.setDone(taskId, done, today)) notices.tryEmit(Notice.NotThisWeek)
        }
    }

    private fun planningReady(): HomeUi.Ready? {
        val ready = home.value as? HomeUi.Ready
        if (ready == null || ready.phase != WeekPhase.Planning) {
            if (ready != null) notices.tryEmit(Notice.PlanningClosed)
            editor.value = null
            return null
        }
        return ready
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "write failed", e)
                notices.tryEmit(Notice.SaveFailed)
            }
        }
    }

    private fun publishClock() {
        clock.trySend(settings.today())
    }

    private fun millisUntilTomorrow(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val next = now.toLocalDate().plusDays(1).atStartOfDay(zone)
        return Duration.between(now, next).toMillis().coerceIn(1_000L, 86_400_000L)
    }

    companion object {
        private const val TAG = "MyWeek"

        fun factory(app: MyWeekApp): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WeekViewModel(app.repository, app.settings, app.versionName) as T
        }
    }
}
