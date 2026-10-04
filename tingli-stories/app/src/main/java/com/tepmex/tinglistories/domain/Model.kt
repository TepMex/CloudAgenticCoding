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

fun storySubtitle(story: Story, progress: StoryProgress): String {
    val status = if (progress.completed) {
        val grade = progress.evaluation
        "Пройдена · ${grade?.score ?: 0} из ${grade?.max ?: story.questions.size}"
    } else {
        "Не пройдена"
    }
    val audio = when {
        story.hasStoryAudio && story.hasQuestionsAudio -> status
        story.hasStoryAudio || story.hasQuestionsAudio -> "$status · аудио неполное"
        else -> "$status · без аудио"
    }
    return audio
}
