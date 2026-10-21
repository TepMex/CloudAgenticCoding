package com.tepmex.extensivereadingtutor.llm

import com.tepmex.extensivereadingtutor.domain.AnswerVerdict
import com.tepmex.extensivereadingtutor.domain.AppResult
import com.tepmex.extensivereadingtutor.domain.Confidence
import com.tepmex.extensivereadingtutor.domain.DomainError
import com.tepmex.extensivereadingtutor.domain.LearnerPreferences
import com.tepmex.extensivereadingtutor.domain.LlmCapabilities
import com.tepmex.extensivereadingtutor.domain.LlmFailureKind
import com.tepmex.extensivereadingtutor.domain.ObservationType
import com.tepmex.extensivereadingtutor.domain.Outcome
import com.tepmex.extensivereadingtutor.domain.QuestionId
import com.tepmex.extensivereadingtutor.domain.QuestionType
import com.tepmex.extensivereadingtutor.domain.SkillType
import com.tepmex.extensivereadingtutor.domain.TokenUsage
import com.tepmex.extensivereadingtutor.pedagogy.MemoryPacket
import com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy
import com.tepmex.extensivereadingtutor.pedagogy.Prompts
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

data class LlmMessage(val role: String, val content: String)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double,
    val maxOutputTokens: Int,
    val jsonObjectMode: Boolean,
    val timeoutMillis: Long,
    val extraBodyJson: String = "",
    val purpose: String = "",
    val jsonSchema: String? = null,
    val jsonSchemaName: String? = null,
)

data class LlmCallSettings(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_SECONDS * 1000,
    val extraBodyJson: String = "",
)

fun interface LlmCallPolicy {
    suspend fun current(): LlmCallSettings
}

data class LlmResponse(
    val content: String,
    val usage: TokenUsage?,
    val finishReason: String?,
)

interface LlmProvider {
    val capabilities: LlmCapabilities
    suspend fun generate(request: LlmRequest): AppResult<LlmResponse>
}

fun interface Sleeper {
    suspend fun sleep(millis: Long)
}

class RetryingCalls(private val sleeper: Sleeper, private val maxAttempts: Int = 3) {
    suspend fun generate(provider: LlmProvider, request: LlmRequest): AppResult<LlmResponse> {
        var last: DomainError = DomainError.LlmFailed(LlmFailureKind.NETWORK, "Модель не ответила.")
        repeat(maxAttempts) { attempt ->
            when (val result = provider.generate(request)) {
                is AppResult.Ok -> return result
                is AppResult.Err -> {
                    last = result.error
                    val kind = (result.error as? DomainError.LlmFailed)?.kind
                    val retry = kind == LlmFailureKind.TIMEOUT ||
                        kind == LlmFailureKind.RATE_LIMIT ||
                        kind == LlmFailureKind.NETWORK
                    if (!retry || attempt == maxAttempts - 1) return result
                    sleeper.sleep(200L * (attempt + 1))
                }
            }
        }
        return AppResult.Err(last)
    }
}

data class PrepareLessonInput(
    val providerConfigId: String,
    val providerKind: String,
    val model: String,
    val strategy: PedagogicalStrategy,
    val preferences: LearnerPreferences,
    val chapterTitle: String?,
    val precedingText: String?,
    val fragmentText: String,
    val memory: MemoryPacket,
    val systemPrompt: String? = null,
    val userPayload: String? = null,
    val maxOutputTokens: Int = 2000,
    val jsonSchema: String? = null,
    val schemaName: String? = null,
)

data class QuestionDraft(
    val type: QuestionType,
    val prompt: String,
    val targetSkill: SkillType,
    val targetExpression: String?,
    val sourceQuote: String,
    val localId: String? = null,
    val purpose: com.tepmex.extensivereadingtutor.domain.QuestionPurpose = com.tepmex.extensivereadingtutor.domain.QuestionPurpose.COMPREHENSION,
    val sourceQuotes: List<String> = emptyList(),
    val rubric: com.tepmex.extensivereadingtutor.domain.QuestionRubric? = null,
    val conceptRef: String? = null,
)

