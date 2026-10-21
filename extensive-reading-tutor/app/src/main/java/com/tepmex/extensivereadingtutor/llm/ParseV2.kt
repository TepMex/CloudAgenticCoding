package com.tepmex.extensivereadingtutor.llm

import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.HintPlan
import com.tepmex.extensivereadingtutor.domain.QuestionPurpose
import com.tepmex.extensivereadingtutor.domain.QuestionRubric
import com.tepmex.extensivereadingtutor.pedagogy.EvaluateOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.ExplainOutputV1
import com.tepmex.extensivereadingtutor.pedagogy.OutputLimits
import com.tepmex.extensivereadingtutor.pedagogy.PrepareOutputV2
import kotlinx.serialization.json.Json

internal val strictJson = Json { ignoreUnknownKeys = false; isLenient = false; explicitNulls = true }

fun parsePreparedV2(content: String): AppResult<PreparedDraft> {
    val dto = try {
        strictJson.decodeFromString<PrepareOutputV2>(extractJsonObject(content))
    } catch (error: Exception) {
        return AppResult.Err(DomainError.InvalidModelOutput("Введение v2 не разобрано: ${error.message}"))
    }
    if (dto.introduction.isBlank()) return AppResult.Err(DomainError.InvalidModelOutput("Пустое введение."))
    if (dto.introduction.length > OutputLimits.EXPLANATION_MAX_CHARS) {
        return AppResult.Err(DomainError.InvalidModelOutput("Введение слишком длинное."))
    }
    return AppResult.Ok(
        PreparedDraft(
            introduction = dto.introduction.trim(),
            hints = dto.hints.map { it.text.trim() },
            questions = dto.questions.map { question ->
                QuestionDraft(
                    type = question.type,
                    prompt = question.prompt.trim(),
                    targetSkill = question.targetSkill,
                    targetExpression = question.targetExpression?.trim()?.takeIf { it.isNotEmpty() },
                    sourceQuote = question.sourceQuotes.firstOrNull().orEmpty(),
                    localId = question.localId,
                    purpose = question.purpose,
                    sourceQuotes = question.sourceQuotes,
                    rubric = QuestionRubric(
                        question.rubric.expectedMeaning,
                        question.rubric.requiredPoints,
                        question.rubric.acceptableAlternatives,
                        question.rubric.unsupportedInferences,
                        question.rubric.evidenceQuotes,
                    ),
                    conceptRef = question.conceptRef,
                )
            },
            rawJson = content,
            usage = null,
            providerKind = "",
            model = "",
            hintPlans = dto.hints.map {
                HintPlan(it.localId, it.text.trim(), it.targetQuestionLocalIds, it.assistanceLevel)
            },
        ),
    )
}

