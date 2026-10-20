package com.tepmex.extensivereadingtutor.domain

import java.time.Instant

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

enum class QuestionType {
    MAIN_IDEA,
    DETAIL_LOOKUP,
    CONTEXT_VOCABULARY,
    IDIOM,
    GRAMMAR,
    INFERENCE_CHECK,
    ACTIVE_PRODUCTION,
}

enum class SkillType {
    VOCABULARY_RECOGNITION,
    IDIOM_COMPREHENSION,
    GRAMMAR_COMPREHENSION,
    READING_COMPREHENSION,
    CONTEXTUAL_INFERENCE,
    TEXTUAL_ACCURACY,
    ACTIVE_PRODUCTION,
}

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

enum class Outcome { POSITIVE, WEAK, NEGATIVE }

enum class Confidence { LOW, MEDIUM, HIGH }

enum class EvidenceStrength { LOW, MEDIUM, HIGH }

enum class KnowledgeStatus { LEARNING, NEEDS_REVIEW, LIKELY_KNOWN, STABLE }

enum class DifficultyPreference { EASY, MATCHED, STRETCH }

enum class HintDepth { MINIMAL, MODERATE, DETAILED }

enum class SubjectiveDifficulty { TOO_EASY, SUITABLE, TOO_HARD }

enum class AnswerVerdict { CORRECT, PARTIAL, INCORRECT, UNANSWERED }

enum class ProviderKind { OPENAI_COMPATIBLE }

enum class AiOperation { PREPARE_LESSON, EVALUATE_ANSWERS }

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

data class LessonQuestion(
    val id: QuestionId,
    val sessionId: SessionId,
    val type: QuestionType,
    val prompt: String,
    val targetSkill: SkillType,
    val targetExpression: String?,
    val sourceAnchors: List<SourceSpan>,
    val order: Int,
)

data class PreparedLesson(
    val introduction: String,
    val hints: List<String>,
    val questions: List<LessonQuestion>,
    val strategyVersion: String,
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
)

data class LessonReview(
    val sessionId: SessionId,
    val results: List<QuestionReview>,
    val observationProposalIds: List<ProposalId>,
    val nextRecommendations: List<String>,
    val aiCallId: AiCallId,
    val createdAt: Instant,
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
