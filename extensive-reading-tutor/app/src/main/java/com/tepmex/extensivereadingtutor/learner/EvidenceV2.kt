package com.tepmex.extensivereadingtutor.learner

import com.tepmex.extensivereadingtutor.books.QuoteLocate
import com.tepmex.extensivereadingtutor.books.locateQuote
import com.tepmex.extensivereadingtutor.books.nfc
import com.tepmex.extensivereadingtutor.books.sha256
import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.ConceptKind
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.EvidenceMode
import com.tepmex.extensivereadingtutor.domain.EvidenceSource
import com.tepmex.extensivereadingtutor.domain.EvidenceStrength
import com.tepmex.extensivereadingtutor.domain.FrozenAssistance
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerId
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.LessonQuestion
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.QuestionReview
import com.tepmex.extensivereadingtutor.domain.SelectionId
import com.tepmex.extensivereadingtutor.domain.SenseScope
import com.tepmex.extensivereadingtutor.domain.SessionId
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.SourceSpan
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.expectedOutcome
import com.tepmex.extensivereadingtutor.pedagogy.Catalog
import com.tepmex.extensivereadingtutor.pedagogy.LessonPolicyConfig
import com.tepmex.extensivereadingtutor.pedagogy.ObservationOutputV2
import com.tepmex.extensivereadingtutor.pedagogy.labelInText
import com.tepmex.extensivereadingtutor.pedagogy.skillsCompatible
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class V2Proposal(
    val input: ObservationOutputV2,
    val payloadJson: String,
)

sealed interface V2Decision {
    data class Accept(
        val concept: Concept?,
        val diagnosticKey: String?,
        val studentEvidence: String,
        val sourceEvidence: String,
        val span: SourceSpan,
        val assistance: AssistanceLevel,
        val eligibleForIndependentSuccess: Boolean,
        val evidenceUnitId: String,
        val contextId: String,
        val contentHash: String,
        val selectionId: SelectionId?,
        val selectionRevision: Int?,
    ) : V2Decision

    data class Reject(val reason: String, val contentHash: String) : V2Decision
}

