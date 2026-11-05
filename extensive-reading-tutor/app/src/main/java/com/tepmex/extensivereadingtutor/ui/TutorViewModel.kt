package com.tepmex.extensivereadingtutor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.extensivereadingtutor.TutorGraph
import com.tepmex.extensivereadingtutor.books.pageIndexFor
import com.tepmex.extensivereadingtutor.books.placeKey
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.lessons.ReaderSpread
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.HintDepth
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.LessonSession
import com.tepmex.extensivereadingtutor.books.selectionBounds
import com.tepmex.extensivereadingtutor.domain.HelpTiming
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.SelectionId
import com.tepmex.extensivereadingtutor.domain.SelectionRequestType
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

class TutorViewModel(private val graph: TutorGraph) : ViewModel(), LessonActions {
    private val _state = MutableStateFlow(TutorUiState())
    val state: StateFlow<TutorUiState> = _state
    private val gate = Mutex()
    private val draftJobs = mutableMapOf<String, Job>()

    init {
        launch(null) { reload() }
    }

    fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab, route = Route.Tabs, word = null, confirmRemoveId = null) }
    }

    fun openBook(id: String) = launch(null) { openReader(id) }

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
                _state.update { it.copy(tab = Tab.BOOKS) }
                openReader(result.value.id.value)
            }
        }
    }

    fun openLesson(bookId: String) = openBook(bookId)

    fun settlePage(index: Int) {
        val reader = _state.value.reader ?: return
        if (index == reader.index || index !in reader.pages.indices) return
        val leaving = reader.pages[reader.index]
        val place = reader.pages[index].place
        _state.update { state ->
            val current = state.reader ?: return@update state
            state.copy(
                reader = current.copy(index = index),
                lesson = current.pages[index].lesson,
            )
        }
        viewModelScope.launch {
            graph.saveReaderPlace(reader.bookId, place)
            if (leaving.lesson?.status == SessionStatus.REVIEW_READY) {
                gate.withLock {
                    graph.lessons.completeLesson(SessionId(leaving.lesson.sessionId))
                    refreshReader(reader.bookId, index)
                }
            }
        }
    }

    fun togglePinyin() {
        val reader = _state.value.reader ?: return
        val show = !reader.showPinyin
        _state.update { it.copy(reader = it.reader?.copy(showPinyin = show)) }
        viewModelScope.launch { graph.saveShowPinyin(show) }
    }

    fun requestIteration() = launch(null) {
        val reader = _state.value.reader ?: return@launch
        val page = reader.pages.getOrNull(reader.index) ?: return@launch
        if (reader.analyzing || !page.lesson.acceptsIteration()) return@launch
        _state.update { it.copy(reader = it.reader?.copy(analyzing = true), error = null) }
        try {
            advancePage(reader.bookId, page)
        } finally {
            _state.update { it.copy(reader = it.reader?.copy(analyzing = false)) }
        }
    }

    override fun prepare() = lessonAction("Готовлю занятие") { graph.lessons.prepareLesson(SessionId(it)) }

    override fun reveal() = lessonAction("Открываю вопросы") { graph.lessons.revealQuestions(SessionId(it)) }

    override fun editDraft(questionId: String, text: String) {
        updateLesson { it.copy(drafts = it.drafts + (questionId to text)) }
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

    override fun setDifficulty(difficulty: SubjectiveDifficulty) {
        updateLesson { it.copy(difficulty = difficulty) }
    }

    override fun setComment(comment: String) {
        updateLesson { it.copy(comment = comment) }
    }

    override fun setUnclear(text: String) {
        updateLesson { it.copy(unclear = text) }
    }

    override fun setWantsMore(value: Boolean) {
        updateLesson { it.copy(wantsMore = value) }
    }

    override fun selectRange(startUtf16: Int, endUtf16: Int) {
        _state.update { it.copy(pendingStart = startUtf16, pendingEnd = endUtf16, error = null) }
    }

    override fun setSelectionType(type: String) {
        _state.update { it.copy(selectionType = type) }
    }

    override fun setSelectionComment(comment: String) {
        _state.update { it.copy(selectionComment = comment) }
    }

    fun clearPendingSelection() {
        _state.update { it.copy(pendingStart = null, pendingEnd = null, selectionComment = "") }
    }

    override fun saveSelection(now: Boolean) {
        val lesson = _state.value.lesson ?: return
        val start = _state.value.pendingStart ?: return
        val end = _state.value.pendingEnd ?: return
        val type = runCatching { SelectionRequestType.valueOf(_state.value.selectionType) }.getOrDefault(SelectionRequestType.GENERAL)
        launch(if (now) "Объясняю выделение" else null) {
            when (
                val marked = graph.lessons.markUtf16(
                    SessionId(lesson.sessionId),
                    start,
                    end,
                    type,
                    _state.value.selectionComment.ifBlank { null },
                    if (now) HelpTiming.NOW else HelpTiming.WITH_REVIEW,
                )
            ) {
                is AppResult.Err -> _state.update { it.copy(error = marked.error.explain()) }
                is AppResult.Ok -> {
                    clearPendingSelection()
                    if (now) {
                        when (val explained = graph.lessons.explainSelection(SessionId(lesson.sessionId), marked.value.id)) {
                            is AppResult.Err -> _state.update { it.copy(error = explained.error.explain()) }
                            is AppResult.Ok -> {
                                if (explained.value.selectionRevision == marked.value.revision && explained.value.explanation != null) {
                                    graph.lessons.revealExplanation(SessionId(lesson.sessionId), marked.value.id, marked.value.revision)
                                }
                            }
                        }
                    }
                    graph.read { it.session(SessionId(lesson.sessionId)) }?.let { showLesson(it, keepDrafts = true) }
                }
            }
        }
    }

    override fun removeSelection(id: String) {
        val lesson = _state.value.lesson ?: return
        launch(null) {
            when (val result = graph.lessons.deleteSelection(SessionId(lesson.sessionId), SelectionId(id))) {
                is AppResult.Err -> _state.update { it.copy(error = result.error.explain()) }
                is AppResult.Ok -> showLesson(result.value, keepDrafts = true)
            }
        }
    }

    override fun explainSaved(id: String) {
        val lesson = _state.value.lesson ?: return
        launch("Объясняю выделение") {
            when (val explained = graph.lessons.explainSelection(SessionId(lesson.sessionId), SelectionId(id))) {
                is AppResult.Err -> _state.update { it.copy(error = explained.error.explain()) }
                is AppResult.Ok -> {
                    val current = graph.lessons.selections(SessionId(lesson.sessionId)).firstOrNull { it.id.value == id }
                    if (current != null && current.revision == explained.value.selectionRevision && explained.value.explanation != null) {
                        graph.lessons.revealExplanation(SessionId(lesson.sessionId), current.id, current.revision)
                    }
                    graph.read { it.session(SessionId(lesson.sessionId)) }?.let { showLesson(it, keepDrafts = true) }
                }
            }
        }
    }

    override fun submit() {
        if (_state.value.reader != null) {
            requestIteration()
            return
        }
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
                wantsMoreExplanation = lesson.wantsMore,
                unclearNotes = lesson.unclear.ifBlank { null },
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

    override fun evaluateAgain() = lessonAction("Повторяю разбор") { graph.lessons.evaluateAnswers(SessionId(it)) }

    override fun complete() = requestIteration()

    override fun leaveLesson() {
        _state.update { it.copy(route = Route.Tabs, word = null) }
    }

    override fun openExpression(expression: String, sentence: String) {
        val current = _state.value
        val card = wordCard(expression, sentence, current.concepts, current.knowledge, current.observations)
        _state.update {
            it.copy(
                selectedWord = expression,
                word = WordSheetUi(card.expression, card.sentence, card.gloss, card.example, card.status, card.skill),
            )
        }
    }

    fun askRemove(id: String) = _state.update { it.copy(confirmRemoveId = id) }

    fun cancelRemove() = _state.update { it.copy(confirmRemoveId = null) }

    fun removeBook(id: String) = launch("Убираю с полки") {
        when (val result = graph.books.removeFromLibrary(BookId(id))) {
            is AppResult.Err -> _state.update { it.copy(error = result.error.explain(), confirmRemoveId = null) }
            is AppResult.Ok -> {
                reload()
                _state.update { it.copy(route = Route.Tabs, tab = Tab.BOOKS, confirmRemoveId = null, reader = null) }
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
        val previous = if (keepDrafts) _state.value.lesson?.takeIf { it.sessionId == session.id.value } else null
        val panel = panelFor(session, previous)
        _state.update { current ->
            current.copy(lesson = panel, reader = current.reader?.withLesson(panel))
        }
    }

    private suspend fun panelFor(session: LessonSession, previous: LessonPanel?): LessonPanel {
        val fragment = graph.lessons.fragment(session.fragmentId)
        val stored = graph.lessons.drafts(session.id).associate { it.questionId.value to it.text }
        val explains = graph.lessons.explainRecords(session.id).filter { it.revealedAt != null }
        val marks = graph.lessons.selections(session.id).filter { it.deletedAt == null }
        val prior = previous?.takeIf { it.sessionId == session.id.value }
        val drafts = if (prior != null) stored + prior.drafts else stored
        val selectionUi = marks.map { mark ->
            val bounds = fragment?.let { selectionBounds(it, mark.sourceAnchors) }
            val shown = explains.firstOrNull { it.selectionId == mark.id && it.selectionRevision == mark.revision }
            SelectionUi(
                id = mark.id.value,
                revision = mark.revision,
                quote = mark.quotedText,
                start = bounds?.first ?: 0,
                end = bounds?.second ?: 0,
                type = mark.requestType.name,
                comment = mark.comment.orEmpty(),
                explanation = shown?.explanation,
            )
        }
        return LessonPanel(
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
            difficulty = session.submission?.difficulty ?: prior?.difficulty,
            comment = session.submission?.freeComment ?: prior?.comment.orEmpty(),
            submitted = session.submission != null,
            reviews = session.review?.results.orEmpty().map {
                ReviewUi(it.questionId.value, it.verdict, it.explanation, it.errorNotes, it.nuance, it.productionFeedback)
            },
            recommendations = session.review?.nextRecommendations.orEmpty(),
            selections = selectionUi,
            selectionReviews = session.review?.selectionReviews.orEmpty().map { review ->
                val quote = marks.firstOrNull { it.id == review.selectionId }?.quotedText
                    ?: review.sourceQuotes.firstOrNull()
                    ?: "выделение"
                SelectionReviewUi(quote, review.explanation, review.nuance, review.status.name)
            },
            wantsMore = session.submission?.wantsMoreExplanation ?: prior?.wantsMore ?: false,
            unclear = session.submission?.unclearNotes ?: prior?.unclear.orEmpty(),
            feedback = session.review?.feedbackResponse,
        )
    }

    private fun ReaderUi.withLesson(updated: LessonPanel) = copy(
        pages = pages.map { if (it.lesson?.sessionId == updated.sessionId) it.copy(lesson = updated) else it },
    )

    private fun updateLesson(transform: (LessonPanel) -> LessonPanel) {
        _state.update { state ->
            val lesson = state.lesson ?: state.reader?.pages?.getOrNull(state.reader.index)?.lesson ?: return@update state
            val updated = transform(lesson)
            state.copy(lesson = updated, reader = state.reader?.withLesson(updated))
        }
    }

    private suspend fun openReader(bookId: String) {
        when (val spread = graph.lessons.readerSpread(BookId(bookId))) {
            is AppResult.Err -> _state.update { it.copy(error = spread.error.explain()) }
            is AppResult.Ok -> {
                val index = pageIndexFor(spread.value.pages, graph.readerPlace(bookId), spread.value.cursor)
                val reader = assembleReader(bookId, spread.value, index, graph.showPinyin(), null)
                reader.pages.getOrNull(reader.index)?.let { graph.saveReaderPlace(bookId, it.place) }
                _state.update {
                    it.copy(
                        route = Route.Reader(bookId),
                        reader = reader,
                        lesson = reader.pages.getOrNull(reader.index)?.lesson,
                        word = null,
                        error = null,
                        confirmRemoveId = null,
                    )
                }
            }
        }
    }

    private suspend fun refreshReader(bookId: String, index: Int) {
        val spread = graph.lessons.readerSpread(BookId(bookId))
        if (spread is AppResult.Ok) {
            val show = _state.value.reader?.showPinyin ?: graph.showPinyin()
            val reader = assembleReader(bookId, spread.value, index, show, _state.value.reader)
            _state.update {
                it.copy(reader = reader, lesson = reader.pages.getOrNull(reader.index)?.lesson)
            }
        }
        reload()
    }

    private suspend fun assembleReader(
        bookId: String,
        spread: ReaderSpread,
        index: Int,
        showPinyin: Boolean,
        previous: ReaderUi?,
    ): ReaderUi {
        val pages = spread.pages.map { page ->
            val session = page.fragmentId?.let { id -> spread.sessions.firstOrNull { it.fragmentId == id } }
            val prior = previous?.pages?.firstOrNull { it.place == page.placeKey() }?.lesson
            ReaderPageUi(
                place = page.placeKey(),
                text = page.text,
                chapter = page.chapter.orEmpty(),
                lesson = session?.let { panelFor(it, prior) },
            )
        }
        val safe = if (pages.isEmpty()) 0 else index.coerceIn(0, pages.lastIndex)
        return ReaderUi(bookId, spread.title, pages, safe, showPinyin, analyzing = false)
    }

    private suspend fun advancePage(bookId: String, page: ReaderPageUi) {
        val opened = page.lesson?.sessionId?.let { id -> graph.read { it.session(SessionId(id)) } }
            ?: when (val created = graph.lessons.openPageLesson(BookId(bookId), page.place)) {
                is AppResult.Err -> {
                    _state.update { it.copy(error = created.error.explain()) }
                    null
                }
                is AppResult.Ok -> created.value
            }
        var session = opened ?: return
        if (session.prepared == null && session.status != SessionStatus.COMPLETED) {
            session = when (val prepared = graph.lessons.prepareLesson(session.id)) {
                is AppResult.Err -> {
                    _state.update { it.copy(error = prepared.error.explain()) }
                    refreshReader(bookId, _state.value.reader?.index ?: 0)
                    return
                }
                is AppResult.Ok -> prepared.value
            }
        }
        if (session.status == SessionStatus.INTRO_READY) {
            session = when (val revealed = graph.lessons.revealQuestions(session.id)) {
                is AppResult.Err -> {
                    _state.update { it.copy(error = revealed.error.explain()) }
                    refreshReader(bookId, _state.value.reader?.index ?: 0)
                    return
                }
                is AppResult.Ok -> revealed.value
            }
        }
        val showingQuestions = page.lesson?.status == SessionStatus.QUESTIONS_READY ||
            page.lesson?.status == SessionStatus.AWAITING_ANSWERS
        val needsReview = session.submission == null && showingQuestions
        if (needsReview || (session.submission != null && session.review == null)) {
            if (session.submission == null) {
                draftJobs.values.forEach { it.cancel() }
                draftJobs.clear()
                val lesson = _state.value.reader?.pages?.firstOrNull { it.place == page.place }?.lesson ?: page.lesson
                if (lesson == null) {
                    refreshReader(bookId, _state.value.reader?.index ?: 0)
                    return
                }
                val submission = StudentSubmission(
                    sessionId = session.id,
                    answers = lesson.questions.map { SubmittedAnswer(QuestionId(it.id), lesson.drafts[it.id].orEmpty()) },
                    difficulty = lesson.difficulty,
                    wantsMoreExplanation = lesson.wantsMore,
                    unclearNotes = lesson.unclear.ifBlank { null },
                    freeComment = lesson.comment.ifBlank { null },
                    hintRequestCount = 0,
                    submittedAt = Instant.now(),
                )
                when (val submitted = graph.lessons.submitAnswers(session.id, submission)) {
                    is AppResult.Err -> {
                        _state.update { it.copy(error = submitted.error.explain()) }
                        refreshReader(bookId, _state.value.reader?.index ?: 0)
                        return
                    }
                    is AppResult.Ok -> Unit
                }
            }
            when (val reviewed = graph.lessons.evaluateAnswers(session.id)) {
                is AppResult.Err -> _state.update { it.copy(error = reviewed.error.explain()) }
                is AppResult.Ok -> Unit
            }
        }
        refreshReader(bookId, _state.value.reader?.index ?: 0)
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