data class PreparedDraft(
    val introduction: String,
    val hints: List<String>,
    val questions: List<QuestionDraft>,
    val rawJson: String,
    val usage: TokenUsage?,
    val providerKind: String,
    val model: String,
    val hintPlans: List<com.tepmex.extensivereadingtutor.domain.HintPlan> = emptyList(),
    val requestFingerprint: String = "",
    val systemPrompt: String = "",
    val userPayload: String = "",
)

data class EvaluateLessonInput(
    val providerConfigId: String,
    val providerKind: String,
    val model: String,
    val strategy: PedagogicalStrategy,
    val preferences: LearnerPreferences,
    val fragmentText: String,
    val questions: List<com.tepmex.extensivereadingtutor.domain.LessonQuestion>,
    val submission: com.tepmex.extensivereadingtutor.domain.StudentSubmission,
    val memory: MemoryPacket,
    val systemPrompt: String? = null,
    val userPayload: String? = null,
    val maxOutputTokens: Int = 2000,
    val jsonSchema: String? = null,
    val schemaName: String? = null,
)

data class ReviewDraft(
    val questionId: String,
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

data class SelectionReviewDraft(
    val selectionId: String,
    val selectionRevision: Int,
    val status: com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus,
    val explanation: String,
    val contextualMeaning: String? = null,
    val nuance: String? = null,
    val example: String? = null,
    val sourceQuotes: List<String> = emptyList(),
)

data class ObservationDraft(
    val questionId: String?,
    val conceptLabel: String?,
    val skillType: SkillType,
    val observationType: ObservationType,
    val outcome: Outcome,
    val studentEvidence: String,
    val sourceQuote: String,
    val interpretation: String,
    val confidence: Confidence,
    val payloadJson: String,
    val conceptRef: String? = null,
    val diagnosticKey: String? = null,
    val evidenceSource: com.tepmex.extensivereadingtutor.domain.EvidenceSource = com.tepmex.extensivereadingtutor.domain.EvidenceSource.ANSWER,
    val evidenceMode: com.tepmex.extensivereadingtutor.domain.EvidenceMode = com.tepmex.extensivereadingtutor.domain.EvidenceMode.DEMONSTRATED,
    val assistanceAssessment: com.tepmex.extensivereadingtutor.domain.AssistanceLevel = com.tepmex.extensivereadingtutor.domain.AssistanceLevel.UNKNOWN,
    val selectionId: String? = null,
    val selectionRevision: Int? = null,
)

data class RejectedObservationDraft(val payloadJson: String, val reason: String)

data class EvaluationDraft(
    val reviews: List<ReviewDraft>,
    val observations: List<ObservationDraft>,
    val rejectedObservations: List<RejectedObservationDraft>,
    val nextRecommendations: List<String>,
    val rawJson: String,
    val usage: TokenUsage?,
    val providerKind: String,
    val model: String,
    val selectionReviews: List<SelectionReviewDraft> = emptyList(),
    val feedbackResponse: String? = null,
    val continuityNotes: List<com.tepmex.extensivereadingtutor.domain.ContinuityNote> = emptyList(),
    val requestFingerprint: String = "",
    val systemPrompt: String = "",
    val userPayload: String = "",
)

data class ExplainSelectionInput(
    val providerConfigId: String,
    val providerKind: String,
    val model: String,
    val strategy: com.tepmex.extensivereadingtutor.pedagogy.PedagogicalStrategy,
    val userPayload: String,
    val systemPrompt: String,
    val selectionId: String,
    val selectionRevision: Int,
)

data class ExplainDraft(
    val selectionId: String,
    val selectionRevision: Int,
    val status: com.tepmex.extensivereadingtutor.domain.SelectionReviewStatus,
    val explanation: String,
    val contextualMeaning: String?,
    val nuance: String?,
    val example: String?,
    val sourceQuotes: List<String>,
    val rawJson: String,
    val usage: TokenUsage?,
    val providerKind: String,
    val model: String,
    val requestFingerprint: String,
)

interface LessonAi {
    suspend fun prepareLesson(input: PrepareLessonInput): AppResult<PreparedDraft>
    suspend fun evaluateAnswers(input: EvaluateLessonInput): AppResult<EvaluationDraft>
    suspend fun explainSelection(input: ExplainSelectionInput): AppResult<ExplainDraft> =
        AppResult.Err(DomainError.LessonNotReady("Объяснение выделения не настроено."))
}

fun interface ProviderLookup {
    suspend fun provider(configId: String): AppResult<LlmProvider>
}

class GatewayLessonAi(
    private val providers: ProviderLookup,
    private val retry: RetryingCalls,
    private val policy: LlmCallPolicy = LlmCallPolicy { LlmCallSettings() },
    private val json: Json = lessonJson,
) : LessonAi {
    override suspend fun prepareLesson(input: PrepareLessonInput): AppResult<PreparedDraft> {
        val user = input.userPayload ?: Prompts.prepareUser(
            input.preferences,
            input.chapterTitle,
            input.precedingText,
            input.fragmentText,
            input.memory,
        )
        val system = input.systemPrompt ?: Prompts.systemPrepare(input.strategy)
        val response = complete(
            input.providerConfigId,
            input.model,
            system,
            user,
            temperature = 0.4,
            purpose = "prepare",
            maxOutputTokens = input.maxOutputTokens,
            jsonSchema = input.jsonSchema,
            schemaName = input.schemaName,
        )
        if (response is AppResult.Err) return response
        val body = (response as AppResult.Ok).value
        if (body.finishReason.equals("length", ignoreCase = true)) {
            return AppResult.Err(DomainError.InvalidModelOutput("Ответ модели обрезан и не сохранён."))
        }
        val parsed = if (input.userPayload != null) parsePreparedV2(body.content) else parsePrepared(json, body.content)
        if (parsed is AppResult.Err) return parsed
        val draft = (parsed as AppResult.Ok).value
        return AppResult.Ok(
            draft.copy(
                rawJson = body.content,
                usage = body.usage,
                providerKind = input.providerKind,
                model = input.model,
                requestFingerprint = com.tepmex.extensivereadingtutor.pedagogy.requestFingerprint(input.model, 0.4, input.maxOutputTokens, system, user),
                systemPrompt = system,
                userPayload = user,
            ),
        )
    }

    override suspend fun evaluateAnswers(input: EvaluateLessonInput): AppResult<EvaluationDraft> {
        val user = input.userPayload ?: Prompts.evaluateUser(
            input.preferences,
            input.fragmentText,
            input.questions,
            input.submission,
            input.memory,
        )
        val system = input.systemPrompt ?: Prompts.systemEvaluate(input.strategy)
        val response = complete(
            input.providerConfigId,
            input.model,
            system,
            user,
            temperature = 0.2,
            purpose = "evaluate",
            maxOutputTokens = input.maxOutputTokens,
            jsonSchema = input.jsonSchema,
            schemaName = input.schemaName,
        )
        if (response is AppResult.Err) return response
        val body = (response as AppResult.Ok).value
        if (body.finishReason.equals("length", ignoreCase = true)) {
            return AppResult.Err(DomainError.InvalidModelOutput("Ответ модели обрезан и не сохранён."))
        }
        val expected = input.questions.map { it.id.value }.toSet()
        val parsed = if (input.userPayload != null) {
            parseEvaluationV2(body.content, expected)
        } else {
            parseEvaluation(json, body.content, expected)
        }
        if (parsed is AppResult.Err) return parsed
        val draft = (parsed as AppResult.Ok).value
        return AppResult.Ok(
            draft.copy(
                rawJson = body.content,
                usage = body.usage,
                providerKind = input.providerKind,
                model = input.model,
                requestFingerprint = com.tepmex.extensivereadingtutor.pedagogy.requestFingerprint(input.model, 0.2, input.maxOutputTokens, system, user),
                systemPrompt = system,
                userPayload = user,
            ),
        )
    }

    override suspend fun explainSelection(input: ExplainSelectionInput): AppResult<ExplainDraft> {
        val response = complete(
            input.providerConfigId,
            input.model,
            input.systemPrompt,
            input.userPayload,
            temperature = 0.2,
            purpose = "explain-selection",
            maxOutputTokens = com.tepmex.extensivereadingtutor.pedagogy.OutputLimits.EXPLAIN_MAX_TOKENS,
            jsonSchema = com.tepmex.extensivereadingtutor.pedagogy.EXPLAIN_SCHEMA_JSON,
            schemaName = "explain-selection-v1",
        )
        if (response is AppResult.Err) return response
        val body = (response as AppResult.Ok).value
        if (body.finishReason.equals("length", ignoreCase = true)) {
            return AppResult.Err(DomainError.InvalidModelOutput("Ответ модели обрезан и не сохранён."))
        }
        val parsed = parseExplainV2(body.content, input.selectionId, input.selectionRevision)
        if (parsed is AppResult.Err) return parsed
        val draft = (parsed as AppResult.Ok).value
        return AppResult.Ok(
            draft.copy(
                rawJson = body.content,
                usage = body.usage,
                providerKind = input.providerKind,
                model = input.model,
                requestFingerprint = com.tepmex.extensivereadingtutor.pedagogy.requestFingerprint(
                    input.model,
                    0.2,
                    com.tepmex.extensivereadingtutor.pedagogy.OutputLimits.EXPLAIN_MAX_TOKENS,
                    input.systemPrompt,
                    input.userPayload,
                ),
            ),
        )
    }

    private suspend fun complete(
        providerConfigId: String,
        model: String,
        system: String,
        user: String,
        temperature: Double,
        purpose: String,
        maxOutputTokens: Int = 2000,
        jsonSchema: String? = null,
        schemaName: String? = null,
    ): AppResult<LlmResponse> {
        val resolved = providers.provider(providerConfigId)
        if (resolved is AppResult.Err) return resolved
        val provider = (resolved as AppResult.Ok).value
        val messages = if (provider.capabilities.systemRole) {
            listOf(LlmMessage("system", system), LlmMessage("user", user))
        } else {
            listOf(LlmMessage("user", system + "\n\n" + user))
        }
        val call = policy.current()
        return retry.generate(
            provider,
            LlmRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxOutputTokens = maxOutputTokens,
                jsonObjectMode = provider.capabilities.jsonObjectMode,
                jsonSchema = jsonSchema,
                jsonSchemaName = schemaName,
                timeoutMillis = call.timeoutMillis,
                extraBodyJson = call.extraBodyJson,
                purpose = purpose,
            ),
        )
    }
}

