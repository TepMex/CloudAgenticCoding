package com.tepmex.extensivereadingtutor.domain

import java.time.Instant

data class LearnerSelection(
    val id: SelectionId,
    val revision: Int,
    val learnerId: LearnerId,
    val bookId: BookId,
    val fragmentId: FragmentId,
    val sessionId: SessionId,
    val sourceAnchors: List<SourceSpan>,
    val quotedText: String,
    val requestType: SelectionRequestType,
    val comment: String?,
    val helpTiming: HelpTiming,
    val createdAt: Instant,
    val requestedAtPhase: RequestedPhase,
    val contextBefore: String,
    val contextAfter: String,
    val deletedAt: Instant? = null,
)

data class FrozenSelection(
    val id: SelectionId,
    val revision: Int,
    val sourceAnchors: List<SourceSpan>,
    val quotedText: String,
    val requestType: SelectionRequestType,
    val comment: String?,
    val contextBefore: String,
    val contextAfter: String,
    val helpTiming: HelpTiming,
)

data class FrozenExplanation(
    val selectionId: SelectionId,
    val selectionRevision: Int,
    val explanation: String,
    val contextualMeaning: String?,
    val nuance: String?,
    val example: String?,
    val sourceQuotes: List<String>,
    val revealedAt: Instant,
)

data class FrozenAssistance(
    val id: String,
    val level: AssistanceLevel,
    val scopeQuestionIds: List<QuestionId>,
    val scopeAll: Boolean,
    val scopeUnknown: Boolean,
    val selectionId: SelectionId?,
    val text: String,
    val shownAt: Instant,
)

data class AttemptFreeze(
    val sessionId: SessionId,
    val frozenAt: Instant,
    val selections: List<FrozenSelection>,
    val explanations: List<FrozenExplanation>,
    val assistance: List<FrozenAssistance>,
    val evaluateInputJson: String,
)

data class AssistanceEvent(
    val id: String,
    val sessionId: SessionId,
    val level: AssistanceLevel,
    val scopeQuestionIds: List<QuestionId>,
    val scopeAll: Boolean,
    val scopeUnknown: Boolean,
    val selectionId: SelectionId?,
    val selectionRevision: Int?,
    val text: String,
    val shownAt: Instant,
    val source: String,
    val countsTowardAttempt: Boolean,
)

data class ExplainRecord(
    val id: String,
    val sessionId: SessionId,
    val selectionId: SelectionId,
    val selectionRevision: Int,
    val idempotencyKey: String,
    val status: CallStatus,
    val responseJson: String?,
    val explanation: String?,
    val contextualMeaning: String?,
    val nuance: String?,
    val example: String?,
    val sourceQuotes: List<String>,
    val reviewStatus: SelectionReviewStatus?,
    val revealedAt: Instant?,
    val createdAt: Instant,
    val providerKind: String,
    val model: String,
    val promptVersion: String,
    val error: String?,
    val countsTowardAttempt: Boolean,
)

data class ContinuityNote(
    val sessionId: SessionId,
    val text: String,
    val sourceQuote: String,
    val createdAt: Instant,
)

data class BudgetReport(
    val memoryBytes: Int,
    val memoryBudgetBytes: Int,
    val estimatedInputTokens: Int,
    val estimatedOutputTokens: Int,
    val estimateUncertain: Boolean,
    val selectedIds: List<String>,
    val omittedIds: List<String>,
    val cutoffReason: String?,
    val builderVersion: String,
    val contextWindowTokens: Int,
)

data class ReadingProfile(
    val sampleSize: Int,
    val independentUnderstandings: Int,
    val assistedUnderstandings: Int,
    val difficulties: Int,
    val from: Instant?,
    val to: Instant?,
    val comparable: Boolean,
    val summary: String,
    val limitation: String,
)
