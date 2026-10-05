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
import com.tepmex.cornegame.domain.InputEffect
import com.tepmex.cornegame.domain.Language
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
)

class TrainViewModel(
    private val historyStore: SessionHistoryStore,
    private val settingsStore: SettingsStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val random: Random = Random.Default,
) : ViewModel() {
    private val _state: MutableStateFlow<TrainUiState>
    val state: StateFlow<TrainUiState>

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
        viewModelScope.launch {
            while (isActive) {
                delay(200)
                _state.update { it.copy(nowMs = clock()) }
            }
        }
    }

    fun onTrainerAction(action: TrainerAction) {
        val current = _state.value
        if (current.screen != AppScreen.Train || current.session.completed) return
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
    }

    fun setLanguage(language: Language) {
        // Повторный тап по уже выбранному языку сессию не трогает.
        if (language == _state.value.settings.language) return
        val settings = _state.value.settings.copy(language = language)
        settingsStore.save(settings)
        _state.update {
            it.copy(
                settings = settings,
                session = newSession(language),
                screen = AppScreen.Train,
                errorNonce = 0,
                lastWrong = null,
                nowMs = clock(),
            )
        }
    }

    fun restart() {
        _state.update {
            it.copy(
                session = newSession(it.settings.language),
                screen = AppScreen.Train,
                errorNonce = 0,
                lastWrong = null,
                nowMs = clock(),
            )
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        _state.update {
            val next = transform(it.settings)
            settingsStore.save(next)
            it.copy(settings = next)
        }
    }

    fun openProgress() {
        _state.update { it.copy(screen = AppScreen.Progress, history = historyStore.load()) }
    }

    fun openSettings() {
        _state.update { it.copy(screen = AppScreen.Settings) }
    }

    /** Назад из прогресса и настроек: к результату, если сессия уже кончилась. */
    fun closeOverlay() {
        _state.update {
            val screen = if (it.session.completed) AppScreen.Results else AppScreen.Train
            it.copy(screen = screen)
        }
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
}

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