fun parseEvaluationV2(content: String, expectedQuestionIds: Set<String>): AppResult<EvaluationDraft> {
    val dto = try {
        strictJson.decodeFromString<EvaluateOutputV2>(extractJsonObject(content))
    } catch (error: Exception) {
        return AppResult.Err(DomainError.InvalidModelOutput("Разбор v2 не разобран: ${error.message}"))
    }
    if (dto.questionReviews.any { it.explanation.isBlank() }) {
        return AppResult.Err(DomainError.InvalidModelOutput("У каждого ответа должно быть объяснение."))
    }
    if (dto.questionReviews.any { it.explanation.length > OutputLimits.EXPLANATION_MAX_CHARS }) {
        return AppResult.Err(DomainError.InvalidModelOutput("Объяснение слишком длинное и не обрезается."))
    }
    val ids = dto.questionReviews.map { it.questionId }
    if (ids.size != ids.toSet().size || ids.toSet() != expectedQuestionIds) {
        return AppResult.Err(DomainError.InvalidModelOutput("Разбор обязан покрыть каждый вопрос ровно один раз."))
    }
    val selectionIds = dto.selectionReviews.map { it.selectionId to it.selectionRevision }
    if (selectionIds.size != selectionIds.toSet().size) {
        return AppResult.Err(DomainError.InvalidModelOutput("Повторяющийся разбор выделения."))
    }
    if (dto.selectionReviews.any { it.explanation.isBlank() }) {
        return AppResult.Err(DomainError.InvalidModelOutput("У каждого выделения должно быть объяснение."))
    }
    if (dto.nextRecommendations.size > 2 || dto.continuityNotes.size > 2) {
        return AppResult.Err(DomainError.InvalidModelOutput("Слишком много рекомендаций или заметок."))
    }
    return AppResult.Ok(
        EvaluationDraft(
            reviews = dto.questionReviews.map {
                ReviewDraft(
                    questionId = it.questionId,
                    verdict = it.verdict,
                    explanation = it.explanation.trim(),
                    errorNotes = it.errorNotes.trim(),
                    evidenceSufficient = it.evidenceSufficient,
                    examples = it.examples,
                    demonstratedMeaning = it.demonstratedMeaning,
                    nuance = it.nuance,
                    productionFeedback = it.productionFeedback,
                    rubricIssue = it.rubricIssue,
                )
            },
            observations = dto.observations.map { item ->
                ObservationDraft(
                    questionId = item.questionId,
                    conceptLabel = item.conceptLabel,
                    skillType = item.skillType,
                    observationType = item.observationType,
                    outcome = item.outcome,
                    studentEvidence = item.studentEvidence,
                    sourceQuote = item.sourceQuote,
                    interpretation = item.interpretation,
                    confidence = item.confidence,
                    payloadJson = strictJson.encodeToString(com.tepmex.extensivereadingtutor.pedagogy.ObservationOutputV2.serializer(), item),
                    conceptRef = item.conceptRef,
                    diagnosticKey = item.diagnosticKey,
                    evidenceSource = item.evidenceSource,
                    evidenceMode = item.evidenceMode,
                    assistanceAssessment = item.assistanceAssessment,
                    selectionId = item.selectionId,
                    selectionRevision = item.selectionRevision,
                )
            },
            rejectedObservations = emptyList(),
            nextRecommendations = dto.nextRecommendations,
            rawJson = content,
            usage = null,
            providerKind = "",
            model = "",
            selectionReviews = dto.selectionReviews.map {
                SelectionReviewDraft(
                    it.selectionId,
                    it.selectionRevision,
                    it.status,
                    it.explanation.trim(),
                    it.contextualMeaning,
                    it.nuance,
                    it.example,
                    it.sourceQuotes,
                )
            },
            feedbackResponse = dto.feedbackResponse,
            continuityNotes = dto.continuityNotes.map {
                com.tepmex.extensivereadingtutor.domain.ContinuityNote(
                    com.tepmex.extensivereadingtutor.domain.SessionId(""),
                    it.text,
                    it.sourceQuote,
                    java.time.Instant.EPOCH,
                )
            },
        ),
    )
}

fun parseExplainV2(content: String, selectionId: String, revision: Int): AppResult<ExplainDraft> {
    val dto = try {
        strictJson.decodeFromString<ExplainOutputV1>(extractJsonObject(content))
    } catch (error: Exception) {
        return AppResult.Err(DomainError.InvalidModelOutput("Объяснение выделения не разобрано: ${error.message}"))
    }
    if (dto.selectionId != selectionId || dto.selectionRevision != revision) {
        return AppResult.Err(DomainError.InvalidModelOutput("Ответ относится к другой версии выделения."))
    }
    if (dto.explanation.isBlank()) return AppResult.Err(DomainError.InvalidModelOutput("Пустое объяснение выделения."))
    if (dto.status == com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus.EXPLAINED && dto.sourceQuotes.isEmpty()) {
        return AppResult.Err(DomainError.InvalidModelOutput("Объяснение без цитаты источника."))
    }
    return AppResult.Ok(
        ExplainDraft(
            selectionId = dto.selectionId,
            selectionRevision = dto.selectionRevision,
            status = dto.status,
            explanation = dto.explanation.trim(),
            contextualMeaning = dto.contextualMeaning,
            nuance = dto.nuance,
            example = dto.example,
            sourceQuotes = dto.sourceQuotes,
            rawJson = content,
            usage = null,
            providerKind = "",
            model = "",
            requestFingerprint = "",
        ),
    )
}

fun draftAssistance(level: AssistanceLevel): AssistanceLevel = level
