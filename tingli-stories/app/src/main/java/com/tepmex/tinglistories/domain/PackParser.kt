package com.tepmex.tinglistories.domain

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ParsedStory(
    val id: Int,
    val title: String,
    val text: String,
    val questions: List<Question>,
    val storyAudioEntry: String?,
    val questionsAudioEntry: String?,
)

data class ParsedPack(
    val level: String,
    val kind: String,
    val stories: List<ParsedStory>,
)

private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "ogg", "aac", "flac", "mp4")

fun ParsedStory.stored(): Story = Story(
    id = id,
    title = title.ifBlank { "История $id" },
    text = text,
    questions = questions,
    hasStoryAudio = storyAudioEntry != null,
    hasQuestionsAudio = questionsAudioEntry != null,
    storyExt = audioExtension(storyAudioEntry),
    questionsExt = audioExtension(questionsAudioEntry),
)

fun audioExtension(entry: String?): String {
    val ext = entry?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return if (ext in AUDIO_EXTENSIONS) ext else "mp3"
}

fun normalizeZipPath(path: String): String =
    path.replace('\\', '/').trim().removePrefix("./").trimStart('/')

fun isJsonName(name: String): Boolean {
    val normalized = normalizeZipPath(name)
    if (normalized.startsWith("__MACOSX/") || normalized.contains("/__MACOSX/")) return false
    val base = normalized.substringAfterLast('/')
    if (base.startsWith("._")) return false
    return base.endsWith(".json", ignoreCase = true)
}

/**
 * Exact zip path, then the file name at the archive root, then `audio/<file name>`.
 * The sample pack points at `audio/story_001.mp3` while the bytes sit next to the JSON.
 */
fun resolveAudio(entryNames: Set<String>, reference: String?): String? {
    if (reference.isNullOrBlank()) return null
    val usable = entryNames.filter { name ->
        val normalized = normalizeZipPath(name)
        normalized.isNotEmpty() &&
            !normalized.endsWith("/") &&
            !normalized.startsWith("__MACOSX/") &&
            !normalized.substringAfterLast('/').startsWith("._")
    }
    val byNormal = usable.associateBy { normalizeZipPath(it) }
    val wanted = normalizeZipPath(reference)
    if (wanted.isEmpty()) return null
    byNormal[wanted]?.let { return it }
    val base = wanted.substringAfterLast('/')
    if (base.isEmpty()) return null
    byNormal[base]?.let { return it }
    byNormal["audio/$base"]?.let { return it }
    val lower = byNormal.entries.associate { (key, value) -> key.lowercase() to value }
    lower[wanted.lowercase()]?.let { return it }
    lower[base.lowercase()]?.let { return it }
    return lower["audio/$base".lowercase()]
}

fun rootObject(text: String): JSONObject {
    val trimmed = text.trim().removePrefix("\uFEFF")
    if (trimmed.startsWith("[")) {
        return JSONObject().put("exercises", JSONArray(trimmed))
    }
    return JSONObject(trimmed)
}

fun isCatalog(text: String): Boolean = runCatching {
    val root = rootObject(text)
    root.optJSONArray("exercises") != null || root.optJSONArray("stories") != null
}.getOrDefault(false)

fun selectCatalogName(namedTexts: List<Pair<String, String>>): String {
    val catalogs = namedTexts.filter { (name, text) -> isJsonName(name) && isCatalog(text) }
    if (catalogs.isEmpty()) throw IllegalArgumentException("В архиве нет JSON с историями")
    return catalogs.firstOrNull { (name, _) ->
        name.substringAfterLast('/').contains("stories", ignoreCase = true)
    }?.first ?: catalogs.first().first
}

fun parseCatalog(text: String, entryNames: Set<String>): ParsedPack {
    val root = try {
        rootObject(text)
    } catch (_: JSONException) {
        throw IllegalArgumentException("JSON в архиве не разбирается")
    }
    val array = root.optJSONArray("exercises") ?: root.optJSONArray("stories")
        ?: throw IllegalArgumentException("В JSON нет списка историй")
    val used = HashSet<Int>()
    val stories = ArrayList<ParsedStory>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val explicit = readId(item.opt("id"))
        val id = if (explicit != null && explicit !in used) explicit else nextFree(used)
        used += id
        val title = item.optString("title").ifBlank { item.optString("name") }.trim()
        val body = item.optString("text")
            .ifBlank { item.optString("story") }
            .ifBlank { item.optString("passage") }
            .trim()
        stories += ParsedStory(
            id = id,
            title = title,
            text = body,
            questions = readQuestions(item.optJSONArray("questions")),
            storyAudioEntry = resolveAudio(entryNames, firstText(item, "audio_story", "story_audio", "audioStory")),
            questionsAudioEntry = resolveAudio(
                entryNames,
                firstText(item, "audio_questions", "questions_audio", "audioQuestions"),
            ),
        )
    }
    if (stories.isEmpty()) throw IllegalArgumentException("В архиве нет историй")
    return ParsedPack(
        level = root.optString("level").trim(),
        kind = root.optString("type").trim(),
        stories = stories,
    )
}

private fun readId(value: Any?): Int? = when (value) {
    null, JSONObject.NULL -> null
    is Number -> value.toInt()
    is String -> value.trim().toIntOrNull()
    else -> null
}

private fun nextFree(used: Set<Int>): Int {
    var candidate = 1
    while (candidate in used) candidate++
    return candidate
}

private fun firstText(obj: JSONObject, vararg keys: String): String? {
    for (key in keys) {
        val value = obj.optString(key).trim()
        if (value.isNotEmpty()) return value
    }
    return null
}

private fun readQuestions(array: JSONArray?): List<Question> {
    if (array == null) return emptyList()
    val questions = ArrayList<Question>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val prompt = item.optString("q")
            .ifBlank { item.optString("question") }
            .ifBlank { item.optString("prompt") }
            .trim()
        if (prompt.isEmpty()) continue
        val reference = item.optString("a")
            .ifBlank { item.optString("answer") }
            .ifBlank { item.optString("reference") }
            .trim()
        questions += Question(prompt, reference)
    }
    return questions
}
