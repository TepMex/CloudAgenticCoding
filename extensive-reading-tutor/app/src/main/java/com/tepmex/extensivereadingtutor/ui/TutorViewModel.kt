package com.tepmex.extensivereadingtutor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.extensivereadingtutor.TutorGraph
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.HintDepth
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.LessonSession
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.domain.SubmittedAnswer
import com.tepmex.extensivereadingtutor.domain.defaultPreferences
import com.tepmex.extensivereadingtutor.domain.explain
import com.tepmex.extensivereadingtutor.llm.parseTimeoutSeconds
import com.tepmex.extensivereadingtutor.llm.validateExtraBody
import java.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TutorViewModel(private val graph: TutorGraph) : ViewModel() {
    private val _state = MutableStateFlow(TutorUiState())
    val state: StateFlow<TutorUiState> = _state
    private val gate = Mutex()
    private val draftJobs = mutableMapOf<String, Job>()

    init {
        launch(null) { reload() }
    }

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab, route = Route.Tabs, word = null, confirmRemove = false) }
    }

    fun openBook(id: String) {
        _state.update { it.copy(route = Route.Book(id), word = null, confirmRemove = false, error = null) }
    }

    fun backToShelf() = selectTab(_state.value.tab)

    fun openSettings() {
        _state.update { it.copy(route = Route.Settings, word = null, probe = null, error = null) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissWord() = _state.update { it.copy(word = null, selectedWord = null) }

    fun editConnection(baseUrl: String, model: String, apiKey: String, extraBodyJson: String, timeoutSeconds: String) {
        _state.update {
            it.copy(
                baseUrl = baseUrl,
                modelName = model,
                apiKey = apiKey,
                extraBodyJson = extraBodyJson,
                timeoutSeconds = timeoutSeconds,
                connectionDirty = true,
                probe = null,
            )
        }
    }

    fun openJournal() {
        _state.update { it.copy(route = Route.AiLog, exchanges = graph.exchanges.list().map { row -> row.toRow() }, word = null) }
    }

    fun closeJournal() {
        _state.update { it.copy(route = Route.Settings) }
    }

    fun openExchange(id: String) {
        val item = graph.exchanges.list().firstOrNull { it.id == id } ?: return
        _state.update { it.copy(route = Route.AiExchange(id), exchange = item.toView()) }
    }

    fun closeExchange() = openJournal()

    fun openRaw(side: RawSide) {
        val id = _state.value.exchange?.id ?: return
        _state.update { it.copy(route = Route.AiRaw(id, side)) }
    }

    fun closeRaw() {
        val id = (_state.value.route as? Route.AiRaw)?.id ?: _state.value.exchange?.id ?: return
        openExchange(id)
    }

    fun clearJournal() {
        graph.exchanges.clear()
        _state.update { it.copy(exchanges = emptyList(), exchange = null) }
    }

    fun saveEndpoint() = launch("Сохраняю модель") {
        val current = _state.value
        val timeout = parseTimeoutSeconds(current.timeoutSeconds)
        val extraError = validateExtraBody(current.extraBodyJson)
        when {
            timeout == null -> _state.update { it.copy(error = "Таймаут — целое число секунд от 1 до 1800.") }
            extraError != null -> _state.update { it.copy(error = extraError) }
            else -> {
                graph.saveSettings(
                    current.baseUrl,
                    current.modelName,
                    current.apiKey.ifBlank { null },
                    current.extraBodyJson,
                    timeout,
                )
                _state.update {
                    it.copy(
                        keyStored = graph.hasApiKey(),
                        apiKey = "",
                        extraBodyJson = current.extraBodyJson.trim(),
                        timeoutSeconds = timeout.toString(),
                        connectionDirty = false,
                        probe = "Настройки сохранены.",
                    )
                }
            }
        }
    }

    fun probe() = launch("Проверяю соединение") {
        when (val result = graph.probeModel()) {
            is AppResult.Ok -> _state.update { it.copy(probe = "Соединение есть. Ответ: ${result.value.take(80)}") }
            is AppResult.Err -> _state.update { it.copy(error = result.error.explain()) }
        }
    }

    fun chooseLevel(level: String) = savePreference { it.copy(selfAssessedLevel = level) }
    fun chooseLanguage(language: String) = savePreference { it.copy(explanationLanguage = language) }
    fun chooseDifficulty(difficulty: DifficultyPreference) = savePreference { it.copy(preferredDifficulty = difficulty) }
    fun chooseDepth(depth: HintDepth) = savePreference { it.copy(hintDepth = depth) }
    fun chooseLength(length: Int) = savePreference { it.copy(targetFragmentCodePoints = length) }

    fun import(bytes: ByteArray, uri: String) = launch("Импортирую EPUB") {
        when (val result = graph.books.import(bytes, uri, "zh")) {
            is AppResult.Err -> _state.update { it.copy(error = result.error.explain()) }
            is AppResult.Ok -> {
                reload()
                _state.update { it.copy(route = Route.Book(result.value.id.value), tab = Tab.BOOKS) }
            }
        }
    }

    fun openLesson(bookId: String) = launch("Открываю занятие") {
        when (val result = graph.lessons.startOrResumeLesson(BookId(bookId))) {
            is AppResult.Err -> _state.update { it.copy(error = result.error.explain()) }
            is AppResult.Ok -> {
                _state.update { it.copy(route = Route.Lesson(bookId), checkingAnswers = false, word = null) }
                showLesson(result.value, keepDrafts = false)
                reload()
            }
        }
    }

    fun prepare() = lessonAction("Готовлю занятие") { graph.lessons.prepareLesson(SessionId(it)) }

    fun reveal() = lessonAction("Открываю вопросы") { graph.lessons.revealQuestions(SessionId(it)) }

    fun showCheck() = _state.update { it.copy(checkingAnswers = true) }

    fun hideCheck() = _state.update { it.copy(checkingAnswers = false) }

    fun editDraft(questionId: String, text: String) {
        _state.update { state ->
            val lesson = state.lesson ?: return@update state
            state.copy(lesson = lesson.copy(drafts = lesson.drafts + (questionId to text)))
        }
        draftJobs[questionId]?.cancel()
        draftJobs[questionId] = viewModelScope.launch {
            delay(350)
            if (!isActive) return@launch
            val lesson = _state.value.lesson ?: return@launch
            when (val result = graph.lessons.saveAnswerDraft(SessionId(lesson.sessionId), QuestionId(questionId), text)) {
                is AppResult.Err -> if (isActive) _state.update { it.copy(error = result.error.explain()) }
                is AppResult.Ok -> if (isActive) {
                    _state.update { state ->
                        state.copy(lesson = state.lesson?.copy(status = result.value.status))
                    }
                }
            }
        }
    }

    fun setDifficulty(difficulty: SubjectiveDifficulty) {
        _state.update { it.copy(lesson = it.lesson?.copy(difficulty = difficulty)) }
    }

    fun setComment(comment: String) {
        _state.update { it.copy(lesson = it.lesson?.copy(comment = comment)) }
    }

    fun submit() {
        draftJobs.values.forEach { it.cancel() }
        draftJobs.clear()
        lessonAction("Отправляю ответы") { sessionId ->
            val lesson = _state.value.lesson ?: return@lessonAction AppResult.Err(
                com.tepmex.extensivereadingtutor.domain.DomainError.LessonNotReady("Занятие не открыто."),
            )
            val submission = StudentSubmission(
                sessionId = SessionId(sessionId),
                answers = lesson.questions.map { SubmittedAnswer(QuestionId(it.id), lesson.drafts[it.id].orEmpty()) },
                difficulty = lesson.difficulty,
                wantsMoreExplanation = lesson.difficulty == SubjectiveDifficulty.TOO_HARD,
                unclearNotes = null,
                freeComment = lesson.comment.ifBlank { null },
                hintRequestCount = 0,
                submittedAt = Instant.now(),
            )
            when (val submitted = graph.lessons.submitAnswers(SessionId(sessionId), submission)) {
                is AppResult.Err -> submitted
                is AppResult.Ok -> graph.lessons.evaluateAnswers(SessionId(sessionId))
            }
        }
    }

    fun evaluateAgain() = lessonAction("Повторяю разбор") { graph.lessons.evaluateAnswers(SessionId(it)) }

    fun complete() = launch("Завершаю занятие") {
        val lesson = _state.value.lesson ?: return@launch
        when (val result = graph.lessons.completeLesson(SessionId(lesson.sessionId))) {
            is AppResult.Err -> _state.update { it.copy(error = result.error.explain()) }
            is AppResult.Ok -> {
                reload()
                _state.update { it.copy(route = Route.Book(lesson.bookId), lesson = null, checkingAnswers = false) }
            }
        }
    }

    fun leaveLesson() {
        val bookId = _state.value.lesson?.bookId ?: ( _state.value.route as? Route.Lesson)?.bookId
        _state.update {
            it.copy(
                route = if (bookId == null) Route.Tabs else Route.Book(bookId),
                checkingAnswers = false,
                word = null,
            )
        }
    }

    fun openExpression(expression: String, sentence: String) {
        val current = _state.value
        val card = wordCard(expression, sentence, current.concepts, current.knowledge, current.observations)
        _state.update {
            it.copy(
                selectedWord = expression,
                word = WordSheetUi(card.expression, card.sentence, card.gloss, card.example, card.status, card.skill),
            )
        }
    }

    fun askRemove() = _state.update { it.copy(confirmRemove = true) }

    fun cancelRemove() = _state.update { it.copy(confirmRemove = false) }

    fun removeBook(id: String) = launch("Убираю с полки") {
        when (val result = graph.books.removeFromLibrary(BookId(id))) {
            is AppResult.Err -> _state.update { it.copy(error = result.error.explain(), confirmRemove = false) }
            is AppResult.Ok -> {
                reload()
                _state.update { it.copy(route = Route.Tabs, tab = Tab.BOOKS, confirmRemove = false) }
            }
        }
    }

    private fun savePreference(transform: (LearnerPreferences) -> LearnerPreferences) = launch(null) {
        val now = Instant.now()
        val current = _state.value.preferences ?: defaultPreferences(now)
        graph.learner.savePreferences(transform(current).copy(updatedAt = now))
        reload()
    }

    private fun lessonAction(label: String, block: suspend (String) -> AppResult<LessonSession>) = launch(label) {
        val sessionId = _state.value.lesson?.sessionId ?: return@launch
        when (val result = block(sessionId)) {
            is AppResult.Err -> {
                _state.update { it.copy(error = result.error.explain()) }
                graph.read { it.session(SessionId(sessionId)) }?.let { showLesson(it, keepDrafts = true) }
            }
            is AppResult.Ok -> {
                _state.update { it.copy(checkingAnswers = false) }
                showLesson(result.value, keepDrafts = true)
                reload()
            }
        }
    }

    private suspend fun reload() {
        graph.lessons.recoverInterruptedSessions()
        val settings = graph.loadSettings()
        if (settings.model.isNotBlank()) {
            graph.models.snapshot = graph.models.snapshot.copy(model = settings.model)
        }
        val loaded = graph.read { loadShelf(it) }
        val preferences = graph.learner.preferences(com.tepmex.extensivereadingtutor.domain.LOCAL_LEARNER)
        _state.update { current ->
            current.copy(
                shelf = loaded.books,
                knowledge = loaded.knowledge,
                concepts = loaded.concepts,
                observations = loaded.observations,
                completedDays = loaded.completedDays,
                observationDays = loaded.observationDays,
                preferences = preferences,
                baseUrl = if (current.connectionDirty) current.baseUrl else settings.baseUrl,
                modelName = if (current.connectionDirty) current.modelName else settings.model,
                extraBodyJson = if (current.connectionDirty) current.extraBodyJson else settings.extraBodyJson,
                timeoutSeconds = if (current.connectionDirty) current.timeoutSeconds else settings.timeoutSeconds.toString(),
                keyStored = graph.hasApiKey(),
            )
        }
    }

    private suspend fun showLesson(session: LessonSession, keepDrafts: Boolean) {
        val fragment = graph.lessons.fragment(session.fragmentId)
        val stored = graph.lessons.drafts(session.id).associate { it.questionId.value to it.text }
        _state.update { current ->
            val previous = current.lesson?.takeIf { it.sessionId == session.id.value }
            val drafts = if (keepDrafts && previous != null) stored + previous.drafts else stored
            current.copy(
                lesson = LessonPanel(
                    sessionId = session.id.value,
                    bookId = session.bookId.value,
                    status = session.status,
                    chapter = fragment?.chapterTitle?.takeIf { it.isNotBlank() } ?: "Фрагмент",
                    original = fragment?.originalText.orEmpty(),
                    introduction = session.prepared?.introduction.orEmpty(),
                    hints = session.prepared?.hints.orEmpty(),
                    questions = session.prepared?.questions.orEmpty().sortedBy { it.order }.map {
                        QuestionUi(it.id.value, it.type, it.prompt, it.targetExpression)
                    },
                    drafts = drafts,
                    difficulty = session.submission?.difficulty ?: previous?.difficulty,
                    comment = session.submission?.freeComment ?: previous?.comment.orEmpty(),
                    submitted = session.submission != null,
                    reviews = session.review?.results.orEmpty().map {
                        ReviewUi(it.questionId.value, it.verdict, it.explanation, it.errorNotes)
                    },
                    recommendations = session.review?.nextRecommendations.orEmpty(),
                ),
            )
        }
    }

    private fun launch(label: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            gate.withLock {
                if (label != null) _state.update { it.copy(busy = label, error = null) }
                try {
                    block()
                } catch (error: Exception) {
                    _state.update { it.copy(error = error.message ?: "Не получилось.") }
                } finally {
                    if (label != null) _state.update { it.copy(busy = null) }
                }
            }
        }
    }

    companion object {
        fun factory(graph: TutorGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TutorViewModel(graph) as T
        }
    }
}
