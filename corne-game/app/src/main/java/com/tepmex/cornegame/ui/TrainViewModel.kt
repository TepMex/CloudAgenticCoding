package com.tepmex.cornegame.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.cornegame.data.AppSettings
import com.tepmex.cornegame.data.FileSessionHistory
import com.tepmex.cornegame.data.FileSettingsStore
import com.tepmex.cornegame.data.SessionHistoryStore
import com.tepmex.cornegame.data.SessionRecord
import com.tepmex.cornegame.data.SettingsStore
import com.tepmex.cornegame.domain.BoardLive
import com.tepmex.cornegame.domain.HardwareStep
import com.tepmex.cornegame.domain.InputEffect
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.TypingSession
import com.tepmex.cornegame.domain.generatePrompt
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class AppScreen {
    Train,
    Results,
    Progress,
    Settings,
}

/**
 * Сессия живёт здесь, а не в remember: ViewModel остаётся при повороте,
 * композиция — нет. Смерть процесса сессию не восстанавливает.
 */
data class TrainUiState(
    val settings: AppSettings,
    val session: TypingSession,
    val screen: AppScreen,
    val nowMs: Long,
    val errorNonce: Int,
    val lastWrong: Char?,
    val history: List<SessionRecord>,
    val board: BoardLive = BoardLive(),
    val legends: LegendTable = LegendTable(),
)

