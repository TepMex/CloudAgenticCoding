package com.tepmex.extensivereadingtutor.domain

import java.time.Instant
import kotlinx.serialization.Serializable

@JvmInline value class BookId(val value: String)
@JvmInline value class BlockId(val value: String)
@JvmInline value class FragmentId(val value: String)
@JvmInline value class SessionId(val value: String)
@JvmInline value class QuestionId(val value: String)
@JvmInline value class ObservationId(val value: String)
@JvmInline value class ConceptId(val value: String)
@JvmInline value class AiCallId(val value: String)
@JvmInline value class LearnerId(val value: String)
@JvmInline value class ProposalId(val value: String)
@JvmInline value class SelectionId(val value: String)

val LOCAL_LEARNER = LearnerId("local")

enum class SessionStatus {
    CREATED,
    PREPARING,
    INTRO_READY,
    QUESTIONS_READY,
    AWAITING_ANSWERS,
    EVALUATING,
    REVIEW_READY,
    COMPLETED,
}

@Serializable
enum class QuestionType {
    MAIN_IDEA,
    DETAIL_LOOKUP,
    CONTEXT_VOCABULARY,
    IDIOM,
    GRAMMAR,
    INFERENCE_CHECK,
    ACTIVE_PRODUCTION,
}

@Serializable
enum class SkillType {
    VOCABULARY_RECOGNITION,
    IDIOM_COMPREHENSION,
    GRAMMAR_COMPREHENSION,
    READING_COMPREHENSION,
    CONTEXTUAL_INFERENCE,
    TEXTUAL_ACCURACY,
    ACTIVE_PRODUCTION,
}

@Serializable
enum class ObservationType {
    CORRECT_UNDERSTANDING,
    PARTIAL_UNDERSTANDING,
    SEMANTIC_MISINTERPRETATION,
    GRAMMAR_DIFFICULTY,
    UNKNOWN_VOCABULARY,
    SUCCESSFUL_CONTEXT_INFERENCE,
    UNSUPPORTED_TEXT_INFERENCE,
    SUCCESSFUL_ACTIVE_USAGE,
    HINT_DEPENDENT_SUCCESS,
}

@Serializable
enum class Outcome { POSITIVE, WEAK, NEGATIVE }

@Serializable
enum class Confidence { LOW, MEDIUM, HIGH }

enum class EvidenceStrength { LOW, MEDIUM, HIGH }

enum class KnowledgeStatus { LEARNING, NEEDS_REVIEW, LIKELY_KNOWN, STABLE }

@Serializable
enum class DifficultyPreference { EASY, MATCHED, STRETCH }

@Serializable
enum class HintDepth { MINIMAL, MODERATE, DETAILED }

@Serializable
enum class SubjectiveDifficulty { TOO_EASY, SUITABLE, TOO_HARD }

@Serializable
enum class AnswerVerdict { CORRECT, PARTIAL, INCORRECT, UNANSWERED, NOT_ASSESSABLE }

enum class ProviderKind { OPENAI_COMPATIBLE }

enum class AiOperation { PREPARE_LESSON, EVALUATE_ANSWERS, EXPLAIN_SELECTION }

@Serializable
enum class EvidenceSource { ANSWER, UNCLEAR_NOTES, FREE_COMMENT, SELECTION_COMMENT }

@Serializable
enum class EvidenceMode { DEMONSTRATED, SELF_REPORTED, AMBIGUOUS }

@Serializable
enum class AssistanceLevel { NONE_RECORDED, SCAFFOLD, MEANING_GIVEN, ANSWER_GIVEN, UNKNOWN }

@Serializable
enum class QuestionPurpose { COMPREHENSION, CONTEXTUAL_LEARNING, REVISIT }

@Serializable
enum class SelectionRequestType { GENERAL, MEANING, GRAMMAR, REFERENCE, CUSTOM }

enum class HelpTiming { WITH_REVIEW, NOW }

