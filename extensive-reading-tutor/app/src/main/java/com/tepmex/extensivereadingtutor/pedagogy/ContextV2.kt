package com.tepmex.extensivereadingtutor.pedagogy

import com.tepmex.extensivereadingtutor.books.sha256
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.BudgetReport
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ContinuityNote
import com.tepmex.extensivereadingtutor.domain.EvidenceMode
import com.tepmex.extensivereadingtutor.domain.FrozenAssistance
import com.tepmex.extensivereadingtutor.domain.FrozenExplanation
import com.tepmex.extensivereadingtutor.domain.FrozenSelection
import com.tepmex.extensivereadingtutor.domain.HintPlan
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.ReadingProfile
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.learner.EvidenceUnitReducer
import com.tepmex.extensivereadingtutor.learner.readingProfile
import com.tepmex.extensivereadingtutor.learner.tendencies
import java.nio.charset.StandardCharsets
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val wireJson = Json { encodeDefaults = true; explicitNulls = true }

data class PreparedContext(
    val input: PrepareInputV2,
    val userJson: String,
    val system: String,
    val report: BudgetReport,
    val profile: ReadingProfile,
)

data class EvaluationContext(
    val input: EvaluateInputV2,
    val userJson: String,
    val system: String,
    val report: BudgetReport,
)

sealed interface ContextResult<T> {
    data class Ready<T>(val value: T) : ContextResult<T>
    data class Overflow<T>(val message: String, val report: BudgetReport) : ContextResult<T>
}

class PrepareContextBuilder(private val config: LessonPolicyConfig = LessonPolicyConfig()) {
    private val reducer = EvidenceUnitReducer()

    fun build(
        fragment: BookFragment,
        language: String,
        preceding: String?,
        preferences: LearnerPreferences,
        knowledge: List<LearnerKnowledgeState>,
        concepts: List<Concept>,
        observations: List<LearnerObservation>,
        recentTypes: List<String>,
        learnerFocus: List<FrozenSelection>,
        continuity: List<ContinuityNote>,
        contextWindowTokens: Int,
        now: java.time.Instant,
    ): ContextResult<PreparedContext> {
        val profile = readingProfile(reducer.reduce(observations.filter { it.language == language }, emptySet()))
        val memory = selectMemory(fragment, language, knowledge, concepts, observations, recentTypes, profile, continuity, now)
        val policy = config.toWire(preferences)
        val prefs = preferences.toWire()
        var packet = memory.packet
        var precedingText = preceding?.takeIf { it.isNotEmpty() }
        var report = memory.report
        fun assemble(current: PrepareMemoryWire, previous: String?): PrepareInputV2 = PrepareInputV2(
            fragment = fragment.toWire(),
            precedingText = previous,
            preferences = prefs,
            lessonPolicy = policy,
            learnerFocus = learnerFocus.map { it.toWire() },
            memory = current,
        )
        var input = assemble(packet, precedingText)
        var user = wireJson.encodeToString(input)
        var estimate = estimateRequest(PromptPack.system(PromptPack.PREPARE, PREPARE_SCHEMA_JSON), user)
        if (!fits(estimate, contextWindowTokens, config.prepareMaxTokens)) {
            packet = packet.copy(tendencies = emptyList(), familiarChecks = emptyList(), supportProfile = profile.summary)
            report = report.copy(cutoffReason = "optional-memory", omittedIds = report.omittedIds + report.selectedIds.filter { it.startsWith("familiar:") || it.startsWith("tendency:") })
            input = assemble(packet, precedingText)
            user = wireJson.encodeToString(input)
            estimate = estimateRequest(PromptPack.system(PromptPack.PREPARE, PREPARE_SCHEMA_JSON), user)
        }
        if (!fits(estimate, contextWindowTokens, config.prepareMaxTokens)) {
            precedingText = null
            report = report.copy(cutoffReason = "preceding-context")
            input = assemble(packet, null)
            user = wireJson.encodeToString(input)
            estimate = estimateRequest(PromptPack.system(PromptPack.PREPARE, PREPARE_SCHEMA_JSON), user)
        }
        val system = PromptPack.system(PromptPack.PREPARE, PREPARE_SCHEMA_JSON)
        val finalReport = report.copy(
            estimatedInputTokens = estimate,
            estimatedOutputTokens = config.prepareMaxTokens,
            estimateUncertain = true,
            contextWindowTokens = contextWindowTokens,
        )
        if (!fits(estimate, contextWindowTokens, config.prepareMaxTokens)) {
            return ContextResult.Overflow("Фрагмент и обязательный контекст не помещаются в окно модели. Возьмите более короткий фрагмент или больший лимит.", finalReport)
        }
        return ContextResult.Ready(PreparedContext(input, user, system, finalReport, profile))
    }

