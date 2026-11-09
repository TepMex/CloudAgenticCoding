@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.tepmex.myweek.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.myweek.MyWeekApp
import com.tepmex.myweek.data.EmojiMemory
import com.tepmex.myweek.data.StepRepository
import com.tepmex.myweek.data.WeekRepository
import com.tepmex.myweek.data.WeekSettings
import com.tepmex.myweek.domain.DebugCalendar
import com.tepmex.myweek.domain.EmojiChoices
import com.tepmex.myweek.domain.Palette
import com.tepmex.myweek.domain.Sprint
import com.tepmex.myweek.domain.StepElo
import com.tepmex.myweek.domain.StepGroups
import com.tepmex.myweek.domain.StepOption
import com.tepmex.myweek.domain.StepPairs
import com.tepmex.myweek.domain.StepStanding
import com.tepmex.myweek.domain.Task
import com.tepmex.myweek.domain.TaskText
import com.tepmex.myweek.domain.WeekPhase
import com.tepmex.myweek.domain.WeekRules
import kotlin.math.roundToInt
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class Tab { Week, Step, Archive, Settings }

enum class Notice {
    PlanningClosed,
    NotThisWeek,
    SaveFailed,
    AlreadyOrdinary,
    AlreadyReal,
    PlanCopied,
    PlanCopyFailed,
}

enum class StepPane { Compare, Rating }

data class StepCard(
    val key: String,
    val emoji: String,
    val label: String,
    val openCount: Int,
    val rating: Int,
    val comparisonCount: Int,
    val rank: Int,
)

data class StepUi(
    val pane: StepPane,
    val loading: Boolean,
    val cards: List<StepCard>,
    val left: StepCard?,
    val right: StepCard?,
    val canUndo: Boolean,
    val busy: Boolean,
)

sealed interface Editor {
    val identity: String

    data class BlockEditor(
        val blockId: Long?,
        val title: String,
        val colorKey: String,
        val emoji: String,
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
    private val steps: StepRepository,
    private val settings: WeekSettings,
    private val emojiMemory: EmojiMemory,
    versionName: String,
) : ViewModel() {
    private val clock = Channel<LocalDate>(Channel.CONFLATED)
    private val todayState = MutableStateFlow<LocalDate?>(null)

    val tab = MutableStateFlow(Tab.Week)
    val editor = MutableStateFlow<Editor?>(null)
    private val stepPane = MutableStateFlow(StepPane.Compare)
    private val pairKeys = MutableStateFlow<Pair<String, String>?>(null)
    private val stepBusy = MutableStateFlow(false)
    private val pairGate = Mutex()
    private val pairs = StepPairs()
    val notices = MutableSharedFlow<Notice>(extraBufferCapacity = 4)
    val emojiChoices: StateFlow<List<String>> = combine(
        repository.observeEmojiUsage(),
        emojiMemory.remembered,
    ) { usage, remembered ->
        EmojiChoices.order(usage, remembered)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EmojiChoices.order(emptyMap(), emojiMemory.remembered.value),
    )

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

    val step: StateFlow<StepUi> = combine(
        combine(
            home,
            steps.observeStandings(),
            steps.observeComparisonCount(),
            pairKeys,
        ) { week, standings, comparisons, pair ->
            StepSnap(week, standings, comparisons, pair)
        },
        stepPane,
        stepBusy,
    ) { snap, pane, busy ->
        if (snap.week is HomeUi.Loading) {
            StepUi(pane, loading = true, cards = emptyList(), left = null, right = null, canUndo = snap.comparisons > 0, busy = busy)
        } else {
            val tasks = (snap.week as HomeUi.Ready).sprint?.blocks?.flatMap { it.tasks }.orEmpty()
            val cards = stepCards(StepGroups.active(tasks, snap.standings))
            val byKey = cards.associateBy { it.key }
            StepUi(
                pane = pane,
                loading = false,
                cards = cards,
                left = snap.pair?.first?.let(byKey::get),
                right = snap.pair?.second?.let(byKey::get),
                canUndo = snap.comparisons > 0,
                busy = busy,
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        StepUi(StepPane.Compare, loading = true, cards = emptyList(), left = null, right = null, canUndo = false, busy = false),
    )

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
        viewModelScope.launch(Dispatchers.IO) {
            val seen = emojiMemory.generation()
            val usage = try {
                repository.emojiUsage()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "emoji usage failed", e)
                emptyMap()
            }
            emojiMemory.rememberAll(usage.keys, seen)
        }
        publishClock()
        watchStepPairs()
    }

    fun onVisible() = publishClock()

    fun setStepPane(pane: StepPane) {
        stepPane.value = pane
    }

    fun chooseStep(leftKey: String, rightKey: String, outcome: StepElo.Outcome) {
        viewModelScope.launch(Dispatchers.IO) {
            pairGate.withLock {
                val pair = pairKeys.value ?: return@withLock
                if (pair.first != leftKey || pair.second != rightKey) return@withLock
                val active = StepGroups.active(currentTasks(), steps.standings())
                val left = active.firstOrNull { it.key == leftKey } ?: return@withLock
                val right = active.firstOrNull { it.key == rightKey } ?: return@withLock
                stepBusy.value = true
                try {
                    steps.record(left, right, outcome)
                    reselectStep(activeAfter = null)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "step compare failed", e)
                    notices.tryEmit(Notice.SaveFailed)
                } finally {
                    stepBusy.value = false
                }
            }
        }
    }