@Serializable
enum class SelectionReviewStatus { EXPLAINED, NEEDS_CONTEXT }

enum class ConceptKind { LEXICAL, IDIOM, GRAMMAR, DIAGNOSTIC }

enum class SenseScope { SURFACE, CONTEXTUAL_ONLY }

enum class RequestedPhase { BEFORE_PREPARE, AFTER_PREPARE, AFTER_SUBMISSION }

enum class CallStatus { SUCCEEDED, REJECTED_OUTPUT, FAILED }

enum class ProposalStatus { ACCEPTED, REJECTED }

enum class LlmFailureKind { TIMEOUT, RATE_LIMIT, UNAUTHORIZED, BAD_RESPONSE, NETWORK }

fun ObservationType.expectedOutcome(): Outcome = when (this) {
    ObservationType.CORRECT_UNDERSTANDING,
    ObservationType.SUCCESSFUL_CONTEXT_INFERENCE,
    ObservationType.SUCCESSFUL_ACTIVE_USAGE,
    -> Outcome.POSITIVE
    ObservationType.PARTIAL_UNDERSTANDING,
    ObservationType.HINT_DEPENDENT_SUCCESS,
    -> Outcome.WEAK
    ObservationType.SEMANTIC_MISINTERPRETATION,
    ObservationType.GRAMMAR_DIFFICULTY,
    ObservationType.UNKNOWN_VOCABULARY,
    ObservationType.UNSUPPORTED_TEXT_INFERENCE,
    -> Outcome.NEGATIVE
}

data class EpubMetadata(
    val identifier: String?,
    val publisher: String?,
    val description: String?,
    val rawLanguage: String?,
)

data class Book(
    val id: BookId,
    val title: String,
    val author: String,
    val language: String,
    val sourceUri: String,
    val localRef: String,
    val contentHash: String,
    val importedAt: Instant,
    val metadata: EpubMetadata,
    val removedAt: Instant?,
)

data class BookBlock(
    val id: BlockId,
    val bookId: BookId,
    val spineIndex: Int,
    val spineHref: String,
    val orderInBook: Int,
    val text: String,
    val textHash: String,
    val chapterTitle: String?,
)

data class TextAnchor(
    val bookId: BookId,
    val spineIndex: Int,
    val spineHref: String,
    val blockId: BlockId,
    val startCodePoint: Int,
    val endCodePoint: Int,
    val quote: String,
    val quoteSha256: String,
)

data class BookFragment(
    val id: FragmentId,
    val bookId: BookId,
    val anchors: List<TextAnchor>,
    val originalText: String,
    val textHash: String,
    val chapterTitle: String?,
    val chapterHref: String?,
    val codePointLength: Int,
    val segmentationVersion: String,
)

data class TextCursor(val blockOrder: Int, val codePoint: Int)

data class ReadingProgress(
    val bookId: BookId,
    val cursor: TextCursor,
    val activeSessionId: SessionId?,
    val completedFragmentIds: List<FragmentId>,
    val updatedAt: Instant,
)

data class CompletedRange(
    val id: String,
    val bookId: BookId,
    val sessionId: SessionId,
    val fragmentId: FragmentId,
    val completedAt: Instant,
)

data class SourceSpan(
    val quote: String,
    val blockId: BlockId?,
    val startCodePoint: Int?,
    val endCodePoint: Int?,
)

data class QuestionRubric(
    val expectedMeaning: String,
    val requiredPoints: List<String>,
    val acceptableAlternatives: List<String>,
    val unsupportedInferences: List<String>,
    val evidenceQuotes: List<String>,
)

data class HintPlan(
    val localId: String,
    val text: String,
    val targetQuestionLocalIds: List<String>,
    val assistanceLevel: AssistanceLevel,
)

