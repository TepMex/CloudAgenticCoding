package com.tepmex.tinglistories.data

import com.tepmex.tinglistories.domain.AudioKind
import com.tepmex.tinglistories.domain.Evaluation
import com.tepmex.tinglistories.domain.Library
import com.tepmex.tinglistories.domain.Story
import com.tepmex.tinglistories.domain.StoryProgress
import com.tepmex.tinglistories.domain.alignedAnswers
import com.tepmex.tinglistories.domain.isJsonName
import com.tepmex.tinglistories.domain.mergeProgress
import com.tepmex.tinglistories.domain.parseCatalog
import com.tepmex.tinglistories.domain.selectCatalogName
import com.tepmex.tinglistories.domain.shouldCountPlay
import com.tepmex.tinglistories.domain.stored
import org.json.JSONException
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipException
import java.util.zip.ZipFile

data class ImportOutcome(
    val library: Library,
    val storyCount: Int,
    val storyAudioCount: Int,
    val questionAudioCount: Int,
)

/**
 * One pack on disk. Zip entry names are never used as output paths: audio is
 * written only as `audio/<id>/story.<ext>` and `audio/<id>/questions.<ext>`.
 */
class LibraryStore(private val root: File) {
    private val lock = Any()
    private val libraryFile: File get() = File(root, "library.json")

    fun load(): Library = synchronized(lock) { readOrEmpty() }

    fun importZip(zipFile: File): ImportOutcome = synchronized(lock) {
        if (!zipFile.isFile) throw IllegalArgumentException("Архив не найден")
        val previous = readOrEmpty()
        try {
            ZipFile(zipFile, StandardCharsets.UTF_8).use { zip ->
                val names = zip.entries().asSequence()
                    .map { it.name }
                    .filter { !it.endsWith("/") }
                    .toList()
                val namedTexts = names.filter { isJsonName(it) }.map { name ->
                    val entry = zip.getEntry(name)
                        ?: throw IllegalArgumentException("В архиве нет $name")
                    val text = zip.getInputStream(entry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                    name to text
                }
                val catalogName = selectCatalogName(namedTexts)
                val catalogText = namedTexts.first { it.first == catalogName }.second
                val catalog = parseCatalog(catalogText, names.toSet())
                val stories = catalog.stories.map { it.stored() }
                val progress = mergeProgress(previous.stories, previous.progress, stories)
                val staging = File(root, "audio-staging")
                if (staging.exists()) staging.deleteRecursively()
                staging.mkdirs()
                var storyAudio = 0
                var questionAudio = 0
                for (parsed in catalog.stories) {
                    val story = stories.first { it.id == parsed.id }
                    if (parsed.storyAudioEntry != null) {
                        copyEntry(zip, parsed.storyAudioEntry, File(staging, "${story.id}/story.${story.storyExt}"))
                        storyAudio++
                    }
                    if (parsed.questionsAudioEntry != null) {
                        copyEntry(
                            zip,
                            parsed.questionsAudioEntry,
                            File(staging, "${story.id}/questions.${story.questionsExt}"),
                        )
                        questionAudio++
                    }
                }
                val audioRoot = File(root, "audio")
                if (audioRoot.exists()) audioRoot.deleteRecursively()
                if (!staging.renameTo(audioRoot)) {
                    staging.copyRecursively(audioRoot, overwrite = true)
                    staging.deleteRecursively()
                }
                val library = Library(
                    level = catalog.level,
                    kind = catalog.kind,
                    stories = stories,
                    progress = progress,
                )
                write(library)
                ImportOutcome(
                    library = library,
                    storyCount = stories.size,
                    storyAudioCount = storyAudio,
                    questionAudioCount = questionAudio,
                )
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (_: ZipException) {
            throw IllegalArgumentException("Архив повреждён или это не zip")
        } catch (_: JSONException) {
            throw IllegalArgumentException("JSON в архиве не разбирается")
        }
    }

    fun recordListen(storyId: Int): Library = synchronized(lock) {
        val library = readOrEmpty()
        if (library.story(storyId) == null) return library
        val previous = library.progressOf(storyId)
        val next = library.copy(
            progress = library.progress + (storyId to previous.copy(listenCount = previous.listenCount + 1)),
        )
        write(next)
        next
    }

    fun saveAttempt(storyId: Int, answers: List<String>, evaluation: Evaluation): Library = synchronized(lock) {
        val library = readOrEmpty()
        val story = library.story(storyId) ?: return library
        val previous = library.progressOf(storyId)
        val progress = previous.copy(
            answers = alignedAnswers(story.questions.size, answers),
            evaluation = evaluation,
        )
        val next = library.copy(progress = library.progress + (storyId to progress))
        write(next)
        next
    }

    fun resetAnswer(storyId: Int): Library = synchronized(lock) {
        val library = readOrEmpty()
        val previous = library.progress[storyId] ?: return library
        val next = library.copy(
            progress = library.progress + (storyId to StoryProgress(listenCount = previous.listenCount)),
        )
        write(next)
        next
    }

    fun audioFile(story: Story, kind: AudioKind): File? {
        if (!shouldCountPlay(story, kind)) return null
        val name = when (kind) {
            AudioKind.STORY -> "story.${story.storyExt}"
            AudioKind.QUESTIONS -> "questions.${story.questionsExt}"
        }
        return File(root, "audio/${story.id}/$name")
    }

    private fun copyEntry(zip: ZipFile, entryName: String, dest: File) {
        val rootCanon = root.canonicalFile
        dest.parentFile?.mkdirs()
        val destCanon = dest.canonicalFile
        val prefix = rootCanon.path + File.separator
        if (!destCanon.path.startsWith(prefix)) {
            throw IllegalArgumentException("Недопустимый путь аудио")
        }
        val entry = zip.getEntry(entryName) ?: throw IllegalArgumentException("В архиве нет $entryName")
        zip.getInputStream(entry).use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun readOrEmpty(): Library {
        if (!libraryFile.isFile) return Library()
        return try {
            LibraryJson.decode(libraryFile.readText(StandardCharsets.UTF_8))
        } catch (_: Exception) {
            Library()
        }
    }

    private fun write(library: Library) {
        root.mkdirs()
        val tmp = File(root, "library.json.tmp")
        tmp.writeText(LibraryJson.encode(library), StandardCharsets.UTF_8)
        if (!tmp.renameTo(libraryFile)) {
            libraryFile.writeText(tmp.readText(StandardCharsets.UTF_8), StandardCharsets.UTF_8)
            tmp.delete()
        }
    }
}