class ObservationPolicyV2(private val config: LessonPolicyConfig = LessonPolicyConfig()) {
    fun decide(
        proposal: V2Proposal,
        fragment: BookFragment,
        submission: StudentSubmission,
        questions: List<LessonQuestion>,
        reviews: List<QuestionReview>,
        assistance: List<FrozenAssistance>,
        language: String,
        sessionId: SessionId,
        selections: List<com.tepmex.extensivereadingtutor.domain.FrozenSelection>,
    ): V2Decision {
        val input = proposal.input
        val hash = sha256(proposal.payloadJson + "|" + sessionId.value)
        if (input.outcome != input.observationType.expectedOutcome() && input.evidenceMode != EvidenceMode.SELF_REPORTED) {
            return V2Decision.Reject("outcome does not match observation type", hash)
        }
        if (input.interpretation.isBlank()) return V2Decision.Reject("interpretation is blank", hash)
        if (input.questionId != null && input.evidenceSource == EvidenceSource.ANSWER) {
            val known = questions.any { it.id.value == input.questionId }
            if (!known) return V2Decision.Reject("unknown questionId", hash)
        }
        if (input.confidence == Confidence.LOW || input.evidenceMode == EvidenceMode.AMBIGUOUS) {
            return V2Decision.Reject("low confidence or ambiguous evidence is not a mastery update", hash)
        }
        val located = locateQuote(fragment, input.sourceQuote)
        if (located.kind != QuoteLocate.UNIQUE) {
            return V2Decision.Reject(
                if (located.kind == QuoteLocate.AMBIGUOUS) "ambiguous source quote" else "source quote is not in the fragment",
                hash,
            )
        }
        val field = evidenceField(input, submission, selections)
            ?: return V2Decision.Reject("student evidence is not in the stored field", hash)
        if (field.second == input.sourceQuote.trim() && input.evidenceSource == EvidenceSource.SELECTION_COMMENT) {
            return V2Decision.Reject("book quote is not learner evidence", hash)
        }
        if (nfc(field.second) == nfc(selections.firstOrNull { it.id.value == input.selectionId }?.quotedText.orEmpty()) &&
            input.evidenceSource == EvidenceSource.SELECTION_COMMENT
        ) {
            return V2Decision.Reject("book quote is not learner evidence", hash)
        }
        val question = input.questionId?.let { id -> questions.firstOrNull { it.id.value == id } }
        if (input.questionId != null && question == null) return V2Decision.Reject("unknown questionId", hash)
        val review = question?.let { item -> reviews.firstOrNull { it.questionId == item.id } }
        if (input.evidenceSource == EvidenceSource.ANSWER) {
            if (review == null) return V2Decision.Reject("answer evidence needs a review", hash)
            if (review.verdict == AnswerVerdict.UNANSWERED || review.verdict == AnswerVerdict.NOT_ASSESSABLE || !review.evidenceSufficient) {
                return V2Decision.Reject("this review cannot support a mastery update", hash)
            }
            if (input.observationType == ObservationType.UNKNOWN_VOCABULARY && review.verdict == AnswerVerdict.UNANSWERED) {
                return V2Decision.Reject("empty answer is not unknown vocabulary", hash)
            }
        }
        if ((review?.verdict == AnswerVerdict.UNANSWERED || review?.verdict == AnswerVerdict.NOT_ASSESSABLE) && input.outcome == Outcome.NEGATIVE) {
            return V2Decision.Reject("unanswered or not assessable creates no negative mastery evidence", hash)
        }
        if (submission.answers.any { it.text.isBlank() && it.questionId.value == input.questionId } &&
            input.observationType == ObservationType.UNKNOWN_VOCABULARY
        ) {
            return V2Decision.Reject("empty answer is not unknown vocabulary", hash)
        }
        val target = question?.targetSkill ?: input.skillType
        if (!skillsCompatible(target, input.skillType, field.second, language)) {
            return V2Decision.Reject("skill is not compatible with the task", hash)
        }
        if (input.diagnosticKey != null && input.diagnosticKey !in Catalog.diagnostics) {
            return V2Decision.Reject("unknown diagnostic key", hash)
        }
        val resolvedConcept = resolveConcept(input, language, fragment)
        if (input.conceptRef != null && resolvedConcept == null) {
            return V2Decision.Reject("foreign concept id", hash)
        }
        if (resolvedConcept == null && input.conceptLabel.isNullOrBlank() && input.diagnosticKey == null &&
            input.evidenceMode == EvidenceMode.DEMONSTRATED
        ) {
            return V2Decision.Reject("no concept or diagnostic", hash)
        }
        if (input.conceptLabel != null && input.skillType in ObservationPolicy.SURFACE_SKILLS &&
            !labelInText(language, fragment.originalText, input.conceptLabel)
        ) {
            return V2Decision.Reject("concept label is not in the fragment", hash)
        }
        if (helpRequestOnly(field.second) && input.evidenceMode != EvidenceMode.SELF_REPORTED) {
            return V2Decision.Reject("a help request is only a self-report", hash)
        }
        if (input.evidenceMode == EvidenceMode.SELF_REPORTED && input.outcome == Outcome.NEGATIVE) {
            return V2Decision.Reject("self-report is not a demonstrated error", hash)
        }
        val merged = mergeAssistance(
            recorded = recordedAssistance(question?.id, assistance),
            assessed = input.assistanceAssessment,
        )
        val eligible = eligibleIndependent(input, review, merged)
        val conceptKey = resolvedConcept?.id?.value ?: input.diagnosticKey.orEmpty()
        val unitId = sha256(listOf(sessionId.value, conceptKey, input.skillType.name).joinToString("|"))
        val contextId = sha256(listOf(language, nfc(located.exact!!), located.span?.blockId?.value.orEmpty(), located.span?.startCodePoint?.toString().orEmpty(), located.span?.endCodePoint?.toString().orEmpty()).joinToString("|"))
        return V2Decision.Accept(
            concept = resolvedConcept,
            diagnosticKey = if (resolvedConcept == null) input.diagnosticKey else null,
            studentEvidence = field.second,
            sourceEvidence = located.exact,
            span = located.span!!,
            assistance = merged,
            eligibleForIndependentSuccess = eligible,
            evidenceUnitId = unitId,
            contextId = contextId,
            contentHash = hash,
            selectionId = input.selectionId?.takeIf { it.isNotBlank() }?.let(::SelectionId),
            selectionRevision = input.selectionRevision,
        )
    }

