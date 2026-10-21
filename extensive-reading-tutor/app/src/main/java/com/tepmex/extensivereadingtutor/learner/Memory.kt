package com.tepmex.extensivereadingtutor.learner

import com.tepmex.extensivereadingtutor.books.locateInFragment
import com.tepmex.extensivereadingtutor.books.nfc
import com.tepmex.extensivereadingtutor.books.sha256
import com.tepmex.extensivereadingtutor.domain.AppClock
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.Concept
import com.tepmex.extensivereadingtutor.domain.ConceptId
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.EvidenceStrength
import com.tepmex.extensivereadingtutor.domain.IdGenerator
import com.tepmex.extensivereadingtutor.domain.KnowledgeStatus
import com.tepmex.extensivereadingtutor.domain.LearnerId
import com.tepmex.extensivereadingtutor.domain.LearnerKnowledgeState
import com.tepmex.extensivereadingtutor.domain.LearnerObservation
import com.tepmex.extensivereadingtutor.domain.ObservationId
import com.tepmex.extensivereadingtutor.domain.ObservationRetraction
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.StudentSubmission
import com.tepmex.extensivereadingtutor.domain.expectedOutcome
import com.tepmex.extensivereadingtutor.storage.TutorStore

data class ProposalInput(
    val questionId: com.tepmex.extensivereadingtutor.domain.QuestionId?,
    val conceptLabel: String?,
    val skillType: SkillType,
    val observationType: ObservationType,
    val outcome: Outcome,
    val studentEvidence: String,
    val sourceQuote: String,
    val interpretation: String,
    val confidence: Confidence,
    val payloadJson: String,
)

sealed interface PolicyDecision {
    data class Accept(
        val concept: Concept?,
        val studentEvidence: String,
        val sourceEvidence: String,
        val span: com.tepmex.extensivereadingtutor.domain.SourceSpan,
        val contentHash: String,
    ) : PolicyDecision

    data class Reject(val reason: String, val contentHash: String) : PolicyDecision
}

class ObservationPolicy {
    fun decide(
        input: ProposalInput,
        fragment: BookFragment,
        submission: StudentSubmission,
        language: String,
        sessionId: com.tepmex.extensivereadingtutor.domain.SessionId,
    ): PolicyDecision {
        val hash = proposalHash(sessionId.value, input)
        if (input.outcome != input.observationType.expectedOutcome()) {
            return PolicyDecision.Reject("outcome does not match observation type", hash)
        }
        if (input.interpretation.isBlank()) {
            return PolicyDecision.Reject("interpretation is blank", hash)
        }
        val located = locateInFragment(fragment, input.sourceQuote)
            ?: return PolicyDecision.Reject("source quote is not in the fragment", hash)
        val evidence = sliceOfAnswer(submission, input.questionId?.value, input.studentEvidence)
            ?: return PolicyDecision.Reject("student evidence is not in the stored answer", hash)
        val label = input.conceptLabel?.trim()?.let(::nfc)?.takeIf { it.isNotEmpty() }
        if (label != null && input.skillType in SURFACE_SKILLS && !nfc(fragment.originalText).contains(label)) {
            return PolicyDecision.Reject("concept label is not in the fragment", hash)
        }
        val concept = label?.let {
            Concept(ConceptId(conceptId(language, it)), language, it)
        }
        return PolicyDecision.Accept(
            concept = concept,
            studentEvidence = evidence,
            sourceEvidence = located.exact,
            span = located.span,
            contentHash = hash,
        )
    }

    companion object {
        val SURFACE_SKILLS = setOf(SkillType.VOCABULARY_RECOGNITION, SkillType.IDIOM_COMPREHENSION)

        fun conceptId(language: String, label: String): String = "$language:${nfc(label)}"

        fun proposalHash(sessionId: String, input: ProposalInput): String = sha256(
            listOf(
                sessionId,
                input.questionId?.value.orEmpty(),
                input.observationType.name,
                input.skillType.name,
                input.conceptLabel?.let(::nfc).orEmpty(),
                nfc(input.studentEvidence.trim()),
                nfc(input.sourceQuote.trim()),
            ).joinToString("|"),
        )
    }
}

class KnowledgeProjector {
    fun project(
        observations: List<LearnerObservation>,
        retractedIds: Set<ObservationId>,
    ): List<LearnerKnowledgeState> {
        val live = observations
            .filter { it.id !in retractedIds }
            .sortedWith(compareBy({ it.createdAt }, { it.sequence }))
        return live.groupBy { keyOf(it) }.map { (key, items) -> toState(key, items) }
            .sortedBy { it.id }
    }

    private data class Key(
        val learnerId: LearnerId,
        val language: String,
        val conceptId: ConceptId?,
        val diagnosticKey: String?,
        val skillType: SkillType,
    )

    private fun keyOf(item: LearnerObservation): Key = Key(
        learnerId = item.learnerId,
        language = item.language,
        conceptId = item.conceptId,
        diagnosticKey = if (item.conceptId == null) item.observationType.name else null,
        skillType = item.skillType,
    )