internal val lessonJson = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }

@Serializable
private data class PreparedDto(
    val introduction: String = "",
    val hints: List<String> = emptyList(),
    val questions: List<JsonElement> = emptyList(),
)

@Serializable
private data class QuestionDto(
    val type: String,
    val prompt: String,
    val targetSkill: String,
    val targetExpression: String? = null,
    val sourceQuote: String = "",
)

@Serializable
private data class EvaluationDto(
    val questionReviews: List<JsonElement> = emptyList(),
    val observations: List<JsonElement> = emptyList(),
    val nextRecommendations: List<String> = emptyList(),
)

@Serializable
private data class ReviewDto(
    val questionId: String,
    val verdict: String,
    val explanation: String = "",
    val errorNotes: String = "",
    val evidenceSufficient: Boolean = false,
    val examples: List<String> = emptyList(),
)

@Serializable
private data class ObservationDto(
    val questionId: String? = null,
    val conceptLabel: String? = null,
    val skillType: String,
    val observationType: String,
    val outcome: String,
    val studentEvidence: String = "",
    val sourceQuote: String = "",
    val interpretation: String = "",
    val confidence: String,
)

fun extractJsonObject(text: String): String {
    val trimmed = text.trim()
    val fenced = Regex("""```(?:json)?\s*(\{.*\})\s*```""", RegexOption.DOT_MATCHES_ALL).find(trimmed)
    if (fenced != null) return fenced.groupValues[1]
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start >= 0 && end > start) return trimmed.substring(start, end + 1)
    return trimmed
}

