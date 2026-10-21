package com.tepmex.extensivereadingtutor.storage

import com.tepmex.extensivereadingtutor.domain.AiCallId
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.AssistanceEvent
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.AttemptFreeze
import com.tepmex.extensivereadingtutor.domain.BlockId
import com.tepmex.extensivereadingtutor.domain.BookId
import com.tepmex.extensivereadingtutor.domain.CallStatus
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.ConceptKind
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.ContinuityNote
import com.tepmex.extensivereadingtutor.domain.EvidenceMode
import com.tepmex.extensivereadingtutor.domain.EvidenceSource
import com.tepmex.extensivereadingtutor.domain.EvidenceStrength
import com.tepmex.extensivereadingtutor.domain.ExplainRecord
import com.tepmex.extensivereadingtutor.domain.FragmentId
import com.tepmex.extensivereadingtutor.domain.FrozenAssistance
import com.tepmex.extensivereadingtutor.domain.FrozenExplanation
import com.tepmex.extensivereadingtutor.domain.FrozenSelection
import com.tepmex.extensivereadingtutor.domain.HelpTiming
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerId
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LearnerSelection
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.LessonReview
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.ProposalId
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.QuestionPurpose
import com.tepmex.extensivereadingtutor.domain.QuestionReview
import com.tepmex.extensivereadingtutor.domain.QuestionRubric
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.RequestedPhase
import com.tepmex.extensivereadingtutor.domain.SelectionId
import com.tepmex.extensivereadingtutor.domain.SelectionRequestType
import com.tepmex.extensivereadingtutor.domain.SelectionReview
import com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus
import com.tepmex.extensivereadingtutor.domain.SenseScope
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.SourceSpan
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = true }

object PedagogyCodec {
    fun selections(values: List<LearnerSelection>): String = json.encodeToString(values.map { it.toWire() })
    fun selections(raw: String): List<LearnerSelection> = json.decodeFromString<List<SelectionDoc>>(raw).map { it.toDomain() }

    fun freeze(value: AttemptFreeze): String = json.encodeToString(value.toWire())
    fun freeze(raw: String): AttemptFreeze = json.decodeFromString<FreezeDoc>(raw).toDomain()

    fun assistance(values: List<AssistanceEvent>): String = json.encodeToString(values.map { it.toWire() })
    fun assistance(raw: String): List<AssistanceEvent> = json.decodeFromString<List<AssistDoc>>(raw).map { it.toDomain() }

    fun explains(values: List<ExplainRecord>): String = json.encodeToString(values.map { it.toWire() })
    fun explains(raw: String): List<ExplainRecord> = json.decodeFromString<List<ExplainDoc>>(raw).map { it.toDomain() }

    fun continuity(values: List<ContinuityNote>): String = json.encodeToString(values.map { it.toWire() })
    fun continuity(raw: String): List<ContinuityNote> = json.decodeFromString<List<NoteDoc>>(raw).map { it.toDomain() }

    fun questions(values: List<LessonQuestion>): String = json.encodeToString(values.map { it.toWire() })
    fun questions(raw: String): List<LessonQuestion> = json.decodeFromString<List<QuestionDoc>>(raw).map { it.toDomain() }

    fun review(value: LessonReview): String = json.encodeToString(value.toWire())
    fun review(raw: String): LessonReview = json.decodeFromString<ReviewDoc>(raw).toDomain()

    fun observation(value: LearnerObservation): String = json.encodeToString(value.toWire())
    fun observation(raw: String): LearnerObservation = json.decodeFromString<ObservationDoc>(raw).toDomain()

    fun knowledge(values: List<LearnerKnowledgeState>): String = json.encodeToString(values.map { it.toWire() })
    fun knowledge(raw: String): List<LearnerKnowledgeState> = json.decodeFromString<List<KnowledgeDoc>>(raw).map { it.toDomain() }
}

@Serializable
private data class SpanDoc(val quote: String, val blockId: String? = null, val start: Int? = null, val end: Int? = null)

@Serializable
private data class RubricDoc(
    val expectedMeaning: String,
    val requiredPoints: List<String>,
    val acceptableAlternatives: List<String>,
    val unsupportedInferences: List<String>,
    val evidenceQuotes: List<String>,
)

