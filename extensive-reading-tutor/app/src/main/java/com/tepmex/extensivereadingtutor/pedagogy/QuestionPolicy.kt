package com.tepmex.extensivereadingtutor.pedagogy

import com.tepmex.extensivereadingtutor.books.QuoteLocate
import com.tepmex.extensivereadingtutor.books.locateQuote
import com.tepmex.extensivereadingtutor.domain.AssistanceLevel
import com.tepmex.extensivereadingtutor.domain.BookFragment
import com.tepmex.extensivereadingtutor.domain.HintDepth
import com.tepmex.extensivereadingtutor.domain.QuestionPurpose
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SourceSpan

data class AcceptedQuestion(
    val localId: String,
    val type: QuestionType,
    val prompt: String,
    val targetSkill: com.tepmex.extensivereadingtutor.domain.SkillType,
    val targetExpression: String?,
    val conceptRef: String?,
    val purpose: QuestionPurpose,
    val anchors: List<SourceSpan>,
    val rubric: com.tepmex.extensivereadingtutor.domain.QuestionRubric,
)

data class AcceptedHint(
    val localId: String,
    val text: String,
    val targetLocalIds: List<String>,
    val assistanceLevel: AssistanceLevel,
    val scopeAll: Boolean,
)

sealed interface PrepareDecision {
    data class Accept(val questions: List<AcceptedQuestion>, val hints: List<AcceptedHint>) : PrepareDecision
    data class Reject(val reason: String) : PrepareDecision
}

class QuestionPolicy(private val config: LessonPolicyConfig = LessonPolicyConfig()) {
    fun decide(
        output: PrepareOutputV2,
        fragment: BookFragment,
        depth: HintDepth,
        language: String,
        allowedConceptIds: Set<String> = emptySet(),
    ): PrepareDecision {
        val maxQuestions = if (depth == HintDepth.DETAILED) config.maxQuestionsWhenDeeper else config.maxQuestions
        if (output.questions.size !in config.minQuestions..maxQuestions) {
            return PrepareDecision.Reject("Нужно от ${config.minQuestions} до $maxQuestions вопросов.")
        }
        if (output.hints.size > config.maxHints) {
            return PrepareDecision.Reject("Подсказок до ответа не больше ${config.maxHints}.")
        }
        if (output.introduction.isBlank()) return PrepareDecision.Reject("Пустое введение.")
        val localIds = output.questions.map { it.localId }
        if (localIds.any { it.isBlank() } || localIds.toSet().size != localIds.size) {
            return PrepareDecision.Reject("localId вопросов должны быть уникальными.")
        }
        if (output.questions.none { it.purpose != QuestionPurpose.REVISIT }) {
            return PrepareDecision.Reject("Хотя бы один вопрос должен быть о текущем фрагменте.")
        }
        if (output.questions.count { it.purpose == QuestionPurpose.REVISIT } > 1) {
            return PrepareDecision.Reject("Не больше одного вопроса-повторения.")
        }
        val questions = mutableListOf<AcceptedQuestion>()
        for (question in output.questions) {
            if (!config.allowActiveProduction && question.type == QuestionType.ACTIVE_PRODUCTION) {
                return PrepareDecision.Reject("Продуктивное задание не запрошено политикой.")
            }
            if (question.prompt.isBlank()) return PrepareDecision.Reject("Пустой вопрос.")
            if (question.sourceQuotes.isEmpty()) return PrepareDecision.Reject("У вопроса нет цитаты.")
            if (question.rubric.evidenceQuotes.isEmpty()) {
                return PrepareDecision.Reject("У рубрики нет цитаты из фрагмента.")
            }
            val anchors = mutableListOf<SourceSpan>()
            for (quote in question.sourceQuotes) {
                val located = locateQuote(fragment, quote)
                when (located.kind) {
                    QuoteLocate.MISSING -> return PrepareDecision.Reject("Цитата вопроса не входит во фрагмент.")
                    QuoteLocate.AMBIGUOUS -> return PrepareDecision.Reject("Цитата вопроса встречается больше одного раза. Нужна однозначная опора.")
                    QuoteLocate.UNIQUE -> anchors += located.span!!
                }
            }
            for (quote in question.rubric.evidenceQuotes) {
                if (locateQuote(fragment, quote).kind != QuoteLocate.UNIQUE) {
                    return PrepareDecision.Reject("Цитата рубрики не является однозначной цитатой фрагмента.")
                }
            }
            if (question.rubric.expectedMeaning.isBlank()) return PrepareDecision.Reject("У рубрики нет ожидаемого смысла.")
            val concept = question.conceptRef?.takeIf { it.isNotBlank() }
            if (concept != null && concept !in allowedConceptIds && Catalog.grammarById(concept)?.language != language) {
                return PrepareDecision.Reject("Модель прислала неизвестный conceptRef.")
            }
            questions += AcceptedQuestion(
                localId = question.localId,
                type = question.type,
                prompt = question.prompt.trim(),
                targetSkill = question.targetSkill,
                targetExpression = question.targetExpression?.trim()?.takeIf { it.isNotEmpty() },
                conceptRef = concept,
                purpose = question.purpose,
                anchors = anchors,
                rubric = com.tepmex.extensivereadingtutor.domain.QuestionRubric(
                    expectedMeaning = question.rubric.expectedMeaning.trim(),
                    requiredPoints = question.rubric.requiredPoints,
                    acceptableAlternatives = question.rubric.acceptableAlternatives,
                    unsupportedInferences = question.rubric.unsupportedInferences,
                    evidenceQuotes = question.rubric.evidenceQuotes,
                ),
            )
        }
        val hints = mutableListOf<AcceptedHint>()
        for (hint in output.hints) {
            if (hint.text.isBlank()) return PrepareDecision.Reject("Пустая подсказка.")
            if (hint.assistanceLevel == AssistanceLevel.ANSWER_GIVEN || hint.assistanceLevel == AssistanceLevel.NONE_RECORDED || hint.assistanceLevel == AssistanceLevel.UNKNOWN) {
                return PrepareDecision.Reject("До ответа нельзя отдавать готовый ответ и нельзя помечать помощь как неизвестную.")
            }
            if (hint.targetQuestionLocalIds.any { it !in localIds }) {
                return PrepareDecision.Reject("Подсказка ссылается на неизвестный вопрос.")
            }
            hints += AcceptedHint(
                localId = hint.localId,
                text = hint.text.trim(),
                targetLocalIds = hint.targetQuestionLocalIds,
                assistanceLevel = hint.assistanceLevel,
                scopeAll = hint.targetQuestionLocalIds.isEmpty(),
            )
        }
        return PrepareDecision.Accept(questions, hints)
    }
}
