package com.tepmex.tinglistories.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationTest {
    @Test
    fun fencedGradeUsesQuestionCountAsMax() {
        val raw = """
            Вот оценка:
            ```json
            {"score":3,"max":99,"summary":"Почти всё.","items":[{"index":1,"correct":true,"comment":"Смысл совпал."},{"index":2,"correct":false,"comment":"Другое место."}]}
            ```
        """.trimIndent()
        val grade = parseEvaluation(raw, questionCount = 4)
        assertEquals(3, grade.score)
        assertEquals(4, grade.max)
        assertEquals("Почти всё.", grade.summary)
        assertEquals("Смысл совпал.", grade.items.first().comment)
        assertFalse(grade.items[1].correct)
    }

    @Test(expected = IllegalArgumentException::class)
    fun proseWithoutObjectFails() {
        parseEvaluation("Ответ хороший, но это не JSON.", questionCount = 2)
    }

    @Test
    fun chatUrlAndMessageParts() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            chatCompletionsUrl("https://api.openai.com/v1"),
        )
        assertEquals(
            "https://example.test/v1/chat/completions",
            chatCompletionsUrl("https://example.test"),
        )
        assertEquals(
            "https://example.test/chat/completions",
            chatCompletionsUrl("https://example.test/chat/completions/"),
        )
        val message = JSONObject().put(
            "content",
            JSONArray()
                .put(JSONObject().put("type", "text").put("text", "{\"score\":1}"))
                .put(" tail"),
        )
        assertEquals("{\"score\":1} tail", readMessageContent(message))
        assertEquals("plain", readMessageContent(JSONObject().put("content", "plain")))
    }

    @Test
    fun promptCarriesReferenceAndLearnerAnswer() {
        val story = Story(
            id = 1,
            title = "换工作",
            text = "李红换了工作。",
            questions = listOf(Question("以前做什么？", "会计")),
            hasStoryAudio = true,
            hasQuestionsAudio = false,
        )
        val prompt = evaluationUserPrompt(story, listOf("会计"))
        assertTrue(prompt.contains("换工作"))
        assertTrue(prompt.contains("Эталон: 会计"))
        assertTrue(prompt.contains("Ответ ученика: 会计"))
        assertTrue(evaluationSystemPrompt().contains("\"score\""))
    }
}