@Serializable
private data class SelectionDoc(
    val id: String,
    val revision: Int,
    val learnerId: String,
    val bookId: String,
    val fragmentId: String,
    val sessionId: String,
    val anchors: List<SpanDoc>,
    val quotedText: String,
    val requestType: String,
    val comment: String? = null,
    val helpTiming: String,
    val createdAt: Long,
    val phase: String,
    val contextBefore: String,
    val contextAfter: String,
    val deletedAt: Long? = null,
)

@Serializable
private data class FrozenSelDoc(
    val id: String,
    val revision: Int,
    val anchors: List<SpanDoc>,
    val quotedText: String,
    val requestType: String,
    val comment: String? = null,
    val contextBefore: String,
    val contextAfter: String,
    val helpTiming: String,
)

@Serializable
private data class FrozenExplDoc(
    val selectionId: String,
    val revision: Int,
    val explanation: String,
    val contextualMeaning: String? = null,
    val nuance: String? = null,
    val example: String? = null,
    val sourceQuotes: List<String>,
    val revealedAt: Long,
)

@Serializable
private data class FrozenAssistDoc(
    val id: String,
    val level: String,
    val scopeQuestionIds: List<String>,
    val scopeAll: Boolean,
    val scopeUnknown: Boolean,
    val selectionId: String? = null,
    val text: String,
    val shownAt: Long,
)

@Serializable
private data class FreezeDoc(
    val sessionId: String,
    val frozenAt: Long,
    val selections: List<FrozenSelDoc>,
    val explanations: List<FrozenExplDoc>,
    val assistance: List<FrozenAssistDoc>,
    val evaluateInputJson: String,
)

@Serializable
private data class AssistDoc(
    val id: String,
    val sessionId: String,
    val level: String,
    val scopeQuestionIds: List<String>,
    val scopeAll: Boolean,
    val scopeUnknown: Boolean,
    val selectionId: String? = null,
    val selectionRevision: Int? = null,
    val text: String,
    val shownAt: Long,
    val source: String,
    val countsTowardAttempt: Boolean,
)

@Serializable
private data class ExplainDoc(
    val id: String,
    val sessionId: String,
    val selectionId: String,
    val selectionRevision: Int,
    val idempotencyKey: String,
    val status: String,
    val responseJson: String? = null,
    val explanation: String? = null,
    val contextualMeaning: String? = null,
    val nuance: String? = null,
    val example: String? = null,
    val sourceQuotes: List<String> = emptyList(),
    val reviewStatus: String? = null,
    val revealedAt: Long? = null,
    val createdAt: Long,
    val providerKind: String,
    val model: String,
    val promptVersion: String,
    val error: String? = null,
    val countsTowardAttempt: Boolean,
)

@Serializable
private data class NoteDoc(val sessionId: String, val text: String, val sourceQuote: String, val createdAt: Long)

@Serializable
private data class QuestionDoc(
    val id: String,
    val sessionId: String,
    val type: String,
    val prompt: String,
    val targetSkill: String,
    val targetExpression: String? = null,
    val anchors: List<SpanDoc>,
    val order: Int,
    val purpose: String,
    val conceptRef: String? = null,
    val rubric: RubricDoc? = null,
    val localId: String? = null,
)

@Serializable
private data class QuestionReviewDoc(
    val questionId: String,
    val verdict: String,
    val explanation: String,
    val errorNotes: String,
    val evidenceSufficient: Boolean,
    val examples: List<String>,
    val demonstratedMeaning: String = "",
    val nuance: String? = null,
    val productionFeedback: String? = null,
    val rubricIssue: String? = null,
)

@Serializable
private data class SelectionReviewDoc(
    val selectionId: String,
    val selectionRevision: Int,
    val status: String,
    val explanation: String,
    val contextualMeaning: String? = null,
    val nuance: String? = null,
    val example: String? = null,
    val sourceQuotes: List<String> = emptyList(),
)

