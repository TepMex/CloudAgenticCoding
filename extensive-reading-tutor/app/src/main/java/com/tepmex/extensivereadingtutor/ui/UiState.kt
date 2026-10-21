package com.tepmex.extensivereadingtutor.ui

import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SessionStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import java.time.LocalDate

enum class Tab { BOOKS, STUDY, KNOWLEDGE, PROFILE }

enum class RawSide { REQUEST, RESPONSE }

sealed interface Route {
    data object Tabs : Route
    data class Book(val id: String) : Route
    data class Lesson(val bookId: String) : Route
    data object Settings : Route
    data object AiLog : Route
    data class AiExchange(val id: String) : Route
    data class AiRaw(val id: String, val side: RawSide) : Route
}

data class QuestionUi(
    val id: String,
    val type: QuestionType,
    val prompt: String,
    val expression: String?,
)

data class ReviewUi(
    val questionId: String,
    val verdict: AnswerVerdict,
    val explanation: String,
    val notes: String,
    val nuance: String? = null,
    val productionFeedback: String? = null,
)

data class SelectionUi(
    val id: String,
    val revision: Int,
    val quote: String,
    val start: Int,
    val end: Int,
    val type: String,
    val comment: String,
    val explanation: String?,
)

data class SelectionReviewUi(
    val quote: String,
    val explanation: String,
    val nuance: String?,
    val status: String,
)

data class LessonPanel(
    val sessionId: String,
    val bookId: String,
    val status: SessionStatus,
    val chapter: String,
    val original: String,
    val introduction: String,
    val hints: List<String>,
    val questions: List<QuestionUi>,
    val drafts: Map<String, String>,
    val difficulty: SubjectiveDifficulty?,
    val comment: String,
    val submitted: Boolean,
    val reviews: List<ReviewUi>,
    val recommendations: List<String>,
    val selections: List<SelectionUi> = emptyList(),
    val selectionReviews: List<SelectionReviewUi> = emptyList(),
    val wantsMore: Boolean = false,
    val unclear: String = "",
    val feedback: String? = null,
)

data class WordSheetUi(
    val expression: String,
    val sentence: String,
    val gloss: String,
    val example: String?,
    val status: KnowledgeStatus?,
    val skill: SkillType?,
)

data class ExchangeRow(
    val id: String,
    val title: String,
    val model: String,
    val whenLabel: String,
    val meta: String,
    val tokens: String,
    val ok: Boolean,
)

data class ExchangeView(
    val id: String,
    val title: String,
    val model: String,
    val url: String,
    val whenLabel: String,
    val duration: String,
    val status: String,
    val tokens: String,
    val ok: Boolean,
    val requestPreview: String,
    val responsePreview: String,
    val requestHeaders: String,
    val requestBody: String,
    val responseHeaders: String,
    val responseBody: String,
    val requestHeadersHighlighted: Boolean,
    val requestBodyHighlighted: Boolean,
    val responseHeadersHighlighted: Boolean,
    val responseBodyHighlighted: Boolean,
)

data class TutorUiState(
    val tab: Tab = Tab.BOOKS,
    val route: Route = Route.Tabs,
    val shelf: List<ShelfBook> = emptyList(),
    val knowledge: List<LearnerKnowledgeState> = emptyList(),
    val concepts: List<Concept> = emptyList(),
    val observations: List<LearnerObservation> = emptyList(),
    val completedDays: List<LocalDate> = emptyList(),
    val observationDays: List<LocalDate> = emptyList(),
    val preferences: LearnerPreferences? = null,
    val baseUrl: String = "",
    val modelName: String = "",
    val apiKey: String = "",
    val extraBodyJson: String = "",
    val timeoutSeconds: String = "60",
    val connectionDirty: Boolean = false,
    val keyStored: Boolean = false,
    val probe: String? = null,
    val exchanges: List<ExchangeRow> = emptyList(),
    val exchange: ExchangeView? = null,
    val lesson: LessonPanel? = null,
    val checkingAnswers: Boolean = false,
    val selectedWord: String? = null,
    val pendingStart: Int? = null,
    val pendingEnd: Int? = null,
    val selectionType: String = "GENERAL",
    val selectionComment: String = "",
    val word: WordSheetUi? = null,
    val confirmRemove: Boolean = false,
    val busy: String? = null,
    val error: String? = null,
)
