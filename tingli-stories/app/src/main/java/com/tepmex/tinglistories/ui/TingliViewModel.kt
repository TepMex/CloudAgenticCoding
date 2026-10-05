package com.tepmex.tinglistories.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.tinglistories.data.LibraryStore
import com.tepmex.tinglistories.data.LlmClient
import com.tepmex.tinglistories.data.LlmSettings
import com.tepmex.tinglistories.data.SettingsStore
import com.tepmex.tinglistories.data.StoryPlayer
import com.tepmex.tinglistories.domain.AudioKind
import com.tepmex.tinglistories.domain.Library
import com.tepmex.tinglistories.domain.alignedAnswers
import com.tepmex.tinglistories.domain.clampSeek
import com.tepmex.tinglistories.domain.evaluationSystemPrompt
import com.tepmex.tinglistories.domain.evaluationUserPrompt
import com.tepmex.tinglistories.domain.importMessage
import com.tepmex.tinglistories.domain.nextStoryId
import com.tepmex.tinglistories.domain.parseEvaluation
import com.tepmex.tinglistories.domain.shouldCountPlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Library : Screen
    data class Listen(val storyId: Int) : Screen
    data class Answer(val storyId: Int) : Screen
    data object Stats : Screen
    data object Settings : Screen
}

data class Playing(val storyId: Int, val kind: AudioKind)

data class TingliUiState(
    val ready: Boolean = false,
    val library: Library = Library(),
    val settings: LlmSettings = LlmSettings(),
    val screen: Screen = Screen.Library,
    val draftStoryId: Int? = null,
    val draftAnswers: List<String> = emptyList(),
    val playing: Playing? = null,
    val playbackPositionMs: Int = 0,
    val playbackDurationMs: Int = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val confirmReplace: Boolean = false,
    val confirmReset: Boolean = false,
    val showToken: Boolean = false,
)

class TingliViewModel(app: Application) : AndroidViewModel(app) {
    private val store = LibraryStore(File(app.filesDir, "tingli"))
    private val settingsStore = SettingsStore(app)
    private val llm = LlmClient()
    private val player = StoryPlayer()
    private var positionJob: Job? = null
    private val stack = ArrayDeque<Screen>()
    private val disk = Mutex()
    private var pendingImport: (() -> Unit)? = null
    private var playToken = 0
    private var seekHoldUntilMs = 0L
    private var seekTargetMs = 0
    private var libraryReady = false
    private var settingsReady = false

    private val _state = MutableStateFlow(TingliUiState())
    val state: StateFlow<TingliUiState> = _state