data class LessonQuestion(
    val id: QuestionId,
    val sessionId: SessionId,
    val type: QuestionType,
    val prompt: String,
    val targetSkill: SkillType,
    val targetExpression: String?,
    val sourceAnchors: List<SourceSpan>,
    val order: Int,
    val purpose: QuestionPurpose = QuestionPurpose.COMPREHENSION,
    val conceptRef: String? = null,
    val rubric: QuestionRubric? = null,
    val localId: String? = null,
)

data class PreparedLesson(
    val introduction: String,
    val hints: List<String>,
    val questions: List<LessonQuestion>,
    val strategyVersion: String,
    val hintPlans: List<HintPlan> = emptyList(),
)

data class AnswerDraft(
    val sessionId: SessionId,
    val questionId: QuestionId,
    val text: String,
    val updatedAt: Instant,
)

data class SubmittedAnswer(val questionId: QuestionId, val text: String)

data class StudentSubmission(
    val sessionId: SessionId,
    val answers: List<SubmittedAnswer>,
    val difficulty: SubjectiveDifficulty?,
    val wantsMoreExplanation: Boolean,
    val unclearNotes: String?,
    val freeComment: String?,
    val hintRequestCount: Int,
    val submittedAt: Instant,
)

data class QuestionReview(
    val questionId: QuestionId,
    val verdict: AnswerVerdict,
    val explanation: String,
    val errorNotes: String,
    val evidenceSufficient: Boolean,
    val examples: List<String>,
    val demonstratedMeaning: String = "",
    val nuance: String? = null,
    val productionFeedback: String? = null,
    val rubricIssue: String? = null,
)

data class SelectionReview(
    val selectionId: SelectionId,
    val selectionRevision: Int,
    val status: SelectionReviewStatus,
    val explanation: String,
    val contextualMeaning: String? = null,
    val nuance: String? = null,
    val example: String? = null,
    val sourceQuotes: List<String> = emptyList(),
)

data class LessonReview(
    val sessionId: SessionId,
    val results: List<QuestionReview>,
    val observationProposalIds: List<ProposalId>,
    val nextRecommendations: List<String>,
    val aiCallId: AiCallId,
    val createdAt: Instant,
    val selectionReviews: List<SelectionReview> = emptyList(),
    val feedbackResponse: String? = null,
)

data class LessonSession(
    val id: SessionId,
    val bookId: BookId,
    val fragmentId: FragmentId,
    val learnerId: LearnerId,
    val status: SessionStatus,
    val strategyVersion: String,
    val prepareCallId: AiCallId?,
    val evaluateCallId: AiCallId?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val observationsAppliedAt: Instant?,
    val prepared: PreparedLesson?,
    val submission: StudentSubmission?,
    val review: LessonReview?,
)

data class Concept(
    val id: ConceptId,
    val language: String,
    val label: String,
    val kind: ConceptKind = ConceptKind.LEXICAL,
    val senseScope: SenseScope = SenseScope.CONTEXTUAL_ONLY,
    val senseGloss: String? = null,
)

data class LearnerObservation(
    val id: ObservationId,
    val learnerId: LearnerId,
    val language: String,
    val sessionId: SessionId,
    val questionId: QuestionId?,
    val conceptId: ConceptId?,
    val skillType: SkillType,
    val observationType: ObservationType,
    val outcome: Outcome,
    val studentEvidence: String,
    val sourceEvidence: String,
    val sourceAnchors: List<SourceSpan>,
    val interpretation: String,
    val confidence: Confidence,
    val evaluatorProvider: String,
    val evaluatorModel: String,
    val evaluationVersion: String,
    val createdAt: Instant,
    val sequence: Long,
    val bookId: BookId? = null,
    val diagnosticKey: String? = null,
    val evidenceSource: EvidenceSource = EvidenceSource.ANSWER,
    val evidenceMode: EvidenceMode = EvidenceMode.DEMONSTRATED,
    val assistance: AssistanceLevel = AssistanceLevel.UNKNOWN,
    val eligibleForIndependentSuccess: Boolean = false,
    val schemaVersion: String = "",
    val policyVersion: String = "",
    val evidenceUnitId: String? = null,
    val contextId: String? = null,
    val selectionId: SelectionId? = null,
    val selectionRevision: Int? = null,
    val timeZone: String? = null,
    val senseScope: SenseScope = SenseScope.CONTEXTUAL_ONLY,
)