    private fun resolveConcept(input: ObservationOutputV2, language: String, fragment: BookFragment): Concept? {
        val ref = input.conceptRef?.takeIf { it.isNotBlank() }
        if (ref != null) {
            val grammar = Catalog.grammarById(ref)
            if (grammar != null) {
                if (grammar.language != language) return null
                return Concept(ConceptId(grammar.id), language, grammar.label, ConceptKind.GRAMMAR, SenseScope.CONTEXTUAL_ONLY, null)
            }
            if (!ref.startsWith("$language:")) return null
            if (!Catalog.knownConceptId(language, ref)) return null
            val label = input.conceptLabel?.let(::nfc)?.takeIf { it.isNotEmpty() } ?: ref.substringAfterLast(':')
            val kind = when {
                ref.contains(":idiom:") -> ConceptKind.IDIOM
                ref.contains(":grammar-candidate:") -> ConceptKind.GRAMMAR
                else -> ConceptKind.LEXICAL
            }
            return Concept(ConceptId(ref), language, label, kind, SenseScope.CONTEXTUAL_ONLY, null)
        }
        val label = input.conceptLabel?.trim()?.let(::nfc)?.takeIf { it.isNotEmpty() } ?: return null
        if (!labelInText(language, fragment.originalText, label) && input.skillType in ObservationPolicy.SURFACE_SKILLS) return null
        if (input.skillType == SkillType.GRAMMAR_COMPREHENSION) {
            val known = Catalog.grammarByAlias(language, label)
            if (known != null) {
                return Concept(ConceptId(known.id), language, known.label, ConceptKind.GRAMMAR, SenseScope.CONTEXTUAL_ONLY, null)
            }
            return Concept(ConceptId(Catalog.surfaceId(language, ConceptKind.GRAMMAR, label)), language, label, ConceptKind.GRAMMAR, SenseScope.CONTEXTUAL_ONLY, null)
        }
        val kind = Catalog.kindForSkill(input.skillType)
        return Concept(ConceptId(Catalog.surfaceId(language, kind, label)), language, label, kind, SenseScope.CONTEXTUAL_ONLY, null)
    }
}

private fun evidenceField(
    input: ObservationOutputV2,
    submission: StudentSubmission,
    selections: List<com.tepmex.extensivereadingtutor.domain.FrozenSelection>,
): Pair<EvidenceSource, String>? {
    val needle = nfc(input.studentEvidence).trim()
    if (needle.isEmpty()) return null
    val haystack = when (input.evidenceSource) {
        EvidenceSource.ANSWER -> {
            val answers = if (input.questionId == null) submission.answers else submission.answers.filter { it.questionId.value == input.questionId }
            answers.map { nfc(it.text) }
        }
        EvidenceSource.UNCLEAR_NOTES -> listOfNotNull(submission.unclearNotes?.let(::nfc))
        EvidenceSource.FREE_COMMENT -> listOfNotNull(submission.freeComment?.let(::nfc))
        EvidenceSource.SELECTION_COMMENT -> selections.filter { input.selectionId == null || it.id.value == input.selectionId }
            .mapNotNull { it.comment?.let(::nfc) }
    }
    for (text in haystack) {
        val at = text.indexOf(needle)
        if (at >= 0) return input.evidenceSource to text.substring(at, at + needle.length)
    }
    return null
}

private fun helpRequestOnly(comment: String): Boolean {
    val normalized = nfc(comment).trim().lowercase()
    return normalized in setOf("не понимаю", "не понял", "не поняла", "не знаю", "?", "i don't understand", "i do not understand")
}

fun recordedAssistance(questionId: QuestionId?, events: List<FrozenAssistance>): AssistanceLevel {
    val matched = events.filter { event ->
        when {
            questionId == null -> event.scopeAll || event.scopeUnknown
            event.scopeAll || event.scopeUnknown -> true
            questionId in event.scopeQuestionIds -> true
            else -> false
        }
    }
    if (matched.isEmpty()) return AssistanceLevel.NONE_RECORDED
    return matched.map { event -> if (event.scopeUnknown) AssistanceLevel.UNKNOWN else event.level }
        .fold(AssistanceLevel.NONE_RECORDED) { acc, level -> mergeAssistance(acc, level) }
}

fun mergeAssistance(recorded: AssistanceLevel, assessed: AssistanceLevel): AssistanceLevel {
    val severe = maxOf(assistanceSeverity(recorded), assistanceSeverity(assessed))
    val fromSeverity = AssistanceLevel.entries.first { assistanceSeverity(it) == severe }
    if (recorded == AssistanceLevel.UNKNOWN || assessed == AssistanceLevel.UNKNOWN) {
        if (severe >= assistanceSeverity(AssistanceLevel.MEANING_GIVEN)) return fromSeverity
        if (severe >= assistanceSeverity(AssistanceLevel.SCAFFOLD)) return AssistanceLevel.SCAFFOLD
        return AssistanceLevel.UNKNOWN
    }
    return fromSeverity
}