    private fun toState(key: Key, items: List<LearnerObservation>): LearnerKnowledgeState {
        var success = 0
        var weak = 0
        var error = 0
        var positivesSinceError = 0
        var seenError = false
        for (item in items) {
            when (item.outcome) {
                Outcome.POSITIVE -> {
                    success += 1
                    if (seenError) positivesSinceError += 1
                }
                Outcome.WEAK -> {
                    weak += 1
                    if (seenError) positivesSinceError = 0
                }
                Outcome.NEGATIVE -> {
                    error += 1
                    seenError = true
                    positivesSinceError = 0
                }
            }
        }
        val last = items.last()
        val tail = items.takeLast(3)
        val stable = tail.size == 3 &&
            tail.all { it.outcome == Outcome.POSITIVE } &&
            tail.map { it.sessionId }.toSet().size >= 2
        val status = when {
            stable -> KnowledgeStatus.STABLE
            success >= 2 && last.outcome == Outcome.POSITIVE && (!seenError || positivesSinceError >= 2) ->
                KnowledgeStatus.LIKELY_KNOWN
            error > 0 && success > 0 -> KnowledgeStatus.NEEDS_REVIEW
            else -> KnowledgeStatus.LEARNING
        }
        val strength = when {
            items.size >= 3 -> EvidenceStrength.HIGH
            items.size == 2 -> EvidenceStrength.MEDIUM
            else -> EvidenceStrength.LOW
        }
        val id = sha256(
            listOf(
                key.learnerId.value,
                key.language,
                key.conceptId?.value.orEmpty(),
                key.diagnosticKey.orEmpty(),
                key.skillType.name,
            ).joinToString("|"),
        )
        return LearnerKnowledgeState(
            id = id,
            learnerId = key.learnerId,
            language = key.language,
            conceptId = key.conceptId,
            diagnosticKey = key.diagnosticKey,
            skillType = key.skillType,
            status = status,
            successCount = success,
            weakCount = weak,
            errorCount = error,
            lastOutcome = last.outcome,
            lastObservedAt = last.createdAt,
            evidenceStrength = strength,
            observationIds = items.map { it.id },
        )
    }
}

class LearnerService(
    private val store: TutorStore,
    private val projector: KnowledgeProjectorV2 = KnowledgeProjectorV2(),
    private val clock: AppClock,
    private val ids: IdGenerator,
) {
    suspend fun retract(observationId: ObservationId, reason: String): AppResult<LearnerObservation> {
        if (reason.isBlank()) {
            return AppResult.Err(DomainError.LessonNotReady("Укажите причину отмены."))
        }
        return store.transaction { db ->
            val observation = db.observation(observationId)
                ?: return@transaction AppResult.Err(DomainError.LessonNotReady("Наблюдение не найдено."))
            if (db.retraction(observationId) == null) {
                db.insertRetraction(
                    ObservationRetraction(
                        id = ids.newId("retract"),
                        observationId = observationId,
                        reason = reason.trim(),
                        createdAt = clock.now(),
                    ),
                )
            }
            val session = db.session(observation.sessionId)
            if (session != null) {
                db.saveContinuityNotes(
                    session.bookId,
                    db.continuityNotes(session.bookId).filter { it.sessionId != observation.sessionId },
                )
            }
            replaceProjection(db, observation.learnerId)
            AppResult.Ok(db.observation(observationId)!!)
        }
    }

    suspend fun recompute(learnerId: LearnerId): List<LearnerKnowledgeState> = store.transaction { db ->
        replaceProjection(db, learnerId)
        db.knowledge(learnerId)
    }

    suspend fun knowledge(learnerId: LearnerId): List<LearnerKnowledgeState> =
        store.read { it.knowledge(learnerId) }

    suspend fun savePreferences(preferences: com.tepmex.extensivereadingtutor.domain.LearnerPreferences) {
        store.transaction { it.savePreferences(preferences) }
    }

    suspend fun preferences(learnerId: LearnerId): com.tepmex.extensivereadingtutor.domain.LearnerPreferences? =
        store.read { it.preferences(learnerId) }

    fun replaceProjection(db: com.tepmex.extensivereadingtutor.storage.StoreSession, learnerId: LearnerId) {
        val observations = db.observations(learnerId)
        val retracted = observations.mapNotNull { item ->
            db.retraction(item.id)?.observationId
        }.toSet()
        db.replaceKnowledge(learnerId, projector.project(observations, retracted, clock.now()))
    }
}

private fun sliceOfAnswer(
    submission: StudentSubmission,
    questionId: String?,
    evidence: String,
): String? {
    val needle = nfc(evidence).trim()
    if (needle.isEmpty()) return null
    val answers = if (questionId == null) {
        submission.answers
    } else {
        submission.answers.filter { it.questionId.value == questionId }
    }
    for (answer in answers) {
        val haystack = nfc(answer.text)
        val at = haystack.indexOf(needle)
        if (at >= 0) return haystack.substring(at, at + needle.length)
    }
    return null
}