fun parsePrepared(json: Json, content: String): AppResult<PreparedDraft> {
    val dto = try {
        json.decodeFromString<PreparedDto>(extractJsonObject(content))
    } catch (error: Exception) {
        return AppResult.Err(DomainError.InvalidModelOutput("Введение не разобрано: ${error.message}"))
    }
    if (dto.introduction.isBlank()) {
        return AppResult.Err(DomainError.InvalidModelOutput("Пустое введение."))
    }
    val questions = mutableListOf<QuestionDraft>()
    for (element in dto.questions) {
        val parsed = try {
            json.decodeFromJsonElement<QuestionDto>(element)
        } catch (_: Exception) {
            continue
        }
        val type = enumOrNull<QuestionType>(parsed.type) ?: continue
        val skill = enumOrNull<SkillType>(parsed.targetSkill) ?: continue
        if (parsed.prompt.isBlank() || parsed.sourceQuote.isBlank()) continue
        questions += QuestionDraft(
            type = type,
            prompt = parsed.prompt.trim(),
            targetSkill = skill,
            targetExpression = parsed.targetExpression?.trim()?.takeIf { it.isNotEmpty() },
            sourceQuote = parsed.sourceQuote,
        )
        if (questions.size == 6) break
    }
    if (questions.isEmpty()) {
        return AppResult.Err(DomainError.InvalidModelOutput("Нет вопросов с точной цитатой."))
    }
    return AppResult.Ok(
        PreparedDraft(
            introduction = dto.introduction.trim().take(4000),
            hints = dto.hints.map { it.trim() }.filter { it.isNotEmpty() }.take(4),
            questions = questions,
            rawJson = content,
            usage = null,
            providerKind = "",
            model = "",
        ),
    )
}

