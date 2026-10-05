package com.tepmex.tinglistories.domain

data class Question(
    val prompt: String,
    val reference: String,
)

enum class AudioKind { STORY, QUESTIONS }

data class Story(
    val id: Int,
    val title: String,
    val text: String,
    val questions: List<Question>,
    val hasStoryAudio: Boolean,
    val hasQuestionsAudio: Boolean,
    val storyExt: String = "mp3",
    val questionsExt: String = "mp3",
)

data class EvaluationItem(
    val index: Int,
    val correct: Boolean,
    val comment: String,
)

data class Evaluation(
    val score: Int,
    val max: Int,
    val summary: String,
    val items: List<EvaluationItem>,
)

data class StoryProgress(
    val listenCount: Int = 0,
    val answers: List<String> = emptyList(),
    val evaluation: Evaluation? = null,
) {
    val completed: Boolean get() = evaluation != null
}

data class Library(
    val level: String = "",
    val kind: String = "",
    val stories: List<Story> = emptyList(),
    val progress: Map<Int, StoryProgress> = emptyMap(),
) {
    fun story(id: Int): Story? = stories.find { it.id == id }

    fun progressOf(id: Int): StoryProgress = progress[id] ?: StoryProgress()

    val totalListens: Int get() = stories.sumOf { progressOf(it.id).listenCount }

    val completedCount: Int get() = stories.count { progressOf(it.id).completed }
}

fun shouldCountPlay(story: Story, kind: AudioKind): Boolean = when (kind) {
    AudioKind.STORY -> story.hasStoryAudio
    AudioKind.QUESTIONS -> story.hasQuestionsAudio
}

/** Story text plus every prompt and reference. Grades survive a reimport only when this matches. */
fun contentKey(story: Story): String = buildString {
    append(story.text)
    append('\u0000')
    story.questions.forEach { question ->
        append(question.prompt)
        append('\u0002')
        append(question.reference)
        append('\u0001')
    }
}

fun mergeProgress(
    oldStories: List<Story>,
    oldProgress: Map<Int, StoryProgress>,
    newStories: List<Story>,
): Map<Int, StoryProgress> {
    val oldById = oldStories.associateBy { it.id }
    val merged = LinkedHashMap<Int, StoryProgress>()
    for (story in newStories) {
        val previous = oldProgress[story.id] ?: continue
        val old = oldById[story.id]
        val kept = if (old != null && contentKey(old) == contentKey(story)) {
            previous
        } else {
            StoryProgress(listenCount = previous.listenCount)
        }
        if (kept.listenCount > 0 || kept.completed || kept.answers.any { it.isNotBlank() }) {
            merged[story.id] = kept
        }
    }
    return merged
}

fun nextStoryId(stories: List<Story>, currentId: Int): Int? {
    val index = stories.indexOfFirst { it.id == currentId }
    if (index < 0 || index + 1 >= stories.size) return null
    return stories[index + 1].id
}

fun alignedAnswers(questionCount: Int, answers: List<String>): List<String> =
    List(questionCount) { index -> answers.getOrNull(index).orEmpty() }

/** Text stays hidden until the grade when the story itself has audio. */
fun showTextBeforeAnswer(story: Story): Boolean = !story.hasStoryAudio

/** The title gives away the topic, so it stays hidden until the grade. */
fun showStoryTitle(progress: StoryProgress): Boolean = progress.completed

/**
 * Written prompts stay hidden until the grade when the questions have audio.
 * Without that audio the wording is the only way to know the question.
 */
fun showQuestionWording(story: Story, progress: StoryProgress): Boolean =
    progress.completed || !story.hasQuestionsAudio

/** Elapsed time as `m:ss`. Hours stay in the minute field. */
fun formatPlayback(positionMs: Int): String {
    val totalSeconds = positionMs.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/** Keeps a seek inside the clip. An unknown duration only rejects a negative position. */
fun clampSeek(positionMs: Int, durationMs: Int): Int {
    val safe = positionMs.coerceAtLeast(0)
    if (durationMs <= 0) return safe
    return safe.coerceAtMost(durationMs)
}

fun audioCoverageNote(library: Library): String? {
    if (library.stories.isEmpty()) return null
    val withAudio = library.stories.count { it.hasStoryAudio || it.hasQuestionsAudio }
    if (withAudio == library.stories.size) return null
    return "Аудио в архиве: $withAudio из ${library.stories.size}. Истории без аудио открываются текстом."
}

fun listensLabel(count: Int): String {
    val n = count.coerceAtLeast(0)
    val word = when {
        n % 100 in 11..14 -> "прослушиваний"
        n % 10 == 1 -> "прослушивание"
        n % 10 in 2..4 -> "прослушивания"
        else -> "прослушиваний"
    }
    return "$n $word"
}

fun storiesLabel(count: Int): String {
    val n = count.coerceAtLeast(0)
    val word = when {
        n % 100 in 11..14 -> "историй"
        n % 10 == 1 -> "история"
        n % 10 in 2..4 -> "истории"
        else -> "историй"
    }
    return "$n $word"
}

fun importMessage(storyCount: Int, storyAudio: Int, questionAudio: Int): String =
    "Импортировано ${storiesLabel(storyCount)}. Аудио историй: $storyAudio, аудио вопросов: $questionAudio."

/**
 * Library badge. Before a grade the circle is empty and [count] is absent,
 * so a zero listen total is never shown. After a grade [count] is the listen
 * total when it is positive, and [correctFraction] is score / max.
 */
data class ListenMark(
    val count: Int?,
    val correctFraction: Float,
)

fun listenMark(progress: StoryProgress): ListenMark {
    val grade = progress.evaluation ?: return ListenMark(count = null, correctFraction = 0f)
    val fraction = if (grade.max <= 0) {
        0f
    } else {
        grade.score.coerceIn(0, grade.max).toFloat() / grade.max.toFloat()
    }
    return ListenMark(
        count = progress.listenCount.takeIf { it > 0 },
        correctFraction = fraction,
    )
}

/** Spoken label for the library circle. A blank circle says there is no attempt yet. */
fun listenMarkDescription(progress: StoryProgress): String {
    val mark = listenMark(progress)
    val grade = progress.evaluation
    if (mark.count == null && grade == null) return "Нет ответов"
    val parts = mutableListOf<String>()
    mark.count?.let { parts += listensLabel(it) }
    if (grade != null) {
        val max = grade.max.coerceAtLeast(0)
        val score = grade.score.coerceIn(0, max)
        parts += "верных $score из $max"
    }
    return parts.joinToString(", ")
}
