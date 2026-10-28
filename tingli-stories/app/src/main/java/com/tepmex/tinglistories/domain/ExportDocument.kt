package com.tepmex.tinglistories.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * One JSON file of the library the learner can keep: every story text,
 * each question with its reference and the learner’s answer, the grade,
 * and the same statistics the chart draws.
 *
 * An unfinished story uses [draftAnswers] when [draftStoryId] matches,
 * so an answer that has not been checked yet is still in the file.
 * A graded story keeps the saved answers. Titles, texts, and references
 * are included even while the screens still hide them.
 * The API token is not part of this document.
 */
fun exportDocument(
    library: Library,
    draftStoryId: Int? = null,
    draftAnswers: List<String> = emptyList(),
): String {
    val points = storyStats(library)
    val mean = meanIndicator(points)
    val pointByCatalog = points.associateBy { it.catalogNumber }
    val stories = JSONArray()
    library.stories.forEachIndexed { index, story ->
        val catalog = index + 1
        val progress = library.progressOf(story.id)
        val point = pointByCatalog[catalog]
        stories.put(encodeStory(catalog, story, progress, point, draftStoryId, draftAnswers))
    }
    val bars = JSONArray()
    library.stories.forEachIndexed { index, story ->
        val point = pointByCatalog[index + 1] ?: return@forEachIndexed
        bars.put(
            JSONObject()
                .put("catalog", point.catalogNumber)
                .put("id", story.id)
                .put("indicator", point.indicator)
                .put("green", point.green),
        )
    }
    val statistics = JSONObject()
        .putNullable("mean", mean)
        .put("bars", bars)
    return JSONObject()
        .put("version", 1)
        .put("level", library.level)
        .put("kind", library.kind)
        .put("stories", stories)
        .put("statistics", statistics)
        .toString(2) + "\n"
}

private fun encodeStory(
    catalog: Int,
    story: Story,
    progress: StoryProgress,
    point: StatsPoint?,
    draftStoryId: Int?,
    draftAnswers: List<String>,
): JSONObject {
    val learner = learnerAnswers(story, progress, draftStoryId, draftAnswers)
    val grade = progress.evaluation
    val comments = grade?.items?.associateBy { it.index }.orEmpty()
    val questions = JSONArray()
    story.questions.forEachIndexed { index, question ->
        val item = comments[index + 1]
        questions.put(
            JSONObject()
                .put("index", index + 1)
                .put("question", question.prompt)
                .put("reference", question.reference)
                .put("answer", learner.getOrElse(index) { "" })
                .putNullable("correct", item?.correct)
                .putNullable("comment", item?.comment),
        )
    }
    return JSONObject()
        .put("catalog", catalog)
        .put("id", story.id)
        .put("title", story.title)
        .put("text", story.text)
        .put("listenCount", progress.listenCount)
        .put("completed", progress.completed)
        .putNullable("score", grade?.score)
        .putNullable("max", grade?.max)
        .putNullable("summary", grade?.summary)
        .putNullable("indicator", point?.indicator)
        .putNullable("green", point?.green)
        .put("questions", questions)
}

private fun learnerAnswers(
    story: Story,
    progress: StoryProgress,
    draftStoryId: Int?,
    draftAnswers: List<String>,
): List<String> {
    val source = if (!progress.completed && story.id == draftStoryId) draftAnswers else progress.answers
    return alignedAnswers(story.questions.size, source)
}

private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
