package com.tepmex.extensivereadingtutor.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.extensivereadingtutor.TutorApplication
import com.tepmex.extensivereadingtutor.TutorGraph
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import com.tepmex.extensivereadingtutor.domain.SubmittedAnswer
import com.tepmex.extensivereadingtutor.domain.explain
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as TutorApplication).graph
        setContent {
            MaterialTheme {
                val model: TutorViewModel = viewModel(factory = TutorViewModel.factory(graph))
                TutorHarness(model)
            }
        }
    }
}

data class HarnessState(
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val bookTitle: String = "",
    val bookId: String? = null,
    val status: String = "Книга не выбрана",
    val fragment: String = "",
    val introduction: String = "",
    val questions: List<Pair<String, String>> = emptyList(),
    val drafts: Map<String, String> = emptyMap(),
    val review: String = "",
    val error: String? = null,
    val busy: Boolean = false,
)

class TutorViewModel(private val graph: TutorGraph) : ViewModel() {
    private val _state = MutableStateFlow(HarnessState())
    val state: StateFlow<HarnessState> = _state
    private var sessionId: String? = null

    init {
        viewModelScope.launch {
            graph.lessons.recoverInterruptedSessions()
            val settings = graph.loadSettings()
            graph.models.snapshot = graph.models.snapshot.copy(model = settings.model)
            _state.update { it.copy(baseUrl = settings.baseUrl, model = settings.model) }
        }
    }

    fun updateSettings(baseUrl: String, model: String, apiKey: String) {
        _state.update { it.copy(baseUrl = baseUrl, model = model, apiKey = apiKey) }
    }

    fun updateDraft(questionId: String, text: String) {
        _state.update { it.copy(drafts = it.drafts + (questionId to text)) }
    }

    fun saveSettings() = launch {
        val current = _state.value
        graph.saveSettings(current.baseUrl, current.model, current.apiKey.ifBlank { null })
        _state.update { it.copy(status = "Настройки сохранены. ${_state.value.status}") }
    }

    fun import(bytes: ByteArray, uri: String) = launch {
        when (val result = graph.books.import(bytes, uri, "zh")) {
            is com.tepmex.extensivereadingtutor.domain.AppResult.Err ->
                _state.update { it.copy(error = result.error.explain()) }
            is com.tepmex.extensivereadingtutor.domain.AppResult.Ok -> {
                _state.update { it.copy(bookId = result.value.id.value, bookTitle = result.value.title, error = null) }
                resume(result.value.id)
            }
        }
    }

    fun prepare() = act { graph.lessons.prepareLesson(SessionId(sessionId!!)) }
    fun reveal() = act { graph.lessons.revealQuestions(SessionId(sessionId!!)) }

    fun saveDrafts() = launch {
        val id = sessionId ?: return@launch
        for ((question, text) in _state.value.drafts) {
            val saved = graph.lessons.saveAnswerDraft(SessionId(id), QuestionId(question), text)
            if (saved is com.tepmex.extensivereadingtutor.domain.AppResult.Err) {
                _state.update { it.copy(error = saved.error.explain()) }
                refresh()
                return@launch
            }
        }
        refresh()
    }

    fun submit(difficulty: SubjectiveDifficulty) = launch {
        val id = sessionId ?: return@launch
        val questions = _state.value.questions
        val submission = StudentSubmission(
            sessionId = SessionId(id),
            answers = questions.map { (questionId, _) ->
                SubmittedAnswer(QuestionId(questionId), _state.value.drafts[questionId].orEmpty())
            },
            difficulty = difficulty,
            wantsMoreExplanation = false,
            unclearNotes = null,
            freeComment = null,
            hintRequestCount = 0,
            submittedAt = Instant.now(),
        )
        when (val result = graph.lessons.submitAnswers(SessionId(id), submission)) {
            is com.tepmex.extensivereadingtutor.domain.AppResult.Err ->
                _state.update { it.copy(error = result.error.explain()) }
            is com.tepmex.extensivereadingtutor.domain.AppResult.Ok -> refresh()
        }
    }

    fun evaluate() = act { graph.lessons.evaluateAnswers(SessionId(sessionId!!)) }
    fun complete() = act { graph.lessons.completeLesson(SessionId(sessionId!!)) }

    fun nextFragment() {
        val book = _state.value.bookId ?: return
        resume(BookId(book))
    }

    private fun resume(bookId: BookId) = launch {
        when (val result = graph.lessons.startOrResumeLesson(bookId)) {
            is com.tepmex.extensivereadingtutor.domain.AppResult.Err ->
                _state.update { it.copy(error = result.error.explain(), status = result.error.explain()) }
            is com.tepmex.extensivereadingtutor.domain.AppResult.Ok -> {
                sessionId = result.value.id.value
                refresh()
            }
        }
    }

