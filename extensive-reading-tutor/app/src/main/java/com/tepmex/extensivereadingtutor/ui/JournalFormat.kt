package com.tepmex.extensivereadingtutor.ui

import com.tepmex.extensivereadingtutor.llm.LlmExchange
import com.tepmex.extensivereadingtutor.llm.jsonDocument
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val whenFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm:ss", Locale.forLanguageTag("ru"))

fun purposeLabel(purpose: String): String = when (purpose) {
    "prepare" -> "Подготовка занятия"
    "evaluate" -> "Разбор ответов"
    "probe" -> "Проверка соединения"
    else -> "Запрос к модели"
}

fun failureLabel(kind: String?): String = when (kind) {
    null, "" -> ""
    "TIMEOUT" -> "Таймаут"
    "RATE_LIMIT" -> "Лимит запросов"
    "UNAUTHORIZED" -> "Нет доступа"
    "BAD_RESPONSE" -> "Плохой ответ"
    "NETWORK" -> "Сеть"
    "INVALID_EXTRA" -> "Неверные доп. поля"
    else -> kind
}

fun statusLabel(exchange: LlmExchange): String {
    val failure = failureLabel(exchange.failureKind)
    return when {
        !exchange.sent -> failure.ifBlank { "Не отправлен" }
        exchange.httpStatus != null && failure.isBlank() -> "HTTP ${exchange.httpStatus}"
        exchange.httpStatus != null -> "HTTP ${exchange.httpStatus} · $failure"
        else -> failure.ifBlank { "Нет ответа" }
    }
}

fun formatDuration(millis: Long): String =
    if (millis < 1000) "$millis мс" else String.format(Locale.US, "%.1f с", millis / 1000.0)

fun formatTokens(prompt: Int?, completion: Int?, total: Int?): String {
    if (prompt == null && completion == null && total == null) return "Токены не сообщены"
    val promptText = prompt?.toString() ?: "—"
    val completionText = completion?.toString() ?: "—"
    val totalText = total?.toString() ?: "—"
    return "$promptText запрос · $completionText ответ · $totalText всего"
}

fun formatWhen(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    whenFormat.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

fun previewText(raw: String?, limit: Int = 180): String {
    val text = jsonDocument(raw).text.trim()
    if (text.isEmpty() || text == "—") return "Пусто"
    val oneLine = text.replace('\n', ' ').replace(Regex("\\s+"), " ")
    return if (oneLine.length <= limit) oneLine else oneLine.take(limit).trimEnd() + "…"
}

fun LlmExchange.toRow(): ExchangeRow {
    val status = statusLabel(this)
    return ExchangeRow(
        id = id,
        title = purposeLabel(purpose),
        model = model.ifBlank { "модель не указана" },
        whenLabel = formatWhen(startedAtEpochMillis),
        meta = "$status · ${formatDuration(durationMillis)}",
        tokens = formatTokens(promptTokens, completionTokens, totalTokens),
        ok = sent && failureKind == null && httpStatus in 200..299,
    )
}

fun LlmExchange.toView(): ExchangeView {
    val requestHeaderDoc = jsonDocument(requestHeadersJson)
    val requestBodyDoc = jsonDocument(requestBody)
    val responseHeaderDoc = jsonDocument(responseHeadersJson)
    val responseBodyDoc = jsonDocument(responseBody)
    return ExchangeView(
        id = id,
        title = purposeLabel(purpose),
        model = model.ifBlank { "модель не указана" },
        url = url.ifBlank { "Адрес не задан" },
        whenLabel = formatWhen(startedAtEpochMillis),
        duration = formatDuration(durationMillis),
        status = statusLabel(this),
        tokens = formatTokens(promptTokens, completionTokens, totalTokens),
        ok = sent && failureKind == null && httpStatus in 200..299,
        requestPreview = previewText(requestBody),
        responsePreview = previewText(responseBody),
        requestHeaders = requestHeaderDoc.text,
        requestBody = requestBodyDoc.text,
        responseHeaders = responseHeaderDoc.text,
        responseBody = responseBodyDoc.text,
        requestHeadersHighlighted = requestHeaderDoc.highlighted,
        requestBodyHighlighted = requestBodyDoc.highlighted,
        responseHeadersHighlighted = responseHeaderDoc.highlighted,
        responseBodyHighlighted = responseBodyDoc.highlighted,
    )
}