    fun undoStep() {
        viewModelScope.launch(Dispatchers.IO) {
            pairGate.withLock {
                stepBusy.value = true
                try {
                    if (steps.undo()) reselectStep(activeAfter = null)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "step undo failed", e)
                    notices.tryEmit(Notice.SaveFailed)
                } finally {
                    stepBusy.value = false
                }
            }
        }
    }

    fun noteCopied() {
        notices.tryEmit(Notice.PlanCopied)
    }

    fun noteCopyFailed() {
        notices.tryEmit(Notice.PlanCopyFailed)
    }

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
                emojiMemory.clear()
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
        editor.value = Editor.BlockEditor(
            blockId = null,
            title = "",
            colorKey = color,
            emoji = TaskText.DEFAULT_EMOJI,
        )
    }

    fun openBlock(blockId: Long) {
        val ready = planningReady() ?: return
        val block = ready.sprint?.blocks?.firstOrNull { it.id == blockId } ?: return
        editor.value = Editor.BlockEditor(
            block.id,
            block.title,
            block.colorKey,
            block.emoji.ifBlank { TaskText.DEFAULT_EMOJI },
        )
    }

    fun openNewTask(blockId: Long) {
        val ready = planningReady() ?: return
        val block = ready.sprint?.blocks?.firstOrNull { it.id == blockId } ?: return
        val emoji = block.emoji.ifBlank { TaskText.DEFAULT_EMOJI }
        editor.value = Editor.TaskEditor(taskId = null, blockId = blockId, emoji = emoji, title = "")
    }

    fun openTask(taskId: Long) {
        val ready = planningReady() ?: return
        val task = ready.sprint?.blocks?.asSequence()?.flatMap { it.tasks.asSequence() }?.firstOrNull { it.id == taskId }
            ?: return
        val blockId = ready.sprint.blocks.first { block -> block.tasks.any { it.id == taskId } }.id
        editor.value = Editor.TaskEditor(task.id, blockId, task.emoji, task.title)
    }

    fun saveBlock(blockId: Long?, title: String, colorKey: String, emoji: String) {
        val ready = planningReady() ?: return
        val sprint = ready.sprint ?: return
        val normalized = TaskText.blockTitle(title) ?: return
        val normalizedEmoji = TaskText.emoji(emoji)
        val today = ready.today
        editor.value = null
        write {
            val ok = if (blockId == null) {
                repository.addBlock(sprint.id, normalized, colorKey, normalizedEmoji, today)
            } else {
                repository.updateBlock(blockId, normalized, colorKey, normalizedEmoji, today)
            }
            if (ok) emojiMemory.remember(normalizedEmoji) else notices.tryEmit(Notice.PlanningClosed)
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
            if (ok) emojiMemory.remember(draft.emoji) else notices.tryEmit(Notice.PlanningClosed)
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

    private fun watchStepPairs() {
        viewModelScope.launch {
            combine(home, steps.observeStandings()) { week, standings ->
                val ready = week as? HomeUi.Ready ?: return@combine null
                StepGroups.active(ready.sprint?.blocks?.flatMap { it.tasks }.orEmpty(), standings)
            }.collect { active ->
                if (active == null) return@collect
                pairGate.withLock {
                    val current = pairKeys.value
                    val keys = active.map { it.key }.toSet()
                    if (current != null && current.first in keys && current.second in keys) return@withLock
                    reselectStep(active)
                }
            }
        }
    }

    private suspend fun reselectStep(activeAfter: List<StepOption>?) {
        val active = activeAfter ?: StepGroups.active(currentTasks(), steps.standings())
        val next = pairs.select(active, steps.history())
        pairKeys.value = next?.let { it.leftKey to it.rightKey }
    }

    private fun currentTasks(): List<Task> {
        val ready = home.value as? HomeUi.Ready ?: return emptyList()
        return ready.sprint?.blocks?.flatMap { it.tasks }.orEmpty()
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

    private data class StepSnap(
        val week: HomeUi,
        val standings: Map<String, StepStanding>,
        val comparisons: Int,
        val pair: Pair<String, String>?,
    )

    companion object {
        private const val TAG = "MyWeek"

        private fun stepCards(options: List<StepOption>): List<StepCard> =
            options.mapIndexed { index, option ->
                StepCard(
                    key = option.key,
                    emoji = option.emoji,
                    label = option.label,
                    openCount = option.openCount,
                    rating = option.rating.roundToInt(),
                    comparisonCount = option.comparisonCount,
                    rank = index + 1,
                )
            }

        fun factory(app: MyWeekApp): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                WeekViewModel(app.repository, app.steps, app.settings, app.emojiMemory, app.versionName) as T
        }
    }
}
