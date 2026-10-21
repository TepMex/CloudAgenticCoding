package com.tepmex.extensivereadingtutor.pedagogy

import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.EvidenceMode
import com.tepmex.extensivereadingtutor.domain.EvidenceSource
import com.tepmex.extensivereadingtutor.domain.HintDepth
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.QuestionPurpose
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SelectionRequestType
import com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.SubjectiveDifficulty
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PrepareSchemaVersion {
    @SerialName("prepare-v2") PREPARE_V2,
}

@Serializable
enum class EvaluateSchemaVersion {
    @SerialName("evaluate-v2") EVALUATE_V2,
}

@Serializable
enum class ExplainSchemaVersion {
    @SerialName("explain-selection-v1") EXPLAIN_V1,
}

@Serializable
data class AnchorWire(
    val blockId: String,
    val startCodePoint: Int,
    val endCodePoint: Int,
    val quote: String,
)

@Serializable
data class FragmentWire(
    val id: String,
    val text: String,
    val anchors: List<AnchorWire>,
)

@Serializable
data class PreferencesWire(
    val studyLanguage: String,
    val explanationLanguage: String,
    val declaredLevel: String,
    val declaredLevelIsMeasured: Boolean,
    val difficulty: DifficultyPreference,
    val explanationDepth: HintDepth,
)

@Serializable
data class LessonPolicyWire(
    val version: String,
    val minQuestions: Int,
    val maxQuestions: Int,
    val maxHints: Int,
    val allowActiveProduction: Boolean,
    val maxRevisitQuestions: Int,
    val continuityNotesEnabled: Boolean,
    val answerBeforeHintsMayRevealMeaning: Boolean,
)

@Serializable
data class SelectionWire(
    val id: String,
    val revision: Int,
    val quotedText: String,
    val anchors: List<AnchorWire>,
    val requestType: SelectionRequestType,
    val comment: String?,
    val contextBefore: String,
    val contextAfter: String,
    val helpTiming: String,
)

@Serializable
data class MemoryItemWire(
    val id: String,
    val label: String,
    val skill: SkillType,
    val support: String,
    val independentSuccesses: Int,
    val stale: Boolean,
    val senseScope: String,
    val limitation: String,
)

@Serializable
data class TendencyWire(
    val key: String,
    val statement: String,
    val supportCount: Int,
    val counterCount: Int,
    val sessionCount: Int,
    val established: Boolean,
    val limitation: String,
    val example: String?,
)

@Serializable
data class ReportedDifficultyWire(
    val label: String,
    val scope: String,
    val uncertainty: String,
    val sessionId: String?,
)

@Serializable
data class PrepareMemoryWire(
    val relevantDifficulties: List<MemoryItemWire>,
    val familiarChecks: List<MemoryItemWire>,
    val revisitCandidateId: String?,
    val tendencies: List<TendencyWire>,
    val recentQuestionTypes: List<String>,
    val supportProfile: String,
    val reportedDifficulties: List<ReportedDifficultyWire>,
)

@Serializable
data class PrepareInputV2(
    val fragment: FragmentWire,
    val precedingText: String?,
    val preferences: PreferencesWire,
    val lessonPolicy: LessonPolicyWire,
    val learnerFocus: List<SelectionWire>,
    val memory: PrepareMemoryWire,
)

@Serializable
data class RubricWire(
    val expectedMeaning: String,
    val requiredPoints: List<String>,
    val acceptableAlternatives: List<String>,
    val unsupportedInferences: List<String>,
    val evidenceQuotes: List<String>,
)

@Serializable
data class HintOutputV2(
    val localId: String,
    val text: String,
    val targetQuestionLocalIds: List<String>,
    val assistanceLevel: AssistanceLevel,
)

@Serializable
data class QuestionOutputV2(
    val localId: String,
    val type: QuestionType,
    val prompt: String,
    val targetSkill: SkillType,
    val targetExpression: String?,
    val conceptRef: String?,
    val sourceQuotes: List<String>,
    val purpose: QuestionPurpose,
    val rubric: RubricWire,
)

@Serializable
data class PrepareOutputV2(
    val schemaVersion: PrepareSchemaVersion,
    val introduction: String,
    val hints: List<HintOutputV2>,
    val questions: List<QuestionOutputV2>,
)