fun assistanceSeverity(level: AssistanceLevel): Int = when (level) {
    AssistanceLevel.NONE_RECORDED -> 0
    AssistanceLevel.UNKNOWN -> 1
    AssistanceLevel.SCAFFOLD -> 2
    AssistanceLevel.MEANING_GIVEN -> 3
    AssistanceLevel.ANSWER_GIVEN -> 4
}

fun eligibleIndependent(input: ObservationOutputV2, review: QuestionReview?, assistance: AssistanceLevel): Boolean {
    if (input.evidenceMode != EvidenceMode.DEMONSTRATED) return false
    if (input.outcome != Outcome.POSITIVE) return false
    if (input.confidence == Confidence.LOW) return false
    if (review == null || !review.evidenceSufficient) return false
    if (review.verdict != AnswerVerdict.CORRECT) return false
    return assistance == AssistanceLevel.NONE_RECORDED
}

enum class UnitKind { INDEPENDENT_POSITIVE, ASSISTED_POSITIVE, ERROR, WEAK, AMBIGUOUS, SELF_REPORT, IGNORED }

data class EvidenceUnit(
    val id: String,
    val learnerId: LearnerId,
    val language: String,
    val conceptId: ConceptId?,
    val diagnosticKey: String?,
    val skillType: SkillType,
    val senseScope: SenseScope,
    val kind: UnitKind,
    val contextId: String,
    val sessionId: SessionId,
    val at: Instant,
    val studyDate: LocalDate?,
    val observationIds: List<ObservationId>,
)

class EvidenceUnitReducer {
    fun reduce(observations: List<LearnerObservation>, retracted: Set<ObservationId>): List<EvidenceUnit> {
        val live = observations.filter { it.id !in retracted }.sortedWith(compareBy({ it.createdAt }, { it.sequence }))
        return live.groupBy { keyOf(it) }.mapNotNull { (_, items) -> toUnit(items) }
    }

    private fun keyOf(item: LearnerObservation): String = listOf(
        item.sessionId.value,
        item.conceptId?.value ?: item.diagnosticKey.orEmpty(),
        item.skillType.name,
    ).joinToString("|")

    private fun toUnit(items: List<LearnerObservation>): EvidenceUnit? {
        val first = items.first()
        val demonstrated = items.filter { it.evidenceMode == EvidenceMode.DEMONSTRATED && it.confidence != Confidence.LOW }
        val outcomes = demonstrated.map { it.outcome }.toSet()
        val kind = when {
            items.any { it.evidenceMode == EvidenceMode.AMBIGUOUS } -> UnitKind.AMBIGUOUS
            Outcome.POSITIVE in outcomes && Outcome.NEGATIVE in outcomes -> UnitKind.AMBIGUOUS
            items.all { it.evidenceMode == EvidenceMode.SELF_REPORTED } -> UnitKind.SELF_REPORT
            items.any { it.confidence == Confidence.LOW } && items.none { it.confidence != Confidence.LOW && it.evidenceMode == EvidenceMode.DEMONSTRATED } -> UnitKind.IGNORED
            items.any { it.eligibleForIndependentSuccess && it.outcome == Outcome.POSITIVE } -> UnitKind.INDEPENDENT_POSITIVE
            items.any { it.outcome == Outcome.POSITIVE && it.evidenceMode == EvidenceMode.DEMONSTRATED } -> UnitKind.ASSISTED_POSITIVE
            items.any { it.outcome == Outcome.NEGATIVE && it.evidenceMode == EvidenceMode.DEMONSTRATED } -> UnitKind.ERROR
            items.any { it.outcome == Outcome.WEAK } -> UnitKind.WEAK
            else -> UnitKind.IGNORED
        }
        if (kind == UnitKind.IGNORED) return null
        return EvidenceUnit(
            id = first.evidenceUnitId ?: sha256(keyOf(first)),
            learnerId = first.learnerId,
            language = first.language,
            conceptId = first.conceptId,
            diagnosticKey = first.diagnosticKey,
            skillType = first.skillType,
            senseScope = first.senseScope,
            kind = kind,
            contextId = first.contextId ?: sha256(nfc(first.sourceEvidence)),
            sessionId = first.sessionId,
            at = items.maxOf { it.createdAt },
            studyDate = studyDate(items.maxOf { it.createdAt }, items.firstNotNullOfOrNull { it.timeZone }),
            observationIds = items.map { it.id },
        )
    }
}