@Serializable
private data class ReviewDoc(
    val sessionId: String,
    val results: List<QuestionReviewDoc>,
    val proposalIds: List<String>,
    val recommendations: List<String>,
    val aiCallId: String,
    val createdAt: Long,
    val selectionReviews: List<SelectionReviewDoc> = emptyList(),
    val feedbackResponse: String? = null,
)

@Serializable
private data class ObservationDoc(
    val id: String,
    val learnerId: String,
    val language: String,
    val sessionId: String,
    val questionId: String? = null,
    val conceptId: String? = null,
    val skillType: String,
    val observationType: String,
    val outcome: String,
    val studentEvidence: String,
    val sourceEvidence: String,
    val anchors: List<SpanDoc>,
    val interpretation: String,
    val confidence: String,
    val evaluatorProvider: String,
    val evaluatorModel: String,
    val evaluationVersion: String,
    val createdAt: Long,
    val sequence: Long,
    val bookId: String? = null,
    val diagnosticKey: String? = null,
    val evidenceSource: String,
    val evidenceMode: String,
    val assistance: String,
    val eligibleForIndependentSuccess: Boolean,
    val schemaVersion: String = "",
    val policyVersion: String = "",
    val evidenceUnitId: String? = null,
    val contextId: String? = null,
    val selectionId: String? = null,
    val selectionRevision: Int? = null,
    val timeZone: String? = null,
    val senseScope: String = "CONTEXTUAL_ONLY",
)

@Serializable
private data class KnowledgeDoc(
    val id: String,
    val learnerId: String,
    val language: String,
    val conceptId: String? = null,
    val diagnosticKey: String? = null,
    val skillType: String,
    val status: String,
    val successCount: Int,
    val weakCount: Int,
    val errorCount: Int,
    val lastOutcome: String? = null,
    val lastObservedAt: Long? = null,
    val evidenceStrength: String,
    val observationIds: List<String>,
    val independentSuccessUnits: Int = 0,
    val assistedSuccessUnits: Int = 0,
    val errorUnits: Int = 0,
    val distinctContexts: Int = 0,
    val distinctStudyDays: Int = 0,
    val lastIndependentSuccessAt: Long? = null,
    val lastErrorAt: Long? = null,
    val evidenceIds: List<String> = emptyList(),
    val projectorVersion: String = "projector-v1",
    val statusReason: String = "",
    val stale: Boolean = false,
    val senseScope: String = "SURFACE",
)

private fun SourceSpan.toDoc() = SpanDoc(quote, blockId?.value, startCodePoint, endCodePoint)
private fun SpanDoc.toDomain() = SourceSpan(quote, blockId?.let(::BlockId), start, end)
private fun Long.instant() = Instant.ofEpochMilli(this)
private fun Instant.millis() = toEpochMilli()

private fun LearnerSelection.toWire() = SelectionDoc(
    id.value, revision, learnerId.value, bookId.value, fragmentId.value, sessionId.value, sourceAnchors.map { it.toDoc() },
    quotedText, requestType.name, comment, helpTiming.name, createdAt.millis(), requestedAtPhase.name, contextBefore, contextAfter, deletedAt?.millis(),
)

private fun SelectionDoc.toDomain() = LearnerSelection(
    SelectionId(id), revision, LearnerId(learnerId), BookId(bookId), FragmentId(fragmentId), SessionId(sessionId),
    anchors.map { it.toDomain() }, quotedText, SelectionRequestType.valueOf(requestType), comment, HelpTiming.valueOf(helpTiming),
    createdAt.instant(), RequestedPhase.valueOf(phase), contextBefore, contextAfter, deletedAt?.instant(),
)

private fun AttemptFreeze.toWire() = FreezeDoc(
    sessionId.value, frozenAt.millis(),
    selections.map { FrozenSelDoc(it.id.value, it.revision, it.sourceAnchors.map { span -> span.toDoc() }, it.quotedText, it.requestType.name, it.comment, it.contextBefore, it.contextAfter, it.helpTiming.name) },
    explanations.map { FrozenExplDoc(it.selectionId.value, it.selectionRevision, it.explanation, it.contextualMeaning, it.nuance, it.example, it.sourceQuotes, it.revealedAt.millis()) },
    assistance.map { FrozenAssistDoc(it.id, it.level.name, it.scopeQuestionIds.map { id -> id.value }, it.scopeAll, it.scopeUnknown, it.selectionId?.value, it.text, it.shownAt.millis()) },
    evaluateInputJson,
)