class TrainViewModel(
    private val historyStore: SessionHistoryStore,
    private val settingsStore: SettingsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
) : ViewModel() {
    private var screenModifierUsed = false
    private var lastKeyboardSig: String? = null
    private val _state: MutableStateFlow<TrainUiState>
    val state: StateFlow<TrainUiState>
    private val _debug = MutableStateFlow<List<String>>(emptyList())
    val debugLog: StateFlow<List<String>> = _debug.asStateFlow()

    init {
        val settings = settingsStore.load()
        val initial = TrainUiState(
            settings = settings,
            session = newSession(settings.language),
            screen = AppScreen.Train,
            nowMs = clock(),
            errorNonce = 0,
            lastWrong = null,
            history = historyStore.load(),
        )
        _state = MutableStateFlow(initial)
        state = _state.asStateFlow()
        debug(
            "START target=\"${initial.session.target}\" lang=${initial.settings.language} | " +
                uiSnapshot(initial),
        )
        viewModelScope.launch {
            while (isActive) {
                delay(200)
                _state.update { it.copy(nowMs = clock()) }
            }
        }
    }

    fun onHardwareModifiers(shift: Boolean, alt: Boolean) {
        _state.update { state ->
            val board = state.board
            if (board.hardwareShift == shift && board.hardwareAlt == alt) state
            else state.copy(board = board.copy(hardwareShift = shift, hardwareAlt = alt))
        }
    }

    /** Состояние до разбора клавиши: по нему лог показывает, что изменилось после. */
    fun debugBaseline(): TrainUiState = _state.value

    fun noteBoot(versionName: String, versionCode: Long) {
        val current = _state.value
        debug(
            "BOOT $versionName ($versionCode) | ${uiSnapshot(current)} " +
                "target=\"${current.session.target}\"",
        )
    }

    /**
     * Раскладка, которую увидела активность. Пишем только когда подпись меняется,
     * иначе каждая клавиша повторяла бы одну и ту же строку.
     */
    fun noteKeyboard(
        deviceId: Int,
        found: Boolean,
        language: Language?,
        table: LegendTable,
        cleared: Boolean,
    ) {
        val sample = legendSample(table)
        val signature = "$deviceId|$found|${language ?: "-"}|$sample|$cleared"
        if (signature == lastKeyboardSig) return
        lastKeyboardSig = signature
        debug(
            "KBD device=$deviceId found=${bit(found)} lang=${language ?: "-"} " +
                "cleared=${bit(cleared)} $sample",
        )
    }

    /**
     * Аппаратная клавиша уже прошла через модификаторы и раскладку.
     * [before] снят до этих обновлений, поэтому строка UI видит и Shift, и символ.
     */
    fun onHardwareKey(before: TrainUiState, trace: HardwareKeyTrace, step: HardwareStep) {
        debug(formatKey(trace))
        debug(formatStep(step))
        val afterLayout = _state.value
        if (before.legends != afterLayout.legends || before.board.language != afterLayout.board.language) {
            debug(
                "LAYOUT kbd=${afterLayout.board.language ?: "-"} ${legendSample(afterLayout.legends)}",
            )
        }
        val deliver = step.deliver
        val applied = deliver?.let { applyAction(it, origin = null) }
        val after = _state.value
        debug(
            formatUiReaction(
                before = before,
                after = after,
                action = deliver,
                ignored = applied?.ignored == true,
                mistake = applied?.mistake == true,
                moved = applied?.moved == true,
            ),
        )
    }

    fun onPointer(
        phase: String,
        id: Int,
        legend: String,
        action: TrainerAction,
        released: Boolean?,
    ) {
        val tail = if (released == null) "" else " released=${bit(released)}"
        debug("POINTER $phase id=$id legend=$legend action=${action.traceLabel()}$tail")
    }

    fun clearDebug() {
        _debug.value = emptyList()
        val current = _state.value
        debug("CLEAR | ${uiSnapshot(current)} target=\"${current.session.target}\"")
    }

    fun onInputLanguage(language: Language?) {
        _state.update { state ->
            if (state.board.language == language) state
            else state.copy(board = state.board.copy(language = language))
        }
    }

    fun onLegendTable(table: LegendTable) {
        _state.update { state ->
            if (state.legends == table) state else state.copy(legends = table)
        }
    }

    /** Палец на Shift, Alt, L1 или L2. Пока зажат, легенды живые. Короткий тап защёлкивает. */
    fun onKeyContact(action: TrainerAction, down: Boolean) {
        if (!action.isScreenModifier()) return
        val before = _state.value
        _state.update { state ->
            val board = state.board
            val next = when (action) {
                TrainerAction.Shift -> board.copy(screenShift = down)
                TrainerAction.Lower -> board.copy(screenLower = down)
                TrainerAction.Upper -> board.copy(screenUpper = down)
                TrainerAction.Alt -> board.copy(screenAlt = down)
                else -> board
            }
            state.copy(board = next)
        }
        if (down) {
            debug("TOUCH down ${action.traceLabel()} | ${uiDelta(before, _state.value)}")
            return
        }
        if (screenModifierUsed) {
            screenModifierUsed = false
            debug("TOUCH up ${action.traceLabel()} HOLD-USED | ${uiDelta(before, _state.value)}")
        } else {
            debug("TOUCH up ${action.traceLabel()} SHORT | ${uiDelta(before, _state.value)}")
            onTrainerAction(action)
        }
    }

    fun onTrainerAction(action: TrainerAction) {
        applyAction(action, origin = "screen")
    }

    private fun applyAction(action: TrainerAction, origin: String?): ApplyResult {
        val current = _state.value
        if (action is TrainerAction.Character &&
            (current.board.screenShift || current.board.screenLower || current.board.screenUpper)
        ) {
            screenModifierUsed = true
        }
        if (current.screen != AppScreen.Train || current.session.completed) {
            if (origin != null) {
                debug(
                    "ACTION $origin ${action.traceLabel()} IGNORED " +
                        "screen=${current.screen} done=${bit(current.session.completed)}",
                )
            }
            return ApplyResult(ignored = true, mistake = false, moved = false)
        }
        val (next, effect) = current.session.apply(action, clock())
        if (effect.completed && !current.session.completed) {
            persist(next)
        }
        _state.update {
            it.copy(
                session = next,
                screen = if (effect.completed) AppScreen.Results else it.screen,
                errorNonce = if (effect.mistake) it.errorNonce + 1 else it.errorNonce,
                lastWrong = wrongChar(action, effect) ?: it.lastWrong,
                nowMs = clock(),
                history = if (effect.completed) historyStore.load() else it.history,
            )
        }
        val after = _state.value
        val moved = after.session != current.session ||
            after.screen != current.screen ||
            after.errorNonce != current.errorNonce
        if (origin != null) {
            val verdict = when {
                effect.mistake -> "MISS expected=${showChar(current.session.nextChar)}"
                moved -> "OK"
                else -> "NOOP"
            }
            debug("ACTION $origin ${action.traceLabel()} $verdict | ${uiDelta(current, after)}")
        }
        return ApplyResult(ignored = false, mistake = effect.mistake, moved = moved)
    }

    fun setLanguage(language: Language) {
        // Повторный тап по уже выбранному языку сессию не трогает.
        if (language == _state.value.settings.language) return
        val settings = _state.value.settings.copy(language = language)
        settingsStore.save(settings)
        screenModifierUsed = false
        _state.update {
            it.copy(
                settings = settings,
                session = newSession(language),
                screen = AppScreen.Train,
                errorNonce = 0,
                lastWrong = null,
                nowMs = clock(),
                board = it.board.copy(
                    screenShift = false,
                    screenAlt = false,
                    screenLower = false,
                    screenUpper = false,
                ),
            )
        }
        val current = _state.value
        debug("CHIP lang=$language | target=\"${current.session.target}\" | ${uiSnapshot(current)}")
    }

    fun restart() {
        screenModifierUsed = false
        _state.update {
            it.copy(
                session = newSession(it.settings.language),
                screen = AppScreen.Train,
                errorNonce = 0,
                lastWrong = null,
                nowMs = clock(),
                board = it.board.copy(
                    screenShift = false,
                    screenAlt = false,
                    screenLower = false,
                    screenUpper = false,
                ),
            )
        }
        val current = _state.value
        debug("RESTART | target=\"${current.session.target}\" | ${uiSnapshot(current)}")
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _state.update {
            val next = transform(it.settings)
            settingsStore.save(next)
            it.copy(settings = next)
        }
        debug("SETTINGS | ${uiSnapshot(_state.value)}")
    }

    fun openProgress() {
        _state.update { it.copy(screen = AppScreen.Progress, history = historyStore.load()) }
        debug("NAV Progress | ${uiSnapshot(_state.value)}")
    }

    fun openSettings() {
        _state.update { it.copy(screen = AppScreen.Settings) }
        debug("NAV Settings | ${uiSnapshot(_state.value)}")
    }

    /** Назад из прогресса и настроек: к результату, если сессия уже кончилась. */
    fun closeOverlay() {
        _state.update {
            val screen = if (it.session.completed) AppScreen.Results else AppScreen.Train
            it.copy(screen = screen)
        }
        debug("NAV ${_state.value.screen} | ${uiSnapshot(_state.value)}")
    }

    private fun persist(session: TypingSession) {
        val finished = session.finishedAtMs ?: return
        val stats = session.stats(finished)
        historyStore.append(
            SessionRecord(
                finishedAtEpochMs = finished,
                language = session.language,
                wpm = stats.wpm,
                accuracy = stats.accuracyPercent,
                errors = stats.errors,
                durationMs = stats.elapsedMs,
            ),
        )
    }

    private fun newSession(language: Language): TypingSession =
        TypingSession(
            language = language,
            target = generatePrompt(language, random),
        )

    private fun wrongChar(action: TrainerAction, effect: InputEffect): Char? {
        if (!effect.mistake) return null
        return (action as? TrainerAction.Character)?.value
    }

    private fun debug(line: String) {
        val stamped = "${debugStamp(clock())} $line"
        _debug.update { (it + stamped).takeLast(DEBUG_LOG_LIMIT) }
    }
}

private data class ApplyResult(
    val ignored: Boolean,
    val mistake: Boolean,
    val moved: Boolean,
)

private fun bit(value: Boolean): Int = if (value) 1 else 0

private fun TrainerAction.isScreenModifier(): Boolean =
    this == TrainerAction.Shift ||
        this == TrainerAction.Lower ||
        this == TrainerAction.Upper ||
        this == TrainerAction.Alt

fun trainViewModelFactory(context: Context): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val dir = context.applicationContext.filesDir
            @Suppress("UNCHECKED_CAST")
            return TrainViewModel(
                historyStore = FileSessionHistory(File(dir, "sessions.json")),
                settingsStore = FileSettingsStore(File(dir, "settings.json")),
            ) as T
        }
    }