fun studyDate(instant: Instant, zone: String?): LocalDate? {
    if (zone.isNullOrBlank()) return null
    val id = try {
        ZoneId.of(zone)
    } catch (_: Exception) {
        return null
    }
    return instant.atZone(id).toLocalDate()
}

class KnowledgeProjectorV2(private val config: LessonPolicyConfig = LessonPolicyConfig()) {
    private val reducer = EvidenceUnitReducer()

    fun project(observations: List<LearnerObservation>, retractedIds: Set<ObservationId>, now: Instant = Instant.EPOCH): List<LearnerKnowledgeState> {
        val units = reducer.reduce(observations, retractedIds)
        return units.groupBy { rowKey(it) }.mapNotNull { (key, grouped) -> toState(key, grouped, observations, retractedIds, now) }
            .sortedBy { it.id }
    }

    private data class RowKey(
        val learnerId: LearnerId,
        val language: String,
        val conceptId: ConceptId?,
        val diagnosticKey: String?,
        val skillType: SkillType,
        val senseScope: SenseScope,
    )

    private fun rowKey(unit: EvidenceUnit) = RowKey(
        unit.learnerId,
        unit.language,
        unit.conceptId,
        if (unit.conceptId == null) unit.diagnosticKey else null,
        unit.skillType,
        unit.senseScope,
    )

    private fun toState(
        key: RowKey,
        units: List<EvidenceUnit>,
        observations: List<LearnerObservation>,
        retractedIds: Set<ObservationId>,
        now: Instant,
    ): LearnerKnowledgeState? {
        val ordered = units.sortedBy { it.at }
        val mastery = ordered.filter { it.kind == UnitKind.INDEPENDENT_POSITIVE || it.kind == UnitKind.ERROR }
        val independent = ordered.filter { it.kind == UnitKind.INDEPENDENT_POSITIVE }
        val assisted = ordered.filter { it.kind == UnitKind.ASSISTED_POSITIVE }
        val errors = ordered.filter { it.kind == UnitKind.ERROR }
        if (independent.isEmpty() && assisted.isEmpty() && errors.isEmpty()) return null
        val lastError = errors.maxByOrNull { it.at }
        val afterError = if (lastError == null) independent else independent.filter { !it.at.isBefore(lastError.at) && it.id != lastError.id && it.at > lastError.at }
        val contexts = independent.map { it.contextId }.toSet()
        val days = independent.mapNotNull { it.studyDate }.toSet()
        val spanOk = if (independent.size >= 2) {
            Duration.between(independent.minOf { it.at }, independent.maxOf { it.at }) >= Duration.ofDays(config.stableSpanDays)
        } else {
            false
        }
        val tailPositive = mastery.size >= 3 && mastery.takeLast(3).all { it.kind == UnitKind.INDEPENDENT_POSITIVE }
        val stable = independent.size >= 3 && contexts.size >= 3 && days.size >= 2 && spanOk && tailPositive
        val likely = !stable && independent.size >= 2 && contexts.size >= 2 && afterError.size >= 2
        val status = when {
            stable -> KnowledgeStatus.STABLE
            likely -> KnowledgeStatus.LIKELY_KNOWN
            errors.isNotEmpty() && independent.isNotEmpty() -> KnowledgeStatus.NEEDS_REVIEW
            else -> KnowledgeStatus.LEARNING
        }
        val reason = when {
            status == KnowledgeStatus.LEARNING && independent.isEmpty() && errors.isNotEmpty() -> "пока затрудняется"
            else -> ""
        }
        val raw = observations.filter { item ->
            item.id !in retractedIds && item.learnerId == key.learnerId && item.language == key.language &&
                item.skillType == key.skillType && item.conceptId == key.conceptId &&
                (key.conceptId != null || item.diagnosticKey == key.diagnosticKey)
        }
        val lastCheck = ordered.maxOf { it.at }
        val stale = Duration.between(lastCheck, now).toDays() >= config.staleAfterDays
        val id = sha256(listOf(key.learnerId.value, key.language, key.conceptId?.value.orEmpty(), key.diagnosticKey.orEmpty(), key.skillType.name, key.senseScope.name).joinToString("|"))
        return LearnerKnowledgeState(
            id = id,
            learnerId = key.learnerId,
            language = key.language,
            conceptId = key.conceptId,
            diagnosticKey = key.diagnosticKey,
            skillType = key.skillType,
            status = status,
            successCount = raw.count { it.outcome == Outcome.POSITIVE },
            weakCount = raw.count { it.outcome == Outcome.WEAK },
            errorCount = raw.count { it.outcome == Outcome.NEGATIVE },
            lastOutcome = raw.maxByOrNull { it.sequence }?.outcome,
            lastObservedAt = raw.maxByOrNull { it.sequence }?.createdAt,
            evidenceStrength = when {
                independent.size >= 3 -> EvidenceStrength.HIGH
                independent.size == 2 -> EvidenceStrength.MEDIUM
                else -> EvidenceStrength.LOW
            },
            observationIds = raw.map { it.id },
            independentSuccessUnits = independent.size,
            assistedSuccessUnits = assisted.size,
            errorUnits = errors.size,
            distinctContexts = contexts.size,
            distinctStudyDays = days.size,
            lastIndependentSuccessAt = independent.maxByOrNull { it.at }?.at,
            lastErrorAt = lastError?.at,
            evidenceIds = ordered.map { it.id },
            projectorVersion = "projector-v2",
            statusReason = reason,
            stale = stale,
            senseScope = key.senseScope,
        )
    }
}