    private fun selectMemory(
        fragment: BookFragment,
        language: String,
        knowledge: List<LearnerKnowledgeState>,
        concepts: List<Concept>,
        observations: List<LearnerObservation>,
        recentTypes: List<String>,
        profile: ReadingProfile,
        continuity: List<ContinuityNote>,
        now: java.time.Instant,
    ): MemorySelection {
        val byId = concepts.associateBy { it.id }
        data class Candidate(val priority: Int, val sort: String, val id: String, val item: MemoryItemWire, val familiar: Boolean)
        val candidates = mutableListOf<Candidate>()
        for (state in knowledge.filter { it.language == language }.sortedBy { it.id }) {
            val concept = state.conceptId?.let { byId[it] } ?: continue
            val matched = labelInText(language, fragment.originalText, concept.label) ||
                Catalog.grammarById(concept.id.value)?.aliases?.any { labelInText(language, fragment.originalText, it) } == true
            if (!matched) continue
            val familiar = state.status == KnowledgeStatus.LIKELY_KNOWN || state.status == KnowledgeStatus.STABLE
            val priority = when {
                state.stale -> 0
                state.status == KnowledgeStatus.NEEDS_REVIEW || state.errorUnits > 0 -> 1
                !familiar -> 2
                else -> 3
            }
            candidates += Candidate(
                priority = priority,
                sort = concept.label + state.skillType.name,
                id = concept.id.value,
                item = MemoryItemWire(
                    id = concept.id.value,
                    label = concept.label,
                    skill = state.skillType,
                    support = if (familiar) "FAMILIAR_IN_OBSERVED_USES" else "NEEDS_SUPPORT",
                    independentSuccesses = state.independentSuccessUnits,
                    stale = state.stale,
                    senseScope = state.senseScope.name,
                    limitation = "Только наблюдавшиеся употребления. Это не знание всех значений.",
                ),
                familiar = familiar,
            )
        }
        candidates.sortWith(compareBy({ it.priority }, { it.sort }, { it.id }))
        val kept = mutableListOf<Candidate>()
        val omitted = mutableListOf<String>()
        var revisit: String? = null
        for (candidate in candidates) {
            if (candidate.familiar && kept.count { it.familiar } >= 2) {
                omitted += "familiar:${candidate.id}"
                continue
            }
            if (!candidate.familiar && revisit == null && candidate.priority <= 1) revisit = candidate.id
            kept += candidate
        }
        val reports = observations.filter { it.language == language && it.evidenceMode == EvidenceMode.SELF_REPORTED }
            .sortedByDescending { it.createdAt }
            .take(4)
            .map {
                ReportedDifficultyWire(
                    label = it.conceptId?.let { id -> byId[id]?.label } ?: it.sourceEvidence.take(40),
                    scope = it.sourceEvidence.take(80),
                    uncertainty = "Прошлый самоотчёт, не доказанная ошибка и не постоянное незнание.",
                    sessionId = it.sessionId.value,
                )
            }
        val tendencyWires = tendencies(reducer.reduce(observations.filter { it.language == language }, emptySet()))
            .sortedWith(compareByDescending<com.tepmex.extensivereadingtutor.learner.TendencyReport> { it.established }.thenBy { it.key })
            .take(2)
            .map {
                TendencyWire(it.key, it.statement, it.supportCount, it.counterCount, it.sessionCount, it.established, it.limitation, it.example)
            }
        val packet = PrepareMemoryWire(
            relevantDifficulties = kept.filter { !it.familiar }.map { it.item },
            familiarChecks = kept.filter { it.familiar }.map { it.item },
            revisitCandidateId = revisit,
            tendencies = tendencyWires,
            recentQuestionTypes = recentTypes.take(6),
            supportProfile = profile.summary + " " + profile.limitation,
            reportedDifficulties = reports,
        )
        val fitted = fitMemory(packet)
        val continuityBytes = continuity.sumOf { it.text.toByteArray().size }
        return MemorySelection(
            fitted.packet,
            BudgetReport(
                memoryBytes = fitted.bytes,
                memoryBudgetBytes = config.memoryBudgetBytes,
                estimatedInputTokens = 0,
                estimatedOutputTokens = 0,
                estimateUncertain = true,
                selectedIds = fitted.selected,
                omittedIds = omitted + fitted.omitted + listOf("continuity-bytes:$continuityBytes"),
                cutoffReason = fitted.cutoff,
                builderVersion = "prepare-context-v2",
                contextWindowTokens = 0,
            ),
        )
    }