fun parseEvaluation(json: Json, content: String, expectedQuestionIds: Set<String>): AppResult<EvaluationDraft> {
    val dto = try {
        json.decodeFromString<EvaluationDto>(extractJsonObject(content))
    } catch (error: Exception) {
        return AppResult.Err(DomainError.InvalidModelOutput("Разбор не разобран: ${error.message}"))
    }
    val reviews = mutableListOf<ReviewDraft>()
    for (element in dto.questionReviews) {
        val parsed = try {
            json.decodeFromJsonElement<ReviewDto>(element)
        } catch (error: Exception) {
            return AppResult.Err(DomainError.InvalidModelOutput("Плохой разбор вопроса: ${error.message}"))
        }
        val verdict = enumOrNull<AnswerVerdict>(parsed.verdict)
            ?: return AppResult.Err(DomainError.InvalidModelOutput("Неизвестный вердикт ${parsed.verdict}"))
        if (parsed.questionId !in expectedQuestionIds) continue
        if (reviews.any { it.questionId == parsed.questionId }) continue
        reviews += ReviewDraft(
            questionId = parsed.questionId,
            verdict = verdict,
            explanation = parsed.explanation.trim(),
            errorNotes = parsed.errorNotes.trim(),
            evidenceSufficient = parsed.evidenceSufficient,
            examples = parsed.examples.take(3),
        )
    }
    if (reviews.map { it.questionId }.toSet() != expectedQuestionIds) {
        return AppResult.Err(DomainError.InvalidModelOutput("Разбор покрывает не все вопросы."))
    }
    val accepted = mutableListOf<ObservationDraft>()
    val rejected = mutableListOf<RejectedObservationDraft>()
    for (element in dto.observations) {
        val raw = element.toString()
        val parsed = try {
            json.decodeFromJsonElement<ObservationDto>(element)
        } catch (error: Exception) {
            rejected += RejectedObservationDraft(raw, error.message ?: "observation schema")
            continue
        }
        val skill = enumOrNull<SkillType>(parsed.skillType)
        val type = enumOrNull<ObservationType>(parsed.observationType)
        val outcome = enumOrNull<Outcome>(parsed.outcome)
        val confidence = enumOrNull<Confidence>(parsed.confidence)
        if (skill == null || type == null || outcome == null || confidence == null) {
            rejected += RejectedObservationDraft(raw, "unknown observation enum")
            continue
        }
        val questionId = parsed.questionId
        if (questionId != null && questionId !in expectedQuestionIds) {
            rejected += RejectedObservationDraft(raw, "unknown questionId")
            continue
        }
        accepted += ObservationDraft(
            questionId = questionId,
            conceptLabel = parsed.conceptLabel,
            skillType = skill,
            observationType = type,
            outcome = outcome,
            studentEvidence = parsed.studentEvidence,
            sourceQuote = parsed.sourceQuote,
            interpretation = parsed.interpretation,
            confidence = confidence,
            payloadJson = raw,
        )
    }
    return AppResult.Ok(
        EvaluationDraft(
            reviews = reviews,
            observations = accepted,
            rejectedObservations = rejected,
            nextRecommendations = dto.nextRecommendations.take(5),
            rawJson = content,
            usage = null,
            providerKind = "",
            model = "",
        ),
    )
}

