package com.tepmex.tinglistories.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

fun evaluationSystemPrompt(): String = """
Ты проверяешь ответы ученика после аудирования китайской истории.
Сравнивай по смыслу, не по дословности. Ответ может быть на русском, китайском или пиньинь.
Верни только JSON без пояснений вокруг:
{"score":0,"max":0,"summary":"кратко по-русски","items":[{"index":1,"correct":true,"comment":"по-русски"}]}
score — сколько ответов верны по смыслу. max — число вопросов. items — по одному объекту на каждый вопрос, index с 1. correct — только true или false.
""".trimIndent()

fun evaluationUserPrompt(story: Story, answers: List<String>): String = buildString {
    appendLine("Название: ${story.title}")
    appendLine("Текст истории:")
    appendLine(story.text)
    appendLine("Вопросы:")
    story.questions.forEachIndexed { index, question ->
        val number = index + 1
        appendLine("$number. ${question.prompt}")
        appendLine("Эталон: ${question.reference}")
        val answer = answers.getOrNull(index).orEmpty().ifBlank { "(пусто)" }
        appendLine("Ответ ученика: $answer")
        appendLine()
    }
}

fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escape = false
    for (index in start until raw.length) {
        val char = raw[index]
        if (inString) {
            if (escape) {
                escape = false
            } else if (char == '\\') {
                escape = true
            } else if (char == '"') {
                inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) return raw.substring(start, index + 1)
            }
        }
    }
    return null
}

fun parseEvaluation(raw: String, questionCount: Int): Evaluation {
    val json = extractJsonObject(raw) ?: throw IllegalArgumentException("Модель вернула не JSON")
    val obj = try {
        JSONObject(json)
    } catch (_: JSONException) {
        throw IllegalArgumentException("Модель вернула не JSON")
    }
    val items = readItems(obj.optJSONArray("items"))
    if (!obj.has("score") && !obj.has("summary") && items.isEmpty()) {
        throw IllegalArgumentException("Модель вернула не JSON")
    }
    val maxScore = if (questionCount > 0) {
        questionCount
    } else {
        obj.optInt("max", items.size).coerceAtLeast(1)
    }
    val rawScore = if (obj.has("score")) obj.optInt("score") else items.count { it.correct }
    val score = rawScore.coerceIn(0, maxScore)
    val summary = obj.optString("summary").trim().ifBlank { "Оценка: $score из $maxScore" }
    return Evaluation(score = score, max = maxScore, summary = summary, items = items)
}

private fun readItems(array: JSONArray?): List<EvaluationItem> {
    if (array == null) return emptyList()
    val items = ArrayList<EvaluationItem>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val index = if (item.has("index")) item.optInt("index") else i + 1
        val comment = item.optString("comment").ifBlank { item.optString("explanation") }.trim()
        items += EvaluationItem(
            index = index,
            correct = item.optBoolean("correct", false),
            comment = comment,
        )
    }
    return items
}

fun readMessageContent(message: JSONObject): String {
    when (val raw = message.opt("content")) {
        is String -> return raw
        is JSONArray -> {
            val text = buildString {
                for (i in 0 until raw.length()) {
                    when (val part = raw.opt(i)) {
                        is String -> append(part)
                        is JSONObject -> append(part.optString("text"))
                    }
                }
            }
            if (text.isNotBlank()) return text
        }
    }
    return message.optString("content")
}

fun chatCompletionsUrl(baseUrl: String): String {
    val root = baseUrl.trim().trimEnd('/')
    return when {
        root.endsWith("/chat/completions") -> root
        root.endsWith("/v1") -> "$root/chat/completions"
        else -> "$root/v1/chat/completions"
    }
}
