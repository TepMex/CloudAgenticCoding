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
import com.tepmex.cornegame.domain.ActionSession
import com.tepmex.cornegame.domain.AndroidKeyCodes
import com.tepmex.cornegame.domain.BoardLive
import com.tepmex.cornegame.domain.DrillKind
import com.tepmex.cornegame.domain.HardwareStep
import com.tepmex.cornegame.domain.InputEffect
import com.tepmex.cornegame.domain.InputSource
import com.tepmex.cornegame.domain.KeyModifier
import com.tepmex.cornegame.domain.Language
import com.tepmex.cornegame.domain.LegendTable
import com.tepmex.cornegame.domain.Resolved
import com.tepmex.cornegame.domain.TrainerAction
import com.tepmex.cornegame.domain.TypingSession
import com.tepmex.cornegame.domain.generatePrompt
import com.tepmex.cornegame.domain.offeredCharacters
import com.tepmex.cornegame.domain.serviceExercises
import com.tepmex.cornegame.domain.shortcutExercises
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
    Usb,
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
    val drill: DrillKind = DrillKind.TEXT,
    val actionSession: ActionSession? = null,
    val lastResolution: InputResolution? = null,
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

    fun resolutionLanguage(): Language {
        val current = _state.value
        return if (current.drill == DrillKind.TEXT) current.session.typingLanguage else current.settings.language
    }

    fun onHardwareModifiers(
        shift: Boolean,
        alt: Boolean,
        ctrl: Boolean = false,
        meta: Boolean = false,
    ) {
        _state.update { state ->
            val board = state.board
            if (
                board.hardwareShift == shift &&
                board.hardwareAlt == alt &&
                board.hardwareCtrl == ctrl &&
                board.hardwareMeta == meta
            ) {
                state
            } else {
                state.copy(
                    board = board.copy(
                        hardwareShift = shift,
                        hardwareAlt = alt,
                        hardwareCtrl = ctrl,
                        hardwareMeta = meta,
                    ),
                )
            }
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
        if (step.deliver.isEmpty()) {
            rememberResolution(trace, step, action = "-", matched = "none")
            debug(
                formatUiReaction(
                    before = before,
                    after = _state.value,
                    action = null,
                    ignored = false,
                    mistake = false,
                    moved = false,
                ),
            )
            return
        }
        var cursor = before
        var matched = "none"
        var label = "-"
        for (resolved in step.deliver) {
            val applied = route(resolved, origin = null)
            matched = when {
                applied.mistake -> "miss"
                applied.moved -> "match"
                else -> "ignored"
            }
            label = resolved.action.traceLabel()
            val after = _state.value
            debug(
                formatUiReaction(
                    before = cursor,
                    after = after,
                    action = resolved.action,
                    ignored = applied.ignored,
                    mistake = applied.mistake,
                    moved = applied.moved,
                ),
            )
            cursor = after
        }
        rememberResolution(trace, step, action = label, matched = matched)
    }

    private fun rememberResolution(
        trace: HardwareKeyTrace,
        step: HardwareStep,
        action: String,
        matched: String,
    ) {
        val resolution = InputResolution(
            source = step.source,
            deviceId = trace.deviceId,
            keyCode = trace.keyCode,
            keyLabel = trace.keyLabel,
            scanCode = trace.scanCode,
            unicodeChar = trace.unicodeChar,
            characters = trace.characters,
            modifiers = step.heldModifiers,
            language = step.language,
            action = action,
            matched = matched,
        )
        _state.update { it.copy(lastResolution = resolution) }
        debug(formatResolution(resolution))
    }

    private fun route(resolved: Resolved, origin: String?): ApplyResult {
        val current = _state.value
        if (current.drill != DrillKind.TEXT && current.actionSession != null) {
            return applyDrill(resolved, origin)
        }
        return applyAction(resolved.action, origin)
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

    /**
     * Палец на модификаторе. L1 и L2 только удерживаются: отпускание слой не защёлкивает.
     * Короткий тап Shift по-прежнему защёлкивает регистр, Alt — шаг смены языка.
     */
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
                TrainerAction.Ctrl -> board.copy(screenCtrl = down)
                TrainerAction.Meta -> board.copy(screenMeta = down)
                else -> board
            }
            state.copy(board = next)
        }
        if (down) {
            debug("TOUCH down ${action.traceLabel()} | ${uiDelta(before, _state.value)}")
            return
        }
        if (action == TrainerAction.Lower || action == TrainerAction.Upper) {
            if (screenModifierUsed) screenModifierUsed = false
            debug("TOUCH up ${action.traceLabel()} MOMENTARY | ${uiDelta(before, _state.value)}")
            return
        }
        if (screenModifierUsed) {
            screenModifierUsed = false
            debug("TOUCH up ${action.traceLabel()} HOLD-USED | ${uiDelta(before, _state.value)}")
            return
        }
        if (action == TrainerAction.Ctrl || action == TrainerAction.Meta) {
            debug("TOUCH up ${action.traceLabel()} SHORT | ${uiDelta(before, _state.value)}")
            return
        }
        debug("TOUCH up ${action.traceLabel()} SHORT | ${uiDelta(before, _state.value)}")
        onTrainerAction(action)
    }

    /** Палец ушёл, жест отменён, экран закрыт или активность потеряла фокус. */
    fun releaseScreenHolds() {
        screenModifierUsed = false
        _state.update { state ->
            val board = state.board
            if (!board.screenShift && !board.screenAlt && !board.screenCtrl && !board.screenMeta &&
                !board.screenLower && !board.screenUpper
            ) {
                state
            } else {
                state.copy(
                    board = board.copy(
                        screenShift = false,
                        screenAlt = false,
                        screenCtrl = false,
                        screenMeta = false,
                        screenLower = false,
                        screenUpper = false,
                    ),
                )
            }
        }
    }

    fun onScreenKey(keyCode: Int, action: TrainerAction) {
        if (action.isScreenModifier()) return
        val current = _state.value
        val mods = heldMods(current.board)
        val blocking = mods.any { it == KeyModifier.CTRL || it == KeyModifier.ALT || it == KeyModifier.META }
        if (current.drill == DrillKind.TEXT) {
            if (blocking && action is TrainerAction.Character) {
                screenModifierUsed = true
                debug("ACTION screen ${action.traceLabel()} BLOCKED mods=$mods")
                return
            }
            if (action is TrainerAction.Character &&
                action.value !in offeredCharacters(current.session, current.board, current.legends)
            ) {
                applyAction(action, origin = "screen", forceMiss = true)
                return
            }
            applyAction(action, origin = "screen")
            return
        }
        if (mods.isNotEmpty()) screenModifierUsed = true
        val code = when (action) {
            is TrainerAction.KeyPress -> action.keyCode
            is TrainerAction.Shortcut -> action.keyCode
            TrainerAction.Backspace -> AndroidKeyCodes.DEL
            else -> keyCode
        }
        val resolvedAction = when {
            code != 0 && (
                blocking ||
                    (KeyModifier.SHIFT in mods && action !is TrainerAction.Character && action !is TrainerAction.Backspace)
                ) -> TrainerAction.Shortcut(code, mods)
            else -> action
        }
        val resolved = Resolved(
            action = resolvedAction,
            keyCode = code,
            modifiers = (resolvedAction as? TrainerAction.Shortcut)?.modifiers ?: emptySet(),
            source = InputSource.UNKNOWN,
        )
        applyDrill(resolved, origin = "screen")
    }

    fun onTrainerAction(action: TrainerAction) {
        applyAction(action, origin = "screen")
    }

    private fun applyAction(action: TrainerAction, origin: String?, forceMiss: Boolean = false): ApplyResult {
        val current = _state.value
        if (action is TrainerAction.Character &&
            (
                current.board.screenShift || current.board.screenLower || current.board.screenUpper ||
                    current.board.screenCtrl || current.board.screenAlt || current.board.screenMeta
                )
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
        val (next, effect) = if (forceMiss && action is TrainerAction.Character) {
            current.session.rejectOffLayer(action.value, clock())
        } else {
            current.session.apply(action, clock())
        }
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

    private fun applyDrill(resolved: Resolved, origin: String?): ApplyResult {
        val current = _state.value
        val session = current.actionSession
        if (session == null || current.screen != AppScreen.Train || session.completed) {
            if (origin != null) {
                debug("ACTION $origin ${resolved.action.traceLabel()} IGNORED screen=${current.screen}")
            }
            return ApplyResult(ignored = true, mistake = false, moved = false)
        }
        val (next, effect) = session.apply(resolved, clock())
        _state.update {
            it.copy(
                actionSession = next,
                screen = if (effect.completed) AppScreen.Results else it.screen,
                errorNonce = if (effect.mistake) it.errorNonce + 1 else it.errorNonce,
                lastWrong = wrongChar(resolved.action, effect) ?: it.lastWrong,
                nowMs = clock(),
            )
        }
        val after = _state.value
        val moved = after.actionSession != current.actionSession || after.screen != current.screen
        if (origin != null) {
            val verdict = when {
                effect.mistake -> "MISS"
                moved -> "OK"
                else -> "NOOP"
            }
            debug("ACTION $origin ${resolved.action.traceLabel()} $verdict | ${uiDelta(current, after)}")
        }
        return ApplyResult(ignored = false, mistake = effect.mistake, moved = moved)
    }

    fun setDrill(kind: DrillKind) {
        if (kind == _state.value.drill) return
        screenModifierUsed = false
        _state.update {
            it.copy(
                drill = kind,
                actionSession = if (kind == DrillKind.TEXT) null else newActionSession(kind),
                screen = AppScreen.Train,
                errorNonce = 0,
                lastWrong = null,
                nowMs = clock(),
            )
        }
        debug("DRILL $kind | ${uiSnapshot(_state.value)}")
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
                    screenCtrl = false,
                    screenMeta = false,
                    screenLower = false,
                    screenUpper = false,
                ),
                actionSession = if (it.drill == DrillKind.TEXT) null else newActionSession(it.drill),
            )
        }
        val current = _state.value
        debug("CHIP lang=$language | target=\"${current.session.target}\" | ${uiSnapshot(current)}")
    }

    fun restart() {
        screenModifierUsed = false
        _state.update {
            if (it.drill != DrillKind.TEXT) {
                it.copy(
                    actionSession = newActionSession(it.drill),
                    screen = AppScreen.Train,
                    errorNonce = 0,
                    lastWrong = null,
                    nowMs = clock(),
                    board = it.board.copy(
                        screenShift = false,
                        screenAlt = false,
                        screenCtrl = false,
                        screenMeta = false,
                        screenLower = false,
                        screenUpper = false,
                    ),
                )
            } else {
                it.copy(
                    session = newSession(it.settings.language),
                    screen = AppScreen.Train,
                    errorNonce = 0,
                    lastWrong = null,
                    nowMs = clock(),
                    board = it.board.copy(
                        screenShift = false,
                        screenAlt = false,
                        screenCtrl = false,
                        screenMeta = false,
                        screenLower = false,
                        screenUpper = false,
                    ),
                )
            }
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
        releaseScreenHolds()
        _state.update { it.copy(screen = AppScreen.Progress, history = historyStore.load()) }
        debug("NAV Progress | ${uiSnapshot(_state.value)}")
    }

    fun openSettings() {
        releaseScreenHolds()
        _state.update { it.copy(screen = AppScreen.Settings) }
        debug("NAV Settings | ${uiSnapshot(_state.value)}")
    }

    fun openUsb() {
        releaseScreenHolds()
        _state.update { it.copy(screen = AppScreen.Usb) }
        debug("NAV Usb | ${uiSnapshot(_state.value)}")
    }

    /** Назад из прогресса, настроек и USB: к результату, если сессия уже кончилась. */
    fun closeOverlay() {
        _state.update {
            val done = if (it.drill == DrillKind.TEXT) {
                it.session.completed
            } else {
                it.actionSession?.completed == true
            }
            val screen = if (done) AppScreen.Results else AppScreen.Train
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

    private fun newActionSession(kind: DrillKind): ActionSession = ActionSession(
        kind = kind,
        steps = when (kind) {
            DrillKind.SERVICE -> serviceExercises()
            DrillKind.SHORTCUT -> shortcutExercises()
            DrillKind.TEXT -> emptyList()
        },
    )

    private fun heldMods(board: BoardLive): Set<KeyModifier> = buildSet {
        if (board.hardwareShift || board.screenShift) add(KeyModifier.SHIFT)
        if (board.hardwareCtrl || board.screenCtrl) add(KeyModifier.CTRL)
        if (board.hardwareAlt || board.screenAlt) add(KeyModifier.ALT)
        if (board.hardwareMeta || board.screenMeta) add(KeyModifier.META)
    }

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
        this == TrainerAction.Alt ||
        this == TrainerAction.Ctrl ||
        this == TrainerAction.Meta

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
