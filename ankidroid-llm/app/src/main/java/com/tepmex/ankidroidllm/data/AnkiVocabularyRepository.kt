package com.tepmex.ankidroidllm.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FIELD_SEP = '\u001f'
private const val TAG = "AnkiVocab"

private data class StudyCardRow(
    val cid: Long,
    val nid: Long,
    val queue: Int,
    val due: Long,
    val deckId: Long,
)

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

    suspend fun loadStudyQueueVocabulary(settings: StorySettings, maxTerms: Int): Result<List<String>> =
        withContext(Dispatchers.IO) {
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
            val cap = maxTerms.coerceAtLeast(1).coerceAtMost(MAX_TERMS_HARD_CAP)
            val rows = if (settings.deckFieldRows.isEmpty()) {
                listOf(StoryDeckFieldRow(deckName = "", fieldName = ""))
            } else {
                settings.deckFieldRows
            }
            try {
                val fromCards = loadVocabularyViaStudyCardOrder(cr, models, rows, cap)
                if (fromCards != null) {
                    if (fromCards.isEmpty()) {
                        return@withContext Result.failure(IllegalStateException("no_vocab"))
                    }
                    return@withContext Result.success(fromCards)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Card-order vocabulary failed, falling back to note search", e)
            }
            val words = LinkedHashSet<String>()
            try {
                for (row in rows) {
                    if (words.size >= cap) break
                    val deckList = if (row.deckName.isBlank()) emptyList() else listOf(row.deckName)
                    val search = buildStudyQueueSearch(deckList)
                    val remaining = (cap - words.size).coerceAtLeast(1)
                    collectVocabularyForSearch(cr, search, models, row.fieldName, words, remaining)
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

    private fun loadVocabularyViaStudyCardOrder(
        cr: android.content.ContentResolver,
        models: Map<Long, List<String>>,
        rows: List<StoryDeckFieldRow>,
        maxTerms: Int,
    ): List<String>? {
        val deckList = rows.map { it.deckName.trim() }.filter { it.isNotEmpty() }.distinct()
        val search = buildStudyQueueSearch(deckList)
        val projection = arrayOf(
            AnkiContract.CARD_ID,
            AnkiContract.CARD_NOTE_ID,
            AnkiContract.CARD_RAW_QUEUE,
            AnkiContract.CARD_RAW_DUE,
            AnkiContract.DECK_ID,
        )
        val cardRows = ArrayList<StudyCardRow>(256)
        try {
            cr.query(AnkiContract.CARDS_URI, projection, search, null, null)?.use { c ->
                val iCid = c.getColumnIndex(AnkiContract.CARD_ID)
                val iNid = c.getColumnIndex(AnkiContract.CARD_NOTE_ID)
                val iQ = c.getColumnIndex(AnkiContract.CARD_RAW_QUEUE)
                val iDue = c.getColumnIndex(AnkiContract.CARD_RAW_DUE)
                val iDid = c.getColumnIndex(AnkiContract.DECK_ID)
                if (iCid < 0 || iNid < 0 || iQ < 0 || iDue < 0 || iDid < 0) {
                    return null
                }
                while (c.moveToNext()) {
                    cardRows.add(
                        StudyCardRow(
                            cid = c.getLong(iCid),
                            nid = c.getLong(iNid),
                            queue = c.getInt(iQ),
                            due = c.getLong(iDue),
                            deckId = c.getLong(iDid),
                        ),
                    )
                }
            } ?: return null
        } catch (e: Exception) {
            Log.w(TAG, "cards URI query failed", e)
            return null
        }
        if (cardRows.isEmpty()) return emptyList()

        val sorted = cardRows.sortedWith(
            compareBy<StudyCardRow>({ queueSortTier(it.queue) }, { it.due }, { it.cid }),
        )
        val deckIdToName = loadDeckIdToName(cr)
        val noteCache = HashMap<Long, Pair<Long, String>?>()
        val out = ArrayList<String>(maxTerms.coerceAtMost(64))

        for (card in sorted) {
            if (out.size >= maxTerms) break
            val deckName = deckIdToName[card.deckId].orEmpty()
            val row = rows.firstOrNull { r ->
                !r.deckName.isBlank() && r.deckName.equals(deckName, ignoreCase = true)
            } ?: rows.firstOrNull { it.deckName.isBlank() } ?: continue

            val pair = noteCache.getOrPut(card.nid) { loadNoteMidAndFlds(cr, card.nid) } ?: continue
            val (mid, fldsRaw) = pair
            val fieldNames = models[mid] ?: emptyList()
            val idx = fieldIndex(row.fieldName, fieldNames)
            val parts = fldsRaw.split(FIELD_SEP)
            val raw = parts.getOrNull(idx)?.trim().orEmpty()
            if (raw.isEmpty()) continue
            val plain = stripToPlainText(raw)
            if (plain.isBlank() || out.contains(plain)) continue
            out.add(plain)
        }
        return out
    }

    private fun queueSortTier(rawQueue: Int): Int = when (rawQueue) {
        1 -> 0
        3 -> 1
        2 -> 2
        0 -> 3
        else -> 99
    }

    private fun loadDeckIdToName(cr: android.content.ContentResolver): Map<Long, String> {
        val map = HashMap<Long, String>()
        try {
            cr.query(
                AnkiContract.DECKS_ALL_URI,
                arrayOf(AnkiContract.DECK_ID, AnkiContract.DECK_NAME),
                null,
                null,
                null,
            )?.use { c ->
                val iId = c.getColumnIndex(AnkiContract.DECK_ID)
                val iName = c.getColumnIndex(AnkiContract.DECK_NAME)
                if (iId < 0 || iName < 0) return@use
                while (c.moveToNext()) {
                    map[c.getLong(iId)] = c.getString(iName).orEmpty()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deck id map failed", e)
        }
        return map
    }

    private fun loadNoteMidAndFlds(cr: android.content.ContentResolver, nid: Long): Pair<Long, String>? {
        val uri = Uri.withAppendedPath(AnkiContract.NOTES_URI, nid.toString())
        return try {
            cr.query(uri, arrayOf(AnkiContract.NOTE_MID, AnkiContract.NOTE_FLDS), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val iMid = c.getColumnIndex(AnkiContract.NOTE_MID)
                val iFlds = c.getColumnIndex(AnkiContract.NOTE_FLDS)
                if (iMid < 0 || iFlds < 0) return null
                c.getLong(iMid) to (c.getString(iFlds) ?: return null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Note lookup failed for nid=$nid", e)
            null
        }
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
            while (c.moveToNext() && count < maxNotes && words.size < MAX_TERMS_HARD_CAP) {
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
        private const val MAX_TERMS_HARD_CAP = 500

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