private fun FreezeDoc.toDomain() = AttemptFreeze(
    SessionId(sessionId), frozenAt.instant(),
    selections.map { FrozenSelection(SelectionId(it.id), it.revision, it.anchors.map { span -> span.toDomain() }, it.quotedText, SelectionRequestType.valueOf(it.requestType), it.comment, it.contextBefore, it.contextAfter, HelpTiming.valueOf(it.helpTiming)) },
    explanations.map { FrozenExplanation(SelectionId(it.selectionId), it.revision, it.explanation, it.contextualMeaning, it.nuance, it.example, it.sourceQuotes, it.revealedAt.instant()) },
    assistance.map { FrozenAssistance(it.id, AssistanceLevel.valueOf(it.level), it.scopeQuestionIds.map(::QuestionId), it.scopeAll, it.scopeUnknown, it.selectionId?.let(::SelectionId), it.text, it.shownAt.instant()) },
    evaluateInputJson,
)

private fun AssistanceEvent.toWire() = AssistDoc(id, sessionId.value, level.name, scopeQuestionIds.map { it.value }, scopeAll, scopeUnknown, selectionId?.value, selectionRevision, text, shownAt.millis(), source, countsTowardAttempt)
private fun AssistDoc.toDomain() = AssistanceEvent(id, SessionId(sessionId), AssistanceLevel.valueOf(level), scopeQuestionIds.map(::QuestionId), scopeAll, scopeUnknown, selectionId?.let(::SelectionId), selectionRevision, text, shownAt.instant(), source, countsTowardAttempt)

private fun ExplainRecord.toWire() = ExplainDoc(id, sessionId.value, selectionId.value, selectionRevision, idempotencyKey, status.name, responseJson, explanation, contextualMeaning, nuance, example, sourceQuotes, reviewStatus?.name, revealedAt?.millis(), createdAt.millis(), providerKind, model, promptVersion, error, countsTowardAttempt)
private fun ExplainDoc.toDomain() = ExplainRecord(id, SessionId(sessionId), SelectionId(selectionId), selectionRevision, idempotencyKey, CallStatus.valueOf(status), responseJson, explanation, contextualMeaning, nuance, example, sourceQuotes, reviewStatus?.let { SelectionReviewStatus.valueOf(it) }, revealedAt?.instant(), createdAt.instant(), providerKind, model, promptVersion, error, countsTowardAttempt)

private fun ContinuityNote.toWire() = NoteDoc(sessionId.value, text, sourceQuote, createdAt.millis())
private fun NoteDoc.toDomain() = ContinuityNote(SessionId(sessionId), text, sourceQuote, createdAt.instant())

private fun LessonQuestion.toWire() = QuestionDoc(id.value, sessionId.value, type.name, prompt, targetSkill.name, targetExpression, sourceAnchors.map { it.toDoc() }, order, purpose.name, conceptRef, rubric?.let { RubricDoc(it.expectedMeaning, it.requiredPoints, it.acceptableAlternatives, it.unsupportedInferences, it.evidenceQuotes) }, localId)
private fun QuestionDoc.toDomain() = LessonQuestion(QuestionId(id), SessionId(sessionId), QuestionType.valueOf(type), prompt, SkillType.valueOf(targetSkill), targetExpression, anchors.map { it.toDomain() }, order, QuestionPurpose.valueOf(purpose), conceptRef, rubric?.let { QuestionRubric(it.expectedMeaning, it.requiredPoints, it.acceptableAlternatives, it.unsupportedInferences, it.evidenceQuotes) }, localId)