    private fun fitMemory(packet: PrepareMemoryWire): FittedMemory {
        var current = packet
        var cutoff: String? = null
        val omitted = mutableListOf<String>()
        fun size(value: PrepareMemoryWire) = wireJson.encodeToString(value).toByteArray(StandardCharsets.UTF_8).size
        while (size(current) > config.memoryBudgetBytes && current.tendencies.isNotEmpty()) {
            omitted += "tendency:${current.tendencies.last().key}"
            current = current.copy(tendencies = current.tendencies.dropLast(1))
            cutoff = "memory-bytes"
        }
        while (size(current) > config.memoryBudgetBytes && current.familiarChecks.isNotEmpty()) {
            omitted += "familiar:${current.familiarChecks.last().id}"
            current = current.copy(familiarChecks = current.familiarChecks.dropLast(1))
            cutoff = "memory-bytes"
        }
        while (size(current) > config.memoryBudgetBytes && current.reportedDifficulties.isNotEmpty()) {
            omitted += "report:${current.reportedDifficulties.last().label}"
            current = current.copy(reportedDifficulties = current.reportedDifficulties.dropLast(1))
            cutoff = "memory-bytes"
        }
        while (size(current) > config.memoryBudgetBytes && current.relevantDifficulties.isNotEmpty()) {
            omitted += current.relevantDifficulties.last().id
            current = current.copy(relevantDifficulties = current.relevantDifficulties.dropLast(1))
            cutoff = "memory-bytes"
        }
        val selected = current.relevantDifficulties.map { it.id } + current.familiarChecks.map { "familiar:${it.id}" } + current.tendencies.map { "tendency:${it.key}" }
        return FittedMemory(current, size(current), selected, omitted, cutoff)
    }
}

class EvaluateContextBuilder(private val config: LessonPolicyConfig = LessonPolicyConfig()) {
    fun build(
        fragment: BookFragment,
        language: String,
        preceding: String?,
        preferences: LearnerPreferences,
        introduction: String,
        questions: List<LessonQuestion>,
        submission: StudentSubmission,
        hints: List<HintPlan>,
        assistance: List<FrozenAssistance>,
        selections: List<FrozenSelection>,
        revealed: List<FrozenExplanation>,
        contextWindowTokens: Int,
    ): ContextResult<EvaluationContext> {
        val hintWires = if (hints.isNotEmpty()) {
            hints.map { hint ->
                ShownHintWire(
                    text = hint.text,
                    assistanceLevel = hint.assistanceLevel,
                    scopeQuestionIds = questions.filter { it.localId != null && it.localId in hint.targetQuestionLocalIds }.map { it.id.value },
                    scopeAll = hint.targetQuestionLocalIds.isEmpty(),
                )
            }
        } else {
            emptyList()
        }
        val input = EvaluateInputV2(
            fragment = fragment.toWire(),
            precedingText = preceding?.takeIf { it.isNotEmpty() },
            introduction = introduction,
            questions = questions.map { question ->
                EvalQuestionWire(
                    questionId = question.id.value,
                    type = question.type,
                    prompt = question.prompt,
                    targetSkill = question.targetSkill,
                    targetExpression = question.targetExpression,
                    purpose = question.purpose,
                    sourceQuotes = question.sourceAnchors.map { it.quote },
                    rubric = (question.rubric ?: com.tepmex.extensivereadingtutor.domain.QuestionRubric(
                        expectedMeaning = question.prompt,
                        requiredPoints = emptyList(),
                        acceptableAlternatives = emptyList(),
                        unsupportedInferences = emptyList(),
                        evidenceQuotes = question.sourceAnchors.map { it.quote },
                    )).let {
                        RubricWire(it.expectedMeaning, it.requiredPoints, it.acceptableAlternatives, it.unsupportedInferences, it.evidenceQuotes)
                    },
                    answer = submission.answers.firstOrNull { it.questionId == question.id }?.text.orEmpty(),
                )
            },
            shownHints = hintWires,
            assistanceEvents = assistance.map {
                AssistanceWire(it.level, it.scopeQuestionIds.map { id -> id.value }, it.scopeAll, it.scopeUnknown, it.selectionId?.value, it.text)
            },
            subjectiveDifficulty = submission.difficulty,
            wantsMoreExplanation = submission.wantsMoreExplanation,
            unclearNotes = submission.unclearNotes,
            freeComment = submission.freeComment,
            selections = selections.map { it.toWire() },
            revealedExplanations = revealed.map { RevealedExplanationWire(it.selectionId.value, it.selectionRevision, it.explanation) },
            conceptCatalog = Catalog.grammar.filter { it.language == language }.map { CatalogEntryWire(it.id, it.label, "GRAMMAR") },
            lessonPolicy = config.toWire(preferences),
            preferences = preferences.toWire(),
        )
        val system = PromptPack.system(PromptPack.EVALUATE, EVALUATE_SCHEMA_JSON)
        val user = wireJson.encodeToString(input)
        val estimate = estimateRequest(system, user)
        val outputEstimate = 600 + questions.size * 350 + selections.size * 320
        val report = BudgetReport(
            memoryBytes = 0,
            memoryBudgetBytes = config.memoryBudgetBytes,
            estimatedInputTokens = estimate,
            estimatedOutputTokens = outputEstimate,
            estimateUncertain = true,
            selectedIds = selections.map { it.id.value },
            omittedIds = emptyList(),
            cutoffReason = null,
            builderVersion = "evaluate-context-v2",
            contextWindowTokens = contextWindowTokens,
        )
        if (outputEstimate > config.evaluateMaxTokens || !fits(estimate, contextWindowTokens, config.evaluateMaxTokens)) {
            return ContextResult.Overflow(
                "Разбор не помещается в лимит модели. Сократите выделения или увеличьте лимит ответа. Черновик сохранён, неполный разбор не будет записан.",
                report,
            )
        }
        return ContextResult.Ready(EvaluationContext(input, user, system, report))
    }
}

