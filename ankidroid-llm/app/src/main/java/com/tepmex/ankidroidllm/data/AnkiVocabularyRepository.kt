package com.tepmex.ankidroidllm.data

import android.content.Context
import android.content.pm.PackageManager
import android.text.Html
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FIELD_SEP = '\u001f'
private const val TAG = "AnkiVocab"

class AnkiVocabularyRepository(private val context: Context) {

    fun hasAnkiInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(
            "com.ichi2.anki",
            PackageManager.PackageInfoFlags.of(0),
        )
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasAnkiPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, AnkiContract.READ_WRITE_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadStudyQueueVocabulary(settings: StorySettings): Result<List<String>> = withContext(Dispatchers.IO) {
        if (!hasAnkiInstalled()) {
            return@withContext Result.failure(IllegalStateException("anki_missing"))
        }
        if (!hasAnkiPermission()) {
            return@withContext Result.failure(SecurityException("anki_permission"))
        }
        val cr = context.contentResolver
        val models = loadModelFieldNames(cr)
        if (models.isEmpty()) {
            Log.w(TAG, "No models returned from AnkiDroid")
        }
        val search = buildStudyQueueSearch(settings.deckNamesCsv)
        val projection = arrayOf(AnkiContract.NOTE_MID, AnkiContract.NOTE_FLDS)
        val words = LinkedHashSet<String>()
        try {
            cr.query(AnkiContract.NOTES_URI, projection, search, null, null)?.use { c ->
                val midCol = c.getColumnIndex(AnkiContract.NOTE_MID)
                val fldsCol = c.getColumnIndex(AnkiContract.NOTE_FLDS)
                if (midCol < 0 || fldsCol < 0) {
                    return@use
                }
                var count = 0
                while (c.moveToNext() && count < MAX_NOTES) {
                    val mid = c.getLong(midCol)
                    val fldsRaw = c.getString(fldsCol) ?: continue
                    val fieldNames = models[mid] ?: emptyList()
                    val idx = fieldIndex(settings.vocabFieldName, fieldNames)
                    val parts = fldsRaw.split(FIELD_SEP)
                    val raw = parts.getOrNull(idx)?.trim().orEmpty()
                    if (raw.isNotEmpty()) {
                        val plain = stripToPlainText(raw)
                        if (plain.isNotBlank()) {
                            words.add(plain)
                        }
                    }
                    count++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Anki query failed", e)
            return@withContext Result.failure(e)
        }
        if (words.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("no_vocab"))
        }
        Result.success(words.toList())
    }

    private fun loadModelFieldNames(cr: android.content.ContentResolver): Map<Long, List<String>> {
        val map = HashMap<Long, List<String>>()
        try {
            cr.query(AnkiContract.MODELS_URI, arrayOf(AnkiContract.MODEL_ID, AnkiContract.MODEL_FIELD_NAMES), null, null, null)
                ?.use { c ->
                    val idCol = c.getColumnIndex(AnkiContract.MODEL_ID)
                    val namesCol = c.getColumnIndex(AnkiContract.MODEL_FIELD_NAMES)
                    if (idCol < 0 || namesCol < 0) return@use
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val namesJoined = c.getString(namesCol) ?: continue
                        val names = namesJoined.split(FIELD_SEP).map { it.trim() }
                        map[id] = names
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Model query failed", e)
        }
        return map
    }

    private fun fieldIndex(vocabFieldName: String, fieldNames: List<String>): Int {
        val target = vocabFieldName.trim()
        if (target.isEmpty()) return 0
        val i = fieldNames.indexOfFirst { it.equals(target, ignoreCase = true) }
        return if (i >= 0) i else 0
    }

    private fun stripToPlainText(htmlOrText: String): String {
        val spanned = Html.fromHtml(htmlOrText, Html.FROM_HTML_MODE_COMPACT)
        return spanned.toString().replace('\n', ' ').trim()
    }

    companion object {
        private const val MAX_NOTES = 250

        fun buildStudyQueueSearch(deckNamesCsv: String): String {
            val queue = "(is:due OR is:learn OR is:new)"
            val decks = deckNamesCsv.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (decks.isEmpty()) return queue
            val deckExpr = decks.joinToString(" OR ") { name ->
                val escaped = name.replace("\"", "\\\"")
                """deck:"$escaped""""
            }
            return "$queue ($deckExpr)"
        }
    }
}