    private fun act(block: suspend () -> com.tepmex.extensivereadingtutor.domain.AppResult<*>) = launch {
        if (sessionId == null) return@launch
        when (val result = block()) {
            is com.tepmex.extensivereadingtutor.domain.AppResult.Err ->
                _state.update { it.copy(error = result.error.explain()) }
            is com.tepmex.extensivereadingtutor.domain.AppResult.Ok -> refresh()
        }
    }

    private suspend fun refresh() {
        val bookId = _state.value.bookId ?: return
        val shown = graph.lessons.getActiveLesson(BookId(bookId))
        if (shown == null) {
            val progress = graph.lessons.getReadingProgress(BookId(bookId))
            sessionId = null
            _state.update {
                it.copy(
                    status = "Нет активного занятия. Курсор ${progress?.cursor?.blockOrder}:${progress?.cursor?.codePoint}",
                    fragment = "",
                    introduction = "",
                    questions = emptyList(),
                )
            }
            return
        }
        sessionId = shown.id.value
        val fragment = graph.lessons.fragment(shown.fragmentId)
        val drafts = graph.lessons.drafts(shown.id).associate { it.questionId.value to it.text }
        val review = shown.review?.results?.joinToString("\n\n") { "${it.verdict}: ${it.explanation}" }.orEmpty()
        _state.update {
            it.copy(
                status = shown.status.name,
                fragment = fragment?.originalText.orEmpty(),
                introduction = shown.prepared?.introduction.orEmpty(),
                questions = shown.prepared?.questions?.map { question -> question.id.value to question.prompt }.orEmpty(),
                drafts = drafts.ifEmpty { it.drafts },
                review = review,
                error = null,
            )
        }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
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

@Composable
private fun TutorHarness(model: TutorViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var difficulty by remember { mutableStateOf(SubjectiveDifficulty.SUITABLE) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
        model.import(bytes, uri.toString())
    }
    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Extensive Reading Tutor", style = MaterialTheme.typography.titleLarge)
            Text("Отладочный контур занятия. Экран можно заменить целиком.")
            OutlinedTextField(state.baseUrl, { model.updateSettings(it, state.model, state.apiKey) }, Modifier.fillMaxWidth(), label = { Text("Base URL") })
            OutlinedTextField(state.model, { model.updateSettings(state.baseUrl, it, state.apiKey) }, Modifier.fillMaxWidth(), label = { Text("Модель") })
            OutlinedTextField(state.apiKey, { model.updateSettings(state.baseUrl, state.model, it) }, Modifier.fillMaxWidth(), label = { Text("API-ключ") })
            Button({ model.saveSettings() }, enabled = !state.busy) { Text("Сохранить настройки") }
            Button({ open.launch(arrayOf("application/epub+zip", "application/octet-stream")) }, enabled = !state.busy) { Text("Импорт EPUB") }
            Text(state.bookTitle.ifBlank { "Книга не выбрана" })
            Text("Статус: ${state.status}")
            state.error?.let { Text(it) }
            if (state.fragment.isNotEmpty()) {
                Text("Фрагмент")
                Text(state.fragment)
            }
            if (state.introduction.isNotEmpty()) {
                Text("Введение")
                Text(state.introduction)
            }
            state.questions.forEach { (id, prompt) ->
                Text(prompt)
                OutlinedTextField(
                    value = state.drafts[id].orEmpty(),
                    onValueChange = { model.updateDraft(id, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ответ") },
                )
            }
            if (state.review.isNotEmpty()) {
                Text("Разбор")
                Text(state.review)
            }
            Button({ model.prepare() }, enabled = !state.busy) { Text("Подготовить") }
            Button({ model.reveal() }, enabled = !state.busy) { Text("Показать вопросы") }
            Button({ model.saveDrafts() }, enabled = !state.busy) { Text("Сохранить черновик") }
            Button({ difficulty = SubjectiveDifficulty.TOO_EASY }, enabled = !state.busy) { Text("Слишком легко") }
            Button({ difficulty = SubjectiveDifficulty.SUITABLE }, enabled = !state.busy) { Text("Нормально") }
            Button({ difficulty = SubjectiveDifficulty.TOO_HARD }, enabled = !state.busy) { Text("Сложно") }
            Text("Оценка фрагмента: ${difficulty.name}")
            Button({ model.submit(difficulty) }, enabled = !state.busy) { Text("Отправить ответы") }
            Button({ model.evaluate() }, enabled = !state.busy) { Text("Проверить") }
            Button({ model.complete() }, enabled = !state.busy) { Text("Завершить занятие") }
            Button({ model.nextFragment() }, enabled = !state.busy) { Text("Следующий фрагмент") }
        }
    }
}
