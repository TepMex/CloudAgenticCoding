package com.tepmex.ankidashboard.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val FIELD_SEP = '\u001f'
private const val TAG = "AnkiDroidRepo"

class AnkiDroidRepository(private val context: Context) {

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

    suspend fun loadDeckNamesAndIds(): Map<String, Long> = withContext(Dispatchers.IO) {
        if (!hasAnkiInstalled() || !hasAnkiPermission()) {
            return@withContext emptyMap()
        }
        val out = linkedMapOf<String, Long>()
        try {
            context.contentResolver.query(
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
                    val name = c.getString(iName)?.trim().orEmpty()
                    if (name.isNotEmpty()) {
                        out[name] = c.getLong(iId)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deck query failed", e)
        }
        out
    }

    suspend fun findCardIds(deckName: String, leechesOnly: Boolean = false): List<Long> =
        withContext(Dispatchers.IO) {
            if (!hasAnkiPermission()) return@withContext emptyList()
            val escaped = deckName.replace("\"", "\\\"")
            val search = if (leechesOnly) {
                """deck:"$escaped" tag:leech"""
            } else {
                """deck:"$escaped""""
            }
            queryCardIds(search)
        }

    suspend fun getIntervals(cardIds: List<Long>): List<Int> = withContext(Dispatchers.IO) {
        if (!hasAnkiPermission() || cardIds.isEmpty()) return@withContext emptyList()
        val ivlById = hashMapOf<Long, Int>()
        for (cid in cardIds) {
            val uri = Uri.withAppendedPath(AnkiContract.CARDS_URI, cid.toString())
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(AnkiContract.CARD_ID, AnkiContract.CARD_INTERVAL),
                    null,
                    null,
                    null,
                )?.use { c ->
                    if (!c.moveToFirst()) return@use
                    val iIvl = c.getColumnIndex(AnkiContract.CARD_INTERVAL)
                    if (iIvl >= 0) {
                        ivlById[cid] = c.getInt(iIvl)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "interval query failed for $cid", e)
            }
        }
        cardIds.map { ivlById[it] ?: 0 }
    }

    suspend fun loadLeechCardsInfo(
        deckNames: List<String>,
    ): List<AnkiDroidRepository.CardInfo> = withContext(Dispatchers.IO) {
        if (!hasAnkiPermission()) return@withContext emptyList()
        val models = loadModelFieldNames()
        val out = ArrayList<CardInfo>()
        for (deckName in deckNames) {
            val ids = findCardIds(deckName, leechesOnly = true)
            for (cid in ids) {
                loadCardInfo(cid, models)?.let { out.add(it) }
            }
        }
        out
    }

    suspend fun sampleFieldNamesForDecks(
        deckNames: List<String>,
    ): Map<String, List<String>> = withContext(Dispatchers.IO) {
        if (!hasAnkiPermission()) return@withContext emptyMap()
        val models = loadModelFieldNames()
        val out = linkedMapOf<String, List<String>>()
        for (deckName in deckNames) {
            val escaped = deckName.replace("\"", "\\\"")
            val search = """deck:"$escaped""""
            val mids = linkedSetOf<Long>()
            try {
                context.contentResolver.query(
                    AnkiContract.NOTES_URI,
                    arrayOf(AnkiContract.NOTE_MID),
                    search,
                    null,
                    null,
                )?.use { c ->
                    val midCol = c.getColumnIndex(AnkiContract.NOTE_MID)
                    if (midCol < 0) return@use
                    var scanned = 0
                    while (c.moveToNext() && scanned < 50) {
                        mids.add(c.getLong(midCol))
                        scanned++
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Notes query for fields failed", e)
            }
            val fields = linkedSetOf<String>()
            for (mid in mids) {
                models[mid]?.forEach { fields.add(it) }
            }
            out[deckName] = fields.sorted()
        }
        out
    }

    private suspend fun queryCardIds(search: String): List<Long> {
        val projection = arrayOf(AnkiContract.CARD_ID)
        val ids = ArrayList<Long>()
        try {
            context.contentResolver.query(
                AnkiContract.CARDS_URI,
                projection,
                search,
                null,
                null,
            )?.use { c ->
                val col = c.getColumnIndex(AnkiContract.CARD_ID)
                if (col < 0) return@use
                while (c.moveToNext()) {
                    ids.add(c.getLong(col))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Card search failed: $search", e)
        }
        return ids
    }

    private fun loadCardInfo(
        cardId: Long,
        models: Map<Long, List<String>>,
    ): CardInfo? {
        val uri = Uri.withAppendedPath(AnkiContract.CARDS_URI, cardId.toString())
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val iNid = c.getColumnIndex(AnkiContract.CARD_NOTE_ID)
                val iDid = c.getColumnIndex(AnkiContract.CARD_DECK_ID)
                val iReps = c.getColumnIndex(AnkiContract.CARD_REPS)
                if (iNid < 0) return null
                val nid = c.getLong(iNid)
                val reps = if (iReps >= 0) c.getInt(iReps) else 0
                val deckId = if (iDid >= 0) c.getLong(iDid) else 0L
                val deckName = loadDeckName(deckId)
                val noteFields = loadNoteFields(nid, models)
                CardInfo(
                    cardId = cardId,
                    deckName = deckName,
                    noteFields = noteFields,
                    reps = reps,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "card info failed for $cardId", e)
            null
        }
    }

    private fun loadDeckName(deckId: Long): String {
        if (deckId == 0L) return ""
        return try {
            context.contentResolver.query(
                AnkiContract.DECKS_ALL_URI,
                arrayOf(AnkiContract.DECK_ID, AnkiContract.DECK_NAME),
                "${AnkiContract.DECK_ID}=?",
                arrayOf(deckId.toString()),
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return ""
                val col = c.getColumnIndex(AnkiContract.DECK_NAME)
                if (col < 0) return ""
                c.getString(col).orEmpty()
            }.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }

    private fun loadNoteFields(
        noteId: Long,
        models: Map<Long, List<String>>,
    ): Map<String, String> {
        val uri = Uri.withAppendedPath(AnkiContract.NOTES_URI, noteId.toString())
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(AnkiContract.NOTE_MID, AnkiContract.NOTE_FLDS),
                null,
                null,
                null,
            )?.use { c ->
                if (!c.moveToFirst()) return emptyMap()
                val iMid = c.getColumnIndex(AnkiContract.NOTE_MID)
                val iFlds = c.getColumnIndex(AnkiContract.NOTE_FLDS)
                if (iMid < 0 || iFlds < 0) return emptyMap()
                val mid = c.getLong(iMid)
                val fldsRaw = c.getString(iFlds) ?: return emptyMap()
                val fieldNames = models[mid] ?: emptyList()
                val parts = fldsRaw.split(FIELD_SEP)
                linkedMapOf<String, String>().apply {
                    fieldNames.forEachIndexed { idx, name ->
                        put(name, stripHtml(parts.getOrNull(idx).orEmpty()))
                    }
                }
            } ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadModelFieldNames(): Map<Long, List<String>> {
        val map = hashMapOf<Long, List<String>>()
        try {
            context.contentResolver.query(AnkiContract.MODELS_URI, null, null, null, null)
                ?.use { c ->
                    val idCol = c.getColumnIndex(AnkiContract.MODEL_ID)
                    val namesCol = c.getColumnIndex(AnkiContract.MODEL_FIELD_NAMES)
                    if (idCol < 0 || namesCol < 0) return@use
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val namesJoined = c.getString(namesCol) ?: continue
                        map[id] = namesJoined.split(FIELD_SEP).map { it.trim() }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Model query failed", e)
        }
        return map
    }

    private fun stripHtml(htmlOrText: String): String {
        val spanned = Html.fromHtml(htmlOrText, Html.FROM_HTML_MODE_COMPACT)
        return spanned.toString().replace('\n', ' ').trim()
    }

    data class CardInfo(
        val cardId: Long,
        val deckName: String,
        val noteFields: Map<String, String>,
        val reps: Int,
    )
}