private inline fun <reified T : Enum<T>> enumOrNull(raw: String): T? =
    enumValues<T>().firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

fun fingerprint(operation: String, model: String, userPayload: String): String =
    com.tepmex.extensivereadingtutor.books.sha256("$operation|$model|$userPayload")

fun chatCompletionsUrl(baseUrl: String): String {
    val trimmed = baseUrl.trim().trimEnd('/')
    return when {
        trimmed.endsWith("/chat/completions") -> trimmed
        trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
        else -> "$trimmed/v1/chat/completions"
    }
}

fun redact(text: String, secrets: List<String>): String {
    var out = text
    for (secret in secrets) {
        if (secret.length >= 4) out = out.replace(secret, "[redacted]")
    }
    return out.replace(Regex("""Bearer\s+\S+"""), "Bearer [redacted]").take(400)
}

sealed interface HttpResult {
    data class Ok(
        val code: Int,
        val body: String,
        val requestHeaders: List<HttpHeader> = emptyList(),
        val responseHeaders: List<HttpHeader> = emptyList(),
    ) : HttpResult

    data class Fail(
        val kind: LlmFailureKind,
        val code: Int?,
        val body: String?,
        val requestHeaders: List<HttpHeader> = emptyList(),
        val responseHeaders: List<HttpHeader> = emptyList(),
    ) : HttpResult
}

interface HttpTransport {
    suspend fun postJson(
        url: String,
        headers: Map<String, String>,
        body: String,
        timeoutMillis: Long,
    ): HttpResult
}