data class TendencyReport(
    val key: String,
    val supportCount: Int,
    val counterCount: Int,
    val sessionCount: Int,
    val established: Boolean,
    val statement: String,
    val limitation: String,
    val example: String?,
)

fun tendencies(units: List<EvidenceUnit>, config: LessonPolicyConfig = LessonPolicyConfig()): List<TendencyReport> {
    return units.filter { it.diagnosticKey in Catalog.diagnostics }.groupBy { it.diagnosticKey!! }.map { (key, grouped) ->
        val support = grouped.count { it.kind == UnitKind.ERROR }
        val counter = grouped.count { it.kind == UnitKind.INDEPENDENT_POSITIVE || it.kind == UnitKind.ASSISTED_POSITIVE }
        val sessions = grouped.map { it.sessionId }.toSet().size
        val established = grouped.count { it.kind == UnitKind.ERROR || it.kind == UnitKind.INDEPENDENT_POSITIVE } >= config.tendencyUnits && sessions >= config.tendencySessions
        val label = if (established) "повторяющийся паттерн" else "единичное наблюдение"
        TendencyReport(
            key = key,
            supportCount = support,
            counterCount = counter,
            sessionCount = sessions,
            established = established,
            statement = "$label: $key",
            limitation = "Эвристика по ${grouped.size} units, не измеренный уровень.",
            example = null,
        )
    }.sortedBy { it.key }
}

fun readingProfile(units: List<EvidenceUnit>): com.tepmex.extensivereadingtutor.domain.ReadingProfile {
    val reading = units.filter {
        it.skillType != SkillType.ACTIVE_PRODUCTION && it.kind in setOf(UnitKind.INDEPENDENT_POSITIVE, UnitKind.ASSISTED_POSITIVE, UnitKind.ERROR)
    }.sortedBy { it.at }
    val window = reading.takeLast(10)
    val independent = window.count { it.kind == UnitKind.INDEPENDENT_POSITIVE }
    val assisted = window.count { it.kind == UnitKind.ASSISTED_POSITIVE }
    val difficulties = window.count { it.kind == UnitKind.ERROR }
    val mixedProduction = units.any { it.skillType == SkillType.ACTIVE_PRODUCTION }
    val comparable = window.size >= 4 && !mixedProduction
    val summary = if (window.isEmpty()) {
        "Пока нет пригодных попыток чтения."
    } else {
        "В последних ${window.size} пригодных заданиях $independent самостоятельных пониманий, $assisted с помощью, $difficulties затруднений."
    }
    val limitation = when {
        window.isEmpty() -> "Выборка пустая. Это не уровень языка."
        !comparable -> "Сравнение неинтерпретируемо: слишком мало попыток или смешаны разные умения. Это не рост и не CEFR/HSK."
        else -> "Описание только чтения. Это не уровень языка и не прогноз."
    }
    return com.tepmex.extensivereadingtutor.domain.ReadingProfile(
        sampleSize = window.size,
        independentUnderstandings = independent,
        assistedUnderstandings = assisted,
        difficulties = difficulties,
        from = window.minOfOrNull { it.at },
        to = window.maxOfOrNull { it.at },
        comparable = comparable,
        summary = summary,
        limitation = limitation,
    )
}
