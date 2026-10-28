package com.tepmex.tinglistories.domain

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportDocumentTest {
    @Test
    fun exportKeepsTextsQuestionsAnswersAndTheChart() {
        val library = Library(
            level = "HSK 4",
            kind = "listening",
            stories = listOf(
                story(
                    1,
                    "换工作",
                    "小王想换工作。",
                    Question("他为什么？", "因为工资低。"),
                    Question("他去哪？", "去公司。"),
                    Question("问三", "答三"),
                    Question("问四", "答四"),
                ),
                story(2, "第二", "第二篇。", Question("问二", "答二"), Question("问二б", "答二б"), Question("问二в", "答二в"), Question("问二г", "答二г")),
                story(3, "第三", "还没听完。", Question("问三", "答三")),
                story(4, "第四", "零分的故事。", Question("问四", "答四"), Question("问四б", "答四б"), Question("问四в", "答四в"), Question("问四г", "答四г")),
            ),
            progress = mapOf(
                1 to StoryProgress(
                    listenCount = 3,
                    answers = listOf("工资", "公司", "", ""),
                    evaluation = Evaluation(
                        score = 2,
                        max = 4,
                        summary = "Смысл в целом совпал.",
                        items = listOf(
                            EvaluationItem(1, true, "Смысл совпал."),
                            EvaluationItem(2, false, "Место другое."),
                        ),
                    ),
                ),
                2 to StoryProgress(
                    listenCount = 2,
                    answers = listOf("верно", "верно", "верно", "верно"),
                    evaluation = Evaluation(4, 4, "Да", listOf(EvaluationItem(1, true, "Совпало."))),
                ),
                3 to StoryProgress(listenCount = 10),
                4 to StoryProgress(
                    listenCount = 4,
                    answers = listOf("", "", "", ""),
                    evaluation = Evaluation(0, 4, "Мимо", emptyList()),
                ),
            ),
        )

        val root = JSONObject(
            exportDocument(library, draftStoryId = 3, draftAnswers = listOf("ещё не отправлено")),
        )

        assertEquals(1, root.getInt("version"))
        assertEquals("HSK 4", root.getString("level"))
        assertEquals("listening", root.getString("kind"))
        assertFalse(root.has("token"))

        val stories = root.getJSONArray("stories")
        assertEquals(4, stories.length())

        val first = stories.getJSONObject(0)
        assertEquals("小王想换工作。", first.getString("text"))
        assertEquals("换工作", first.getString("title"))
        assertEquals(3, first.getInt("listenCount"))
        assertTrue(first.getBoolean("completed"))
        assertEquals(2, first.getInt("score"))
        assertEquals(4, first.getInt("max"))
        assertEquals("Смысл в целом совпал.", first.getString("summary"))
        assertEquals(50.0 / 3.0, first.getDouble("indicator"), 0.0001)
        assertFalse(first.getBoolean("green"))
        val questions = first.getJSONArray("questions")
        assertEquals("他为什么？", questions.getJSONObject(0).getString("question"))
        assertEquals("因为工资低。", questions.getJSONObject(0).getString("reference"))
        assertEquals("工资", questions.getJSONObject(0).getString("answer"))
        assertTrue(questions.getJSONObject(0).getBoolean("correct"))
        assertEquals("Смысл совпал.", questions.getJSONObject(0).getString("comment"))
        assertEquals("公司", questions.getJSONObject(1).getString("answer"))
        assertFalse(questions.getJSONObject(1).getBoolean("correct"))

        val draft = stories.getJSONObject(2)
        assertEquals("还没听完。", draft.getString("text"))
        assertFalse(draft.getBoolean("completed"))
        assertTrue(draft.isNull("score"))
        assertTrue(draft.isNull("indicator"))
        assertTrue(draft.isNull("green"))
        val pending = draft.getJSONArray("questions").getJSONObject(0)
        assertEquals("问三", pending.getString("question"))
        assertEquals("答三", pending.getString("reference"))
        assertEquals("ещё не отправлено", pending.getString("answer"))
        assertTrue(pending.isNull("correct"))
        assertTrue(pending.isNull("comment"))

        val statistics = root.getJSONObject("statistics")
        val bars = statistics.getJSONArray("bars")
        assertEquals(listOf(1, 2, 4), List(bars.length()) { bars.getJSONObject(it).getInt("catalog") })
        assertEquals(listOf(1, 2, 4), List(bars.length()) { bars.getJSONObject(it).getInt("id") })
        assertEquals(50.0 / 3.0, bars.getJSONObject(0).getDouble("indicator"), 0.0001)
        assertEquals(50.0, bars.getJSONObject(1).getDouble("indicator"), 0.0001)
        assertEquals(0.0, bars.getJSONObject(2).getDouble("indicator"), 0.0001)
        assertFalse(bars.getJSONObject(0).getBoolean("green"))
        assertTrue(bars.getJSONObject(1).getBoolean("green"))
        assertFalse(bars.getJSONObject(2).getBoolean("green"))
        assertEquals("22,2", formatStat(statistics.getDouble("mean")))
        assertTrue(exportDocument(library).contains("小王想换工作。"))
    }

    @Test
    fun aGradedStoryKeepsSavedAnswersWhenAnotherDraftIsOpen() {
        val library = Library(
            stories = listOf(story(7, "标题", "正文", Question("问", "答"))),
            progress = mapOf(
                7 to StoryProgress(
                    listenCount = 1,
                    answers = listOf("сохранено"),
                    evaluation = Evaluation(1, 1, "ok", listOf(EvaluationItem(1, true, "да"))),
                ),
            ),
        )
        val root = JSONObject(exportDocument(library, draftStoryId = 7, draftAnswers = listOf("черновик")))
        val question = root.getJSONArray("stories").getJSONObject(0).getJSONArray("questions").getJSONObject(0)
        assertEquals("сохранено", question.getString("answer"))
        assertEquals("答", question.getString("reference"))
        val bar = root.getJSONObject("statistics").getJSONArray("bars").getJSONObject(0)
        assertEquals(7, bar.getInt("id"))
        assertTrue(bar.getBoolean("green"))
    }

    @Test
    fun anEmptyLibraryExportsNoBars() {
        val root = JSONObject(exportDocument(Library()))
        assertEquals(0, root.getJSONArray("stories").length())
        val statistics = root.getJSONObject("statistics")
        assertTrue(statistics.isNull("mean"))
        assertEquals(0, statistics.getJSONArray("bars").length())
    }

    private fun story(id: Int, title: String, text: String, vararg questions: Question) = Story(
        id = id,
        title = title,
        text = text,
        questions = questions.toList(),
        hasStoryAudio = true,
        hasQuestionsAudio = false,
    )
}
