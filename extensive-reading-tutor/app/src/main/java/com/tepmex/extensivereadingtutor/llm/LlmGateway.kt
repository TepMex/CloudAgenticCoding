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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

data class LlmMessage(val role: String, val content: String)

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double,
    val maxOutputTokens: Int,
    val jsonObjectMode: Boolean,
    val timeoutMillis: Long,
)

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
)

data class QuestionDraft(
    val type: QuestionType,
    val prompt: String,
    val targetSkill: SkillType,
    val targetExpression: String?,
    val sourceQuote: String,
)

data class PreparedDraft(
    val introduction: String,
    val hints: List<String>,
    val questions: List<QuestionDraft>,
    val rawJson: String,
    val usage: TokenUsage?,
    val providerKind: String,
    val model: String,
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
)

data class ReviewDraft(
    val questionId: String,
    val verdict: AnswerVerdict,
    val explanation: String,
    val errorNotes: String,
    val evidenceSufficient: Boolean,
    val examples: List<String>,
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
)

interface LessonAi {
    suspend fun prepareLesson(input: PrepareLessonInput): AppResult<PreparedDraft>
    suspend fun evaluateAnswers(input: EvaluateLessonInput): AppResult<EvaluationDraft>
}

fun interface ProviderLookup {
    suspend fun provider(configId: String): AppResult<LlmProvider>
}

class GatewayLessonAi(
    private val providers: ProviderLookup,
    private val retry: RetryingCalls,
    private val json: Json = lessonJson,
) : LessonAi {
    override suspend fun prepareLesson(input: PrepareLessonInput): AppResult<PreparedDraft> {
        val user = Prompts.prepareUser(
            input.preferences,
            input.chapterTitle,
            input.precedingText,
            input.fragmentText,
            input.memory,
        )
        val response = complete(
            input.providerConfigId,
            input.model,
            Prompts.systemPrepare(input.strategy),
            user,
            temperature = 0.4,
        )
        if (response is AppResult.Err) return response
        val body = (response as AppResult.Ok).value
        val parsed = parsePrepared(json, body.content)
        if (parsed is AppResult.Err) return parsed
        val draft = (parsed as AppResult.Ok).value
        return AppResult.Ok(
            draft.copy(rawJson = body.content, usage = body.usage, providerKind = input.providerKind, model = input.model),
        )
    }

    override suspend fun evaluateAnswers(input: EvaluateLessonInput): AppResult<EvaluationDraft> {
        val user = Prompts.evaluateUser(
            input.preferences,
            input.fragmentText,
            input.questions,
            input.submission,
            input.memory,
        )
        val response = complete(
            input.providerConfigId,
            input.model,
            Prompts.systemEvaluate(input.strategy),
            user,
            temperature = 0.2,
        )
        if (response is AppResult.Err) return response
        val body = (response as AppResult.Ok).value
        val expected = input.questions.map { it.id.value }.toSet()
        val parsed = parseEvaluation(json, body.content, expected)
        if (parsed is AppResult.Err) return parsed
        val draft = (parsed as AppResult.Ok).value
        return AppResult.Ok(
            draft.copy(rawJson = body.content, usage = body.usage, providerKind = input.providerKind, model = input.model),
        )
    }

    private suspend fun complete(
        providerConfigId: String,
        model: String,
        system: String,
        user: String,
        temperature: Double,
    ): AppResult<LlmResponse> {
        val resolved = providers.provider(providerConfigId)
        if (resolved is AppResult.Err) return resolved
        val provider = (resolved as AppResult.Ok).value
        val messages = if (provider.capabilities.systemRole) {
            listOf(LlmMessage("system", system), LlmMessage("user", user))
        } else {
            listOf(LlmMessage("user", system + "\n\n" + user))
        }
        return retry.generate(
            provider,
            LlmRequest(
                model = model,
                messages = messages,
                temperature = temperature,
                maxOutputTokens = 2000,
                jsonObjectMode = provider.capabilities.jsonObjectMode,
                timeoutMillis = 60_000,
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
    data class Ok(val code: Int, val body: String) : HttpResult
    data class Fail(val kind: LlmFailureKind, val code: Int?, val body: String?) : HttpResult
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
) : LlmProvider {
    override suspend fun generate(request: LlmRequest): AppResult<LlmResponse> {
        if (apiKey.isBlank()) {
            return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.UNAUTHORIZED, "Нет ключа API."))
        }
        if (baseUrl.isBlank()) {
            return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, "Не указан адрес API."))
        }
        val payload = buildString {
            append("""{"model":""")
            append(jsonString(request.model))
            append(""","temperature":""")
            append(request.temperature)
            append(""","messages":[""")
            request.messages.forEachIndexed { index, message ->
                if (index > 0) append(',')
                append("""{"role":""")
                append(jsonString(message.role))
                append(""","content":""")
                append(jsonString(message.content))
                append('}')
            }
            append(']')
            if (request.jsonObjectMode && capabilities.jsonObjectMode) {
                append(""","response_format":{"type":"json_object"}""")
            }
            append('}')
        }
        val headers = linkedMapOf("Authorization" to "Bearer $apiKey")
        return when (val result = http.postJson(chatCompletionsUrl(baseUrl), headers, payload, request.timeoutMillis)) {
            is HttpResult.Fail -> AppResult.Err(
                DomainError.LlmFailed(result.kind, redact(result.body ?: result.kind.name, listOf(apiKey))),
            )
            is HttpResult.Ok -> parseChatBody(result.body, apiKey, capabilities.reportsUsage)
        }
    }
}

private fun parseChatBody(body: String, apiKey: String, readUsage: Boolean): AppResult<LlmResponse> {
    return try {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject
        val choice = (root["choices"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? kotlinx.serialization.json.JsonObject
        val message = choice?.get("message") as? kotlinx.serialization.json.JsonObject
        val content = (message?.get("content") as? kotlinx.serialization.json.JsonPrimitive)?.content
        if (content.isNullOrBlank()) {
            return AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, "Пустой ответ модели."))
        }
        val usage = if (readUsage) root["usage"] as? kotlinx.serialization.json.JsonObject else null
        val finish = (choice?.get("finish_reason") as? kotlinx.serialization.json.JsonPrimitive)?.content
        AppResult.Ok(
            LlmResponse(
                content = content,
                usage = usage?.let {
                    TokenUsage(
                        promptTokens = intField(it, "prompt_tokens"),
                        completionTokens = intField(it, "completion_tokens"),
                        totalTokens = intField(it, "total_tokens"),
                    )
                },
                finishReason = finish,
            ),
        )
    } catch (error: Exception) {
        AppResult.Err(DomainError.LlmFailed(LlmFailureKind.BAD_RESPONSE, redact(error.message ?: "bad json", listOf(apiKey))))
    }
}

private fun intField(obj: kotlinx.serialization.json.JsonObject, name: String): Int? =
    (obj[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(ch)
        }
    }
    append('"')
}

fun questionIdOrNull(raw: String?): QuestionId? = raw?.takeIf { it.isNotBlank() }?.let(::QuestionId)