class OpenAiCompatibleProvider(
    override val capabilities: LlmCapabilities,
    private val baseUrl: String,
    private val apiKey: String,
    private val http: HttpTransport,
    private val exchanges: LlmExchangeLog = NoopLlmExchangeLog,
) : LlmProvider {
    override suspend fun generate(request: LlmRequest): AppResult<LlmResponse> {
        val started = Instant.now()
        val startedNanos = System.nanoTime()
        val url = if (baseUrl.isBlank()) "" else chatCompletionsUrl(baseUrl)
        if (apiKey.isBlank()) {
            remember(request, url, started, startedNanos, false, null, LlmFailureKind.UNAUTHORIZED.name, emptyList(), emptyList(), "", "Нет ключа API.")
            return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.UNAUTHORIZED, "Нет ключа API."))
        }
        if (baseUrl.isBlank()) {
            remember(request, url, started, startedNanos, false, null, LlmFailureKind.BAD_RESPONSE.name, emptyList(), emptyList(), "", "Не указан адрес API.")
            return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, "Не указан адрес API."))
        }
        val payload = when (val built = chatPayload(request, capabilities.jsonObjectMode, capabilities.strictJsonSchema)) {
            is AppResult.Err -> {
                val message = (built.error as? DomainError.LlmFailed)?.detail ?: "Неверные дополнительные поля."
                remember(request, url, started, startedNanos, false, null, "INVALID_EXTRA", emptyList(), emptyList(), request.extraBodyJson, message)
                return built
            }
            is AppResult.Ok -> built.value
        }
        val headers = linkedMapOf("Authorization" to "Bearer $apiKey")
        return when (val result = http.postJson(url, headers, payload, request.timeoutMillis)) {
            is HttpResult.Fail -> {
                remember(
                    request, url, started, startedNanos, true, result.code, result.kind.name,
                    result.requestHeaders.ifEmpty { headers.toHeaders() }, result.responseHeaders, payload, result.body,
                )
                AppResult.Err(DomainError.LlmFailed(result.kind, redact(result.body ?: result.kind.name, listOf(apiKey))))
            }
            is HttpResult.Ok -> {
                val parsed = parseChatBody(result.body, apiKey, capabilities.reportsUsage)
                val failure = (parsed as? AppResult.Err)?.let { (it.error as? DomainError.LlmFailed)?.kind?.name }
                remember(
                    request, url, started, startedNanos, true, result.code, failure,
                    result.requestHeaders.ifEmpty { headers.toHeaders() }, result.responseHeaders, payload, result.body,
                )
                parsed
            }
        }
    }

    private suspend fun remember(
        request: LlmRequest,
        url: String,
        started: Instant,
        startedNanos: Long,
        sent: Boolean,
        httpStatus: Int?,
        failureKind: String?,
        requestHeaders: List<HttpHeader>,
        responseHeaders: List<HttpHeader>,
        requestBody: String,
        responseBody: String?,
    ) {
        val usage = tokenUsageOrNull(responseBody)
        val secrets = listOf(apiKey)
        val exchange = LlmExchange(
            id = UUID.randomUUID().toString(),
            purpose = request.purpose,
            model = request.model,
            url = url,
            startedAtEpochMillis = started.toEpochMilli(),
            finishedAtEpochMillis = Instant.now().toEpochMilli(),
            durationMillis = ((System.nanoTime() - startedNanos) / 1_000_000L).coerceAtLeast(0),
            sent = sent,
            httpStatus = httpStatus,
            failureKind = failureKind,
            requestHeadersJson = headersJson(redactHeaders(requestHeaders, secrets)),
            requestBody = loggedBody(requestBody, secrets),
            responseHeadersJson = headersJson(redactHeaders(responseHeaders, secrets)),
            responseBody = responseBody?.let { loggedBody(it, secrets) },
            promptTokens = usage?.promptTokens,
            completionTokens = usage?.completionTokens,
            totalTokens = usage?.totalTokens,
        )
        withContext(Dispatchers.IO) {
            try {
                exchanges.append(exchange)
            } catch (_: Exception) {
            }
        }
    }
}

private fun Map<String, String>.toHeaders(): List<HttpHeader> = map { (name, value) -> HttpHeader(name, value) }

private fun parseChatBody(body: String, apiKey: String, readUsage: Boolean): AppResult<LlmResponse> {
    return try {
        val root = Json.parseToJsonElement(body) as JsonObject
        val choice = (root["choices"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
        val content = (message?.get("content") as? JsonPrimitive)?.content
        if (content.isNullOrBlank()) {
            return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, "Пустой ответ модели."))
        }
        val usage = if (readUsage) tokenUsageOrNull(body) else null
        val finish = (choice?.get("finish_reason") as? JsonPrimitive)?.content
        AppResult.Ok(LlmResponse(content = content, usage = usage, finishReason = finish))
    } catch (error: Exception) {
        AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, redact(error.message ?: "bad json", listOf(apiKey))))
    }
}

fun questionIdOrNull(raw: String?): QuestionId? = raw?.takeIf { it.isNotBlank() }?.let(::QuestionId)