    init {
        viewModelScope.launch {
            val library = withContext(Dispatchers.IO) { store.load() }
            libraryReady = true
            _state.update { it.copy(library = library, ready = settingsReady) }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                settingsReady = true
                _state.update { it.copy(settings = settings, ready = libraryReady) }
            }
        }
    }

    fun importFromUri(uri: Uri) {
        requestImport {
            viewModelScope.launch {
                val file = try {
                    withContext(Dispatchers.IO) { copyToCache(uri) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _state.update { it.copy(message = e.message ?: "Не удалось открыть архив") }
                    return@launch
                }
                importFile(file)
            }
        }
    }

    fun importSample() {
        requestImport {
            viewModelScope.launch {
                val file = try {
                    withContext(Dispatchers.IO) { copySample() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    _state.update { it.copy(message = e.message ?: "Пример не найден в приложении") }
                    return@launch
                }
                importFile(file)
            }
        }
    }

    fun confirmReplace() {
        val action = pendingImport
        pendingImport = null
        _state.update { it.copy(confirmReplace = false) }
        action?.invoke()
    }

    fun cancelReplace() {
        pendingImport = null
        _state.update { it.copy(confirmReplace = false) }
    }

    fun openStory(storyId: Int) = navigate(Screen.Listen(storyId))

    fun openStats() = navigate(Screen.Stats)

    fun openSettings() = navigate(Screen.Settings)

    fun openAnswer(storyId: Int) {
        val snapshot = _state.value
        val story = snapshot.library.story(storyId) ?: return
        val progress = snapshot.library.progressOf(storyId)
        val drafts = if (progress.completed) {
            alignedAnswers(story.questions.size, progress.answers)
        } else if (snapshot.draftStoryId == storyId) {
            alignedAnswers(story.questions.size, snapshot.draftAnswers)
        } else {
            List(story.questions.size) { "" }
        }
        navigate(Screen.Answer(storyId))
        _state.update { it.copy(draftStoryId = storyId, draftAnswers = drafts) }
    }

    fun onDraftChange(index: Int, text: String) {
        _state.update { snapshot ->
            val storyId = (snapshot.screen as? Screen.Answer)?.storyId ?: snapshot.draftStoryId ?: return@update snapshot
            val count = snapshot.library.story(storyId)?.questions?.size ?: return@update snapshot
            if (snapshot.library.progressOf(storyId).completed) return@update snapshot
            val answers = alignedAnswers(count, snapshot.draftAnswers).toMutableList()
            if (index !in answers.indices) return@update snapshot
            answers[index] = text
            snapshot.copy(draftAnswers = answers, draftStoryId = storyId)
        }
    }

    fun play(storyId: Int, kind: AudioKind) {
        val story = _state.value.library.story(storyId) ?: return
        if (!shouldCountPlay(story, kind)) return
        val token = ++playToken
        seekHoldUntilMs = 0
        cancelPositionUpdates()
        viewModelScope.launch {
            val audio = disk.withLock {
                val file = withContext(Dispatchers.IO) { store.audioFile(story, kind) }
                if (file == null || !file.isFile) {
                    if (token == playToken) {
                        _state.update { it.copy(message = "Файл аудио не найден") }
                    }
                    null
                } else {
                    val library = withContext(Dispatchers.IO) { store.recordListen(storyId) }
                    _state.update { snapshot ->
                        snapshot.copy(
                            library = library,
                            playing = if (token == playToken) Playing(storyId, kind) else snapshot.playing,
                            playbackPositionMs = if (token == playToken) 0 else snapshot.playbackPositionMs,
                            playbackDurationMs = if (token == playToken) 0 else snapshot.playbackDurationMs,
                        )
                    }
                    file
                }
            }
            if (audio == null || token != playToken) return@launch
            try {
                player.play(audio) {
                    if (token != playToken) return@play
                    cancelPositionUpdates()
                    _state.update { snapshot ->
                        if (snapshot.playing == Playing(storyId, kind)) {
                            snapshot.withoutPlayback()
                        } else {
                            snapshot
                        }
                    }
                }
                if (token != playToken || _state.value.playing != Playing(storyId, kind)) return@launch
                val duration = player.durationMs()
                _state.update { snapshot ->
                    if (snapshot.playing != Playing(storyId, kind)) snapshot
                    else snapshot.copy(playbackPositionMs = 0, playbackDurationMs = duration)
                }
                cancelPositionUpdates()
                positionJob = viewModelScope.launch {
                    while (isActive && token == playToken) {
                        val reported = player.positionMs()
                        val position = if (
                            SystemClock.uptimeMillis() < seekHoldUntilMs &&
                            kotlin.math.abs(reported - seekTargetMs) > 800
                        ) {
                            seekTargetMs
                        } else {
                            reported
                        }
                        val liveDuration = player.durationMs()
                        if (token != playToken || _state.value.playing != Playing(storyId, kind)) break
                        _state.update { snapshot ->
                            if (token != playToken || snapshot.playing != Playing(storyId, kind)) snapshot
                            else snapshot.copy(
                                playbackPositionMs = position,
                                playbackDurationMs = liveDuration.coerceAtLeast(snapshot.playbackDurationMs),
                            )
                        }
                        delay(200)
                    }
                }
            } catch (_: Exception) {
                cancelPositionUpdates()
                if (token == playToken) {
                    _state.update {
                        it.withoutPlayback().copy(message = "Не удалось воспроизвести аудио")
                    }
                }
            }
        }
    }

    fun seekPlayback(positionMs: Int) {
        if (_state.value.playing == null) return
        val duration = player.durationMs().let { live ->
            if (live > 0) live else _state.value.playbackDurationMs
        }
        val target = clampSeek(positionMs, duration)
        seekTargetMs = target
        seekHoldUntilMs = SystemClock.uptimeMillis() + 400
        player.seekTo(target)
        _state.update { current ->
            if (current.playing == null) current else current.copy(playbackPositionMs = target)
        }
    }

    fun submit(storyId: Int) {
        val snapshot = _state.value
        val story = snapshot.library.story(storyId) ?: return
        if (story.questions.isEmpty()) {
            _state.update { it.copy(message = "В этой истории нет вопросов") }
            return
        }
        if (snapshot.library.progressOf(storyId).completed) return
        if (!snapshot.settings.endpointReady) {
            _state.update { it.copy(message = "Укажите base URL и модель в настройках") }
            return
        }
        if (snapshot.busy) return
        val answers = alignedAnswers(story.questions.size, snapshot.draftAnswers)
        _state.update { it.copy(busy = true, message = null, draftAnswers = answers, draftStoryId = storyId) }
        viewModelScope.launch {
            try {
                val raw = llm.complete(
                    baseUrl = snapshot.settings.baseUrl,
                    token = snapshot.settings.token,
                    model = snapshot.settings.model,
                    system = evaluationSystemPrompt(),
                    user = evaluationUserPrompt(story, answers),
                )
                val evaluation = parseEvaluation(raw, story.questions.size)
                disk.withLock {
                    val library = withContext(Dispatchers.IO) { store.saveAttempt(storyId, answers, evaluation) }
                    _state.update { it.copy(library = library, busy = false) }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.update { it.copy(busy = false, message = e.message ?: "Не удалось получить оценку") }
            }
        }
    }

    fun requestReset() {
        _state.update { it.copy(confirmReset = true) }
    }

    fun confirmReset() {
        val storyId = currentStoryId() ?: run {
            _state.update { it.copy(confirmReset = false) }
            return
        }
        viewModelScope.launch {
            disk.withLock {
                val library = withContext(Dispatchers.IO) { store.resetAnswer(storyId) }
                val count = library.story(storyId)?.questions?.size ?: 0
                _state.update {
                    it.copy(
                        library = library,
                        confirmReset = false,
                        draftStoryId = storyId,
                        draftAnswers = List(count) { "" },
                        message = "Ответ сброшен",
                    )
                }
            }
        }
    }

    fun cancelReset() {
        _state.update { it.copy(confirmReset = false) }
    }

    fun goNext(storyId: Int) {
        haltAudio()
        val next = nextStoryId(_state.value.library.stories, storyId)
        stack.clear()
        if (next == null) {
            _state.update { it.withoutPlayback().copy(screen = Screen.Library) }
        } else {
            stack.addLast(Screen.Library)
            _state.update {
                it.withoutPlayback().copy(
                    screen = Screen.Listen(next),
                    draftAnswers = emptyList(),
                    draftStoryId = null,
                )
            }
        }
    }

    fun saveSettings(baseUrl: String, token: String, model: String) {
        viewModelScope.launch {
            settingsStore.save(baseUrl, token, model)
            _state.update { it.copy(message = "Настройки сохранены") }
        }
    }

    fun toggleToken() {
        _state.update { it.copy(showToken = !it.showToken) }
    }

    fun back() {
        haltAudio()
        if (_state.value.confirmReplace) {
            cancelReplace()
            return
        }
        if (_state.value.confirmReset) {
            cancelReset()
            return
        }
        val previous = if (stack.isEmpty()) Screen.Library else stack.removeLast()
        _state.update { it.withoutPlayback().copy(screen = previous) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    override fun onCleared() {
        cancelPositionUpdates()
        player.stop()
    }

    private fun currentStoryId(): Int? = when (val screen = _state.value.screen) {
        is Screen.Listen -> screen.storyId
        is Screen.Answer -> screen.storyId
        else -> null
    }

    private fun navigate(screen: Screen) {
        haltAudio()
        val current = _state.value.screen
        if (current != screen) stack.addLast(current)
        _state.update { it.withoutPlayback().copy(screen = screen) }
    }

    private fun requestImport(block: () -> Unit) {
        viewModelScope.launch {
            _state.first { it.ready }
            if (_state.value.busy) return@launch
            if (_state.value.library.stories.isEmpty()) {
                block()
            } else {
                pendingImport = block
                _state.update { it.copy(confirmReplace = true) }
            }
        }
    }

    private suspend fun importFile(file: File) {
        haltAudio()
        _state.update { it.withoutPlayback().copy(busy = true, message = null) }
        try {
            disk.withLock {
                val outcome = withContext(Dispatchers.IO) { store.importZip(file) }
                stack.clear()
                _state.update {
                    it.copy(
                        library = outcome.library,
                        screen = Screen.Library,
                        busy = false,
                        draftAnswers = emptyList(),
                        draftStoryId = null,
                        message = importMessage(
                            outcome.storyCount,
                            outcome.storyAudioCount,
                            outcome.questionAudioCount,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.update { it.copy(busy = false, message = e.message ?: "Не удалось импортировать архив") }
        }
    }

    private fun haltAudio() {
        playToken++
        cancelPositionUpdates()
        player.stop()
    }

    private fun cancelPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun TingliUiState.withoutPlayback(): TingliUiState =
        copy(playing = null, playbackPositionMs = 0, playbackDurationMs = 0)

    private fun copyToCache(uri: Uri): File {
        val dest = File(getApplication<Application>().cacheDir, "import-${System.currentTimeMillis()}.zip")
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Не удалось открыть архив")
        input.use { stream -> dest.outputStream().use { stream.copyTo(it) } }
        return dest
    }

    private fun copySample(): File {
        val dest = File(getApplication<Application>().cacheDir, "hsk4_sample.zip")
        getApplication<Application>().assets.open("hsk4_sample.zip").use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
        return dest
    }
}
