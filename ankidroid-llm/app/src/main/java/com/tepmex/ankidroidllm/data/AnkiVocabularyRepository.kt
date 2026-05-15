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

    suspend fun loadAllDeckNames(): List<String> = withContext(Dispatchers.IO) {
        if (!hasAnkiInstalled() || !hasAnkiPermission()) {
            return@withContext emptyList()
        }
        val names = LinkedHashSet<String>()
        try {
            // Match AnkiDroid samples: null projection uses Deck.DEFAULT_PROJECTION (see FlashCardsContract).
            context.contentResolver.query(
                AnkiContract.DECKS_ALL_URI,
                null,
                null,
                null,
                null,
            )?.use { c ->
                val col = c.getColumnIndex(AnkiContract.DECK_NAME)
                if (col < 0) return@use
                while (c.moveToNext()) {
                    c.getString(col)?.trim()?.takeIf { it.isNotEmpty() }?.let { names.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deck query failed", e)
        }
        names.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    /**
     * Field names that can appear on notes in [deckName], based on note types seen in that deck.
     * Empty [deckName] means all note types (union of every model's fields).
     */
    suspend fun loadDistinctFieldNamesForDeck(deckName: String): List<String> = withContext(Dispatchers.IO) {
        if (!hasAnkiInstalled() || !hasAnkiPermission()) {
            return@withContext emptyList()
        }
        val cr = context.contentResolver
        val models = loadModelFieldNames(cr)
        if (deckName.isBlank()) {
            return@withContext unionAllFieldNames(models)
        }
        val mids = LinkedHashSet<Long>()
        val escaped = deckName.replace("\"", "\\\"")
        val search = """deck:"$escaped""""
        try {
            cr.query(AnkiContract.NOTES_URI, arrayOf(AnkiContract.NOTE_MID), search, null, null)?.use { c ->
                val midCol = c.getColumnIndex(AnkiContract.NOTE_MID)
                if (midCol < 0) return@use
                var scanned = 0
                while (c.moveToNext() && scanned < 800 && mids.size < 80) {
                    mids.add(c.getLong(midCol))
                    scanned++
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notes query for deck fields failed", e)
        }
        val out = LinkedHashSet<String>()
        mids.forEach { mid -> models[mid]?.forEach { out.add(it) } }
        if (out.isEmpty()) {
            return@withContext unionAllFieldNames(models)
        }
        out.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

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
        val rows = if (settings.deckFieldRows.isEmpty()) {
            listOf(StoryDeckFieldRow(deckName = "", fieldName = ""))
        } else {
            settings.deckFieldRows
        }
        val words = LinkedHashSet<String>()
        try {
            for (row in rows) {
                val deckList = if (row.deckName.isBlank()) emptyList() else listOf(row.deckName)
                val search = buildStudyQueueSearch(deckList)
                collectVocabularyForSearch(cr, search, models, row.fieldName, words, MAX_NOTES_PER_ROW)
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

    private fun collectVocabularyForSearch(
        cr: android.content.ContentResolver,
        search: String,
        models: Map<Long, List<String>>,
        vocabFieldName: String,
        words: LinkedHashSet<String>,
        maxNotes: Int,
    ) {
        val projection = arrayOf(AnkiContract.NOTE_MID, AnkiContract.NOTE_FLDS)
        var count = 0
        cr.query(AnkiContract.NOTES_URI, projection, search, null, null)?.use { c ->
            val midCol = c.getColumnIndex(AnkiContract.NOTE_MID)
            val fldsCol = c.getColumnIndex(AnkiContract.NOTE_FLDS)
            if (midCol < 0 || fldsCol < 0) {
                return@use
            }
            while (c.moveToNext() && count < maxNotes) {
                val mid = c.getLong(midCol)
                val fldsRaw = c.getString(fldsCol) ?: continue
                val fieldNames = models[mid] ?: emptyList()
                val idx = fieldIndex(vocabFieldName, fieldNames)
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
    }

    private fun loadModelFieldNames(cr: android.content.ContentResolver): Map<Long, List<String>> {
        val map = HashMap<Long, List<String>>()
        try {
            cr.query(AnkiContract.MODELS_URI, null, null, null, null)
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

    private fun unionAllFieldNames(models: Map<Long, List<String>>): List<String> {
        val out = LinkedHashSet<String>()
        models.values.forEach { list -> list.forEach { out.add(it) } }
        return out.sortedWith(String.CASE_INSENSITIVE_ORDER)
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
        private const val MAX_NOTES_PER_ROW = 250

        fun buildStudyQueueSearch(deckNames: List<String>): String {
            val queue = "(is:due OR is:learn OR is:new)"
            if (deckNames.isEmpty()) return queue
            val deckExpr = deckNames.joinToString(" OR ") { name ->
                val escaped = name.replace("\"", "\\\"")
                """deck:"$escaped""""
            }
            return "$queue ($deckExpr)"
        }
    }
}