fun requestFingerprint(model: String, temperature: Double, maxTokens: Int, system: String, user: String): String =
    sha256("$model|$temperature|$maxTokens|$system\n$user")

fun estimateRequest(system: String, user: String): Int {
    val bytes = (system + user).toByteArray(StandardCharsets.UTF_8).size
    return (bytes / 4).coerceAtLeast(1)
}

fun fits(inputTokens: Int, contextWindowTokens: Int, outputTokens: Int, safety: Int = 512): Boolean =
    inputTokens + outputTokens + safety <= contextWindowTokens

private data class MemorySelection(val packet: PrepareMemoryWire, val report: BudgetReport)
private data class FittedMemory(val packet: PrepareMemoryWire, val bytes: Int, val selected: List<String>, val omitted: List<String>, val cutoff: String?)

private fun LessonPolicyConfig.toWire(preferences: LearnerPreferences) = LessonPolicyWire(
    version = "policy-v2",
    minQuestions = minQuestions,
    maxQuestions = if (preferences.hintDepth == com.tepmex.extensivereadingtutor.domain.HintDepth.DETAILED) maxQuestionsWhenDeeper else maxQuestions,
    maxHints = maxHints,
    allowActiveProduction = allowActiveProduction,
    maxRevisitQuestions = 1,
    continuityNotesEnabled = continuityNotesEnabled,
    answerBeforeHintsMayRevealMeaning = false,
)

private fun LearnerPreferences.toWire() = PreferencesWire(
    studyLanguage = studyLanguage,
    explanationLanguage = explanationLanguage,
    declaredLevel = selfAssessedLevel,
    declaredLevelIsMeasured = false,
    difficulty = preferredDifficulty,
    explanationDepth = hintDepth,
)

private fun BookFragment.toWire() = FragmentWire(
    id = id.value,
    text = originalText,
    anchors = anchors.map { AnchorWire(it.blockId.value, it.startCodePoint, it.endCodePoint, it.quote) },
)

private fun FrozenSelection.toWire() = SelectionWire(
    id = id.value,
    revision = revision,
    quotedText = quotedText,
    anchors = sourceAnchors.map { AnchorWire(it.blockId?.value.orEmpty(), it.startCodePoint ?: 0, it.endCodePoint ?: 0, it.quote) },
    requestType = requestType,
    comment = comment,
    contextBefore = contextBefore,
    contextAfter = contextAfter,
    helpTiming = helpTiming.name,
)