@Serializable
data class EvalQuestionWire(
    val questionId: String,
    val type: QuestionType,
    val prompt: String,
    val targetSkill: SkillType,
    val targetExpression: String?,
    val purpose: QuestionPurpose,
    val sourceQuotes: List<String>,
    val rubric: RubricWire,
    val answer: String,
)

@Serializable
data class ShownHintWire(
    val text: String,
    val assistanceLevel: AssistanceLevel,
    val scopeQuestionIds: List<String>,
    val scopeAll: Boolean,
)

@Serializable
data class AssistanceWire(
    val level: AssistanceLevel,
    val scopeQuestionIds: List<String>,
    val scopeAll: Boolean,
    val scopeUnknown: Boolean,
    val selectionId: String?,
    val text: String,
)

@Serializable
data class RevealedExplanationWire(
    val selectionId: String,
    val selectionRevision: Int,
    val text: String,
)

@Serializable
data class CatalogEntryWire(
    val id: String,
    val label: String,
    val kind: String,
)

@Serializable
data class EvaluateInputV2(
    val fragment: FragmentWire,
    val precedingText: String?,
    val introduction: String,
    val questions: List<EvalQuestionWire>,
    val shownHints: List<ShownHintWire>,
    val assistanceEvents: List<AssistanceWire>,
    val subjectiveDifficulty: SubjectiveDifficulty?,
    val wantsMoreExplanation: Boolean,
    val unclearNotes: String?,
    val freeComment: String?,
    val selections: List<SelectionWire>,
    val revealedExplanations: List<RevealedExplanationWire>,
    val conceptCatalog: List<CatalogEntryWire>,
    val lessonPolicy: LessonPolicyWire,
    val preferences: PreferencesWire,
)

@Serializable
data class QuestionReviewOutputV2(
    val questionId: String,
    val verdict: AnswerVerdict,
    val evidenceSufficient: Boolean,
    val demonstratedMeaning: String,
    val explanation: String,
    val nuance: String?,
    val productionFeedback: String?,
    val errorNotes: String,
    val examples: List<String>,
    val rubricIssue: String?,
)

@Serializable
data class SelectionReviewOutputV2(
    val selectionId: String,
    val selectionRevision: Int,
    val status: SelectionReviewStatus,
    val explanation: String,
    val contextualMeaning: String?,
    val nuance: String?,
    val example: String?,
    val sourceQuotes: List<String>,
)

@Serializable
data class ObservationOutputV2(
    val questionId: String?,
    val selectionId: String?,
    val selectionRevision: Int?,
    val conceptRef: String?,
    val conceptLabel: String?,
    val diagnosticKey: String?,
    val skillType: SkillType,
    val observationType: ObservationType,
    val outcome: Outcome,
    val confidence: Confidence,
    val evidenceSource: EvidenceSource,
    val studentEvidence: String,
    val sourceQuote: String,
    val evidenceMode: EvidenceMode,
    val assistanceAssessment: AssistanceLevel,
    val interpretation: String,
)

@Serializable
data class ContinuityNoteWire(
    val text: String,
    val sourceQuote: String,
)

@Serializable
data class EvaluateOutputV2(
    val schemaVersion: EvaluateSchemaVersion,
    val questionReviews: List<QuestionReviewOutputV2>,
    val selectionReviews: List<SelectionReviewOutputV2>,
    val feedbackResponse: String?,
    val observations: List<ObservationOutputV2>,
    val nextRecommendations: List<String>,
    val continuityNotes: List<ContinuityNoteWire>,
)

@Serializable
data class QuestionScopeWire(
    val questionId: String,
    val prompt: String,
    val targetExpression: String?,
    val sourceQuotes: List<String>,
)

@Serializable
data class ExplainInputV1(
    val selection: SelectionWire,
    val sourceContext: String,
    val explanationLanguage: String,
    val studyLanguage: String,
    val requestedDepth: HintDepth,
    val questionScopes: List<QuestionScopeWire>,
)

@Serializable
data class ExplainOutputV1(
    val schemaVersion: ExplainSchemaVersion,
    val selectionId: String,
    val selectionRevision: Int,
    val status: SelectionReviewStatus,
    val explanation: String,
    val contextualMeaning: String?,
    val nuance: String?,
    val example: String?,
    val sourceQuotes: List<String>,
)
