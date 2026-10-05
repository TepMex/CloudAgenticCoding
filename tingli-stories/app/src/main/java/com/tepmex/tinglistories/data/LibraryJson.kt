package com.tepmex.tinglistories.data

import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.EvaluationItem
import com.tepmex.tinglistories.domain.Library
import com.tepmex.tinglistories.domain.Question
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.StoryProgress
import org.json.JSONArray
import org.json.JSONObject

object LibraryJson {
    fun encode(library: Library): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("level", library.level)
        root.put("kind", library.kind)
        val stories = JSONArray()
        for (story in library.stories) {
            stories.put(encodeStory(story))
        }
        root.put("stories", stories)
        val progress = JSONObject()
        for ((id, item) in library.progress) {
            progress.put(id.toString(), encodeProgress(item))
        }
        root.put("progress", progress)
        return root.toString()
    }

    fun decode(text: String): Library {
        val root = JSONObject(text)
        val storiesArray = root.optJSONArray("stories") ?: JSONArray()
        val stories = ArrayList<Story>(storiesArray.length())
        for (i in 0 until storiesArray.length()) {
            val item = storiesArray.optJSONObject(i) ?: continue
            stories += decodeStory(item)
        }
        val known = stories.map { it.id }.toSet()
        val progressJson = root.optJSONObject("progress") ?: JSONObject()
        val progress = LinkedHashMap<Int, StoryProgress>()
        val keys = progressJson.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val id = key.toIntOrNull() ?: continue
            if (id !in known) continue
            val item = progressJson.optJSONObject(key) ?: continue
            progress[id] = decodeProgress(item)
        }
        return Library(
            level = root.optString("level"),
            kind = root.optString("kind"),
            stories = stories,
            progress = progress,
        )
    }

    private fun encodeStory(story: Story): JSONObject {
        val questions = JSONArray()
        for (question in story.questions) {
            questions.put(JSONObject().put("q", question.prompt).put("a", question.reference))
        }
        return JSONObject()
            .put("id", story.id)
            .put("title", story.title)
            .put("text", story.text)
            .put("questions", questions)
            .put("hasStoryAudio", story.hasStoryAudio)
            .put("hasQuestionsAudio", story.hasQuestionsAudio)
            .put("storyExt", story.storyExt)
            .put("questionsExt", story.questionsExt)
    }

    private fun decodeStory(obj: JSONObject): Story {
        val questionsJson = obj.optJSONArray("questions") ?: JSONArray()
        val questions = ArrayList<Question>(questionsJson.length())
        for (i in 0 until questionsJson.length()) {
            val item = questionsJson.optJSONObject(i) ?: continue
            val prompt = item.optString("q").trim()
            if (prompt.isEmpty()) continue
            questions += Question(prompt, item.optString("a").trim())
        }
        return Story(
            id = obj.optInt("id"),
            title = obj.optString("title").ifBlank { "История ${obj.optInt("id")}" },
            text = obj.optString("text"),
            questions = questions,
            hasStoryAudio = obj.optBoolean("hasStoryAudio"),
            hasQuestionsAudio = obj.optBoolean("hasQuestionsAudio"),
            storyExt = obj.optString("storyExt").ifBlank { "mp3" },
            questionsExt = obj.optString("questionsExt").ifBlank { "mp3" },
        )
    }

    private fun encodeProgress(progress: StoryProgress): JSONObject {
        val answers = JSONArray()
        progress.answers.forEach { answers.put(it) }
        val obj = JSONObject()
            .put("listenCount", progress.listenCount)
            .put("answers", answers)
        progress.evaluation?.let { obj.put("evaluation", encodeEvaluation(it)) }
        return obj
    }

    private fun decodeProgress(obj: JSONObject): StoryProgress {
        val answersJson = obj.optJSONArray("answers") ?: JSONArray()
        val answers = ArrayList<String>(answersJson.length())
        for (i in 0 until answersJson.length()) {
            answers += answersJson.optString(i)
        }
        return StoryProgress(
            listenCount = obj.optInt("listenCount").coerceAtLeast(0),
            answers = answers,
            evaluation = obj.optJSONObject("evaluation")?.let { decodeEvaluation(it) },
        )
    }

    private fun encodeEvaluation(evaluation: Evaluation): JSONObject {
        val items = JSONArray()
        for (item in evaluation.items) {
            items.put(
                JSONObject()
                    .put("index", item.index)
                    .put("correct", item.correct)
                    .put("comment", item.comment),
            )
        }
        return JSONObject()
            .put("score", evaluation.score)
            .put("max", evaluation.max)
            .put("summary", evaluation.summary)
            .put("items", items)
    }

    private fun decodeEvaluation(obj: JSONObject): Evaluation {
        val itemsJson = obj.optJSONArray("items") ?: JSONArray()
        val items = ArrayList<EvaluationItem>(itemsJson.length())
        for (i in 0 until itemsJson.length()) {
            val item = itemsJson.optJSONObject(i) ?: continue
            items += EvaluationItem(
                index = item.optInt("index", i + 1),
                correct = item.optBoolean("correct"),
                comment = item.optString("comment"),
            )
        }
        val max = obj.optInt("max", items.size).coerceAtLeast(0)
        val score = obj.optInt("score").coerceIn(0, max.coerceAtLeast(obj.optInt("score")))
        return Evaluation(
            score = score,
            max = max,
            summary = obj.optString("summary"),
            items = items,
        )
    }
}