private fun LessonReview.toWire() = ReviewDoc(
    sessionId.value,
    results.map { QuestionReviewDoc(it.questionId.value, it.verdict.name, it.explanation, it.errorNotes, it.evidenceSufficient, it.examples, it.demonstratedMeaning, it.nuance, it.productionFeedback, it.rubricIssue) },
    observationProposalIds.map { it.value }, nextRecommendations, aiCallId.value, createdAt.millis(),
    selectionReviews.map { SelectionReviewDoc(it.selectionId.value, it.selectionRevision, it.status.name, it.explanation, it.contextualMeaning, it.nuance, it.example, it.sourceQuotes) },
    feedbackResponse,
)
private fun ReviewDoc.toDomain() = LessonReview(
    SessionId(sessionId),
    results.map { QuestionReview(QuestionId(it.questionId), AnswerVerdict.valueOf(it.verdict), it.explanation, it.errorNotes, it.evidenceSufficient, it.examples, it.demonstratedMeaning, it.nuance, it.productionFeedback, it.rubricIssue) },
    proposalIds.map(::ProposalId), recommendations, AiCallId(aiCallId), createdAt.instant(),
    selectionReviews.map { SelectionReview(SelectionId(it.selectionId), it.selectionRevision, SelectionReviewStatus.valueOf(it.status), it.explanation, it.contextualMeaning, it.nuance, it.example, it.sourceQuotes) },
    feedbackResponse,
)

private fun LearnerObservation.toWire() = ObservationDoc(
    id.value, learnerId.value, language, sessionId.value, questionId?.value, conceptId?.value, skillType.name, observationType.name, outcome.name,
    studentEvidence, sourceEvidence, sourceAnchors.map { it.toDoc() }, interpretation, confidence.name, evaluatorProvider, evaluatorModel, evaluationVersion,
    createdAt.millis(), sequence, bookId?.value, diagnosticKey, evidenceSource.name, evidenceMode.name, assistance.name, eligibleForIndependentSuccess,
    schemaVersion, policyVersion, evidenceUnitId, contextId, selectionId?.value, selectionRevision, timeZone, senseScope.name,
)
private fun ObservationDoc.toDomain() = LearnerObservation(
    ObservationId(id), LearnerId(learnerId), language, SessionId(sessionId), questionId?.let(::QuestionId), conceptId?.let(::ConceptId),
    SkillType.valueOf(skillType), ObservationType.valueOf(observationType), Outcome.valueOf(outcome), studentEvidence, sourceEvidence, anchors.map { it.toDomain() },
    interpretation, Confidence.valueOf(confidence), evaluatorProvider, evaluatorModel, evaluationVersion, createdAt.instant(), sequence,
    bookId?.let(::BookId), diagnosticKey, EvidenceSource.valueOf(evidenceSource), EvidenceMode.valueOf(evidenceMode), AssistanceLevel.valueOf(assistance),
    eligibleForIndependentSuccess, schemaVersion, policyVersion, evidenceUnitId, contextId, selectionId?.let(::SelectionId), selectionRevision, timeZone, SenseScope.valueOf(senseScope),
)

private fun LearnerKnowledgeState.toWire() = KnowledgeDoc(
    id, learnerId.value, language, conceptId?.value, diagnosticKey, skillType.name, status.name, successCount, weakCount, errorCount, lastOutcome?.name,
    lastObservedAt?.millis(), evidenceStrength.name, observationIds.map { it.value }, independentSuccessUnits, assistedSuccessUnits, errorUnits,
    distinctContexts, distinctStudyDays, lastIndependentSuccessAt?.millis(), lastErrorAt?.millis(), evidenceIds, projectorVersion, statusReason, stale, senseScope.name,
)
private fun KnowledgeDoc.toDomain() = LearnerKnowledgeState(
    id, LearnerId(learnerId), language, conceptId?.let(::ConceptId), diagnosticKey, SkillType.valueOf(skillType), KnowledgeStatus.valueOf(status),
    successCount, weakCount, errorCount, lastOutcome?.let { Outcome.valueOf(it) }, lastObservedAt?.instant(), EvidenceStrength.valueOf(evidenceStrength),
    observationIds.map(::ObservationId), independentSuccessUnits, assistedSuccessUnits, errorUnits, distinctContexts, distinctStudyDays,
    lastIndependentSuccessAt?.instant(), lastErrorAt?.instant(), evidenceIds, projectorVersion, statusReason, stale, SenseScope.valueOf(senseScope),
)

@Suppress("unused")
private val kindMarker = ConceptKind.LEXICAL