data class ObservationRetraction(
    val id: String,
    val observationId: ObservationId,
    val reason: String,
    val createdAt: Instant,
)

data class ObservationProposal(
    val id: ProposalId,
    val sessionId: SessionId,
    val contentHash: String,
    val status: ProposalStatus,
    val rejectionReason: String?,
    val observationId: ObservationId?,
    val payloadJson: String,
    val createdAt: Instant,
)

data class LearnerKnowledgeState(
    val id: String,
    val learnerId: LearnerId,
    val language: String,
    val conceptId: ConceptId?,
    val diagnosticKey: String?,
    val skillType: SkillType,
    val status: KnowledgeStatus,
    val successCount: Int,
    val weakCount: Int,
    val errorCount: Int,
    val lastOutcome: Outcome?,
    val lastObservedAt: Instant?,
    val evidenceStrength: EvidenceStrength,
    val observationIds: List<ObservationId>,
    val independentSuccessUnits: Int = 0,
    val assistedSuccessUnits: Int = 0,
    val errorUnits: Int = 0,
    val distinctContexts: Int = 0,
    val distinctStudyDays: Int = 0,
    val lastIndependentSuccessAt: Instant? = null,
    val lastErrorAt: Instant? = null,
    val evidenceIds: List<String> = emptyList(),
    val projectorVersion: String = "projector-v1",
    val statusReason: String = "",
    val stale: Boolean = false,
    val senseScope: SenseScope = SenseScope.SURFACE,
)

data class LearnerPreferences(
    val learnerId: LearnerId,
    val studyLanguage: String,
    val explanationLanguage: String,
    val selfAssessedLevel: String,
    val preferredDifficulty: DifficultyPreference,
    val hintDepth: HintDepth,
    val targetFragmentCodePoints: Int,
    val updatedAt: Instant,
    val timeZone: String? = null,
)

fun defaultPreferences(now: Instant): LearnerPreferences = LearnerPreferences(
    learnerId = LOCAL_LEARNER,
    studyLanguage = "zh",
    explanationLanguage = "ru",
    selfAssessedLevel = "HSK 4",
    preferredDifficulty = DifficultyPreference.MATCHED,
    hintDepth = HintDepth.MINIMAL,
    targetFragmentCodePoints = 180,
    updatedAt = now,
)

data class LlmCapabilities(
    val jsonObjectMode: Boolean = true,
    val reportsUsage: Boolean = true,
    val systemRole: Boolean = true,
    val strictJsonSchema: Boolean = false,
    val contextWindowTokens: Int? = null,
)

data class ProviderConfig(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val capabilities: LlmCapabilities,
)

data class ModelSnapshot(
    val providerConfigId: String,
    val providerKind: String,
    val model: String,
)

data class TokenUsage(
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
)

data class AiCallRecord(
    val id: AiCallId,
    val sessionId: SessionId,
    val operation: AiOperation,
    val providerConfigId: String,
    val providerKind: String,
    val model: String,
    val promptTemplateVersion: String,
    val strategyVersion: String,
    val requestFingerprint: String,
    val status: CallStatus,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val totalTokens: Int?,
    val rawJson: String?,
    val startedAt: Instant,
    val finishedAt: Instant,
)

fun interface AppClock {
    fun now(): Instant
}

fun interface IdGenerator {
    fun newId(prefix: String): String
}

class UuidIds : IdGenerator {
    override fun newId(prefix: String): String = "$prefix-${java.util.UUID.randomUUID()}"
}

fun interface ModelSource {
    fun current(): ModelSnapshot
}
