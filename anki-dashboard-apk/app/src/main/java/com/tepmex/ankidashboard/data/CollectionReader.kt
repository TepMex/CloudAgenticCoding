package com.tepmex.ankidashboard.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Read-only access to Anki's collection.anki2 for revlog-based statistics.
 * Mirrors the web app's collection.worker.js queries.
 */
class CollectionReader(private val context: Context) {

    private var db: SQLiteDatabase? = null
    private var decksById: Map<String, DeckEntry> = emptyMap()
    private var deckNameById: Map<String, String> = emptyMap()
    private var fieldsByNotetypeId: Map<String, List<String>> = emptyMap()
    private var modelsById: Map<String, ModelEntry> = emptyMap()

    fun isOpen(): Boolean = db != null

    fun openFromUri(uriString: String): Boolean {
        close()
        return try {
            context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")?.use { pfd ->
                val tmp = File(context.cacheDir, "collection-copy.anki2")
                context.contentResolver.openInputStream(Uri.parse(uriString))?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                }
                openFile(tmp)
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "openFromUri failed", e)
            false
        }
    }

    fun openCachedCollection(): Boolean {
        close()
        val file = com.tepmex.ankidashboard.data.sync.CollectionStore.collectionFile(context)
        if (!file.isFile || !file.canRead()) return false
        // Copy before open: avoids SQLITE_CANTOPEN on some devices and matches openFromUri.
        return try {
            val tmp = File(context.cacheDir, "collection-ankiweb-copy.anki2")
            file.inputStream().use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            openFile(tmp)
        } catch (e: Exception) {
            Log.e(TAG, "openCachedCollection failed", e)
            false
        }
    }

    fun openDefaultPath(): Boolean {
        close()
        for (path in defaultCollectionPaths()) {
            val file = File(path)
            if (file.isFile && file.canRead()) {
                if (openFile(file)) return true
            }
        }
        return false
    }

    fun close() {
        try {
            db?.close()
        } catch (_: Exception) {
        }
        db = null
        decksById = emptyMap()
        deckNameById = emptyMap()
        fieldsByNotetypeId = emptyMap()
        modelsById = emptyMap()
    }

    fun deckNamesAndIds(): Map<String, Long> {
        val out = linkedMapOf<String, Long>()
        for ((id, deck) in decksById) {
            out[deck.name] = id.toLongOrNull() ?: continue
        }
        return out
    }

    fun findCards(query: String): List<Long> {
        val db = db ?: return emptyList()
        // Web app uses `"deck:Name"`; older builds used `deck:"Name"`.
        val deckMatch = DECK_QUERY_PATTERN.find(query) ?: return emptyList()
        val deckName = deckMatch.groupValues[1]
        val deckIds = resolveDeckIds(deckName)
        if (deckIds.isEmpty()) return emptyList()
        val idList = deckIds.joinToString(",")
        val sql = if (query.contains("tag:leech")) {
            """
            SELECT c.id FROM cards c
            JOIN notes n ON c.nid = n.id
            WHERE c.did IN ($idList) AND n.tags LIKE '% leech%'
            """.trimIndent()
        } else {
            "SELECT c.id FROM cards c WHERE c.did IN ($idList)"
        }
        return queryLongColumn(db, sql)
    }

    fun getIntervals(cardIds: List<Long>): List<Int> {
        if (cardIds.isEmpty()) return emptyList()
        val db = db ?: return List(cardIds.size) { 0 }
        val idList = cardIds.joinToString(",")
        val ivlById = hashMapOf<Long, Int>()
        db.rawQuery("SELECT id, ivl FROM cards WHERE id IN ($idList)", null).use { c ->
            val iId = c.getColumnIndex("id")
            val iIvl = c.getColumnIndex("ivl")
            while (c.moveToNext()) {
                ivlById[c.getLong(iId)] = c.getInt(iIvl)
            }
        }
        return cardIds.map { ivlById[it] ?: 0 }
    }

    fun getNumCardsReviewedByDay(): List<Pair<String, Int>> {
        val db = db ?: return emptyList()
        val sql = """
            SELECT strftime('%Y-%m-%d', id / 1000, 'unixepoch') AS day, COUNT(*)
            FROM revlog
            GROUP BY day
            ORDER BY day
        """.trimIndent()
        val out = ArrayList<Pair<String, Int>>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out.add(c.getString(0) to c.getInt(1))
            }
        }
        return out
    }

    fun getReviewsOfCards(cardIds: List<Long>): Map<Long, List<CardReview>> {
        if (cardIds.isEmpty()) return emptyMap()
        val db = db ?: return emptyMap()
        val out = hashMapOf<Long, MutableList<CardReview>>()
        val batchSize = 500
        var i = 0
        while (i < cardIds.size) {
            val batch = cardIds.subList(i, minOf(i + batchSize, cardIds.size))
            val idList = batch.joinToString(",")
            val sql = """
                SELECT cid, id, ease, time, ivl
                FROM revlog
                WHERE cid IN ($idList)
                ORDER BY id
            """.trimIndent()
            db.rawQuery(sql, null).use { c ->
                val iCid = c.getColumnIndex("cid")
                val iId = c.getColumnIndex("id")
                val iEase = c.getColumnIndex("ease")
                val iTime = c.getColumnIndex("time")
                val iIvl = c.getColumnIndex("ivl")
                while (c.moveToNext()) {
                    val cid = c.getLong(iCid)
                    val list = out.getOrPut(cid) { ArrayList() }
                    list.add(
                        CardReview(
                            id = c.getLong(iId),
                            ease = c.getInt(iEase),
                            time = c.getInt(iTime),
                            ivl = c.getInt(iIvl),
                        ),
                    )
                }
            }
            i += batchSize
        }
        return out
    }

    fun cardsInfo(cardIds: List<Long>): List<CardInfoRow> {
        if (cardIds.isEmpty()) return emptyList()
        val db = db ?: return emptyList()
        val idList = cardIds.joinToString(",")
        val sql = """
            SELECT c.id, c.did, c.nid, n.mid, n.flds
            FROM cards c
            JOIN notes n ON c.nid = n.id
            WHERE c.id IN ($idList)
        """.trimIndent()
        val out = ArrayList<CardInfoRow>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val cardId = c.getLong(0)
                val did = c.getLong(1)
                val noteId = c.getLong(2)
                val mid = c.getLong(3)
                val flds = c.getString(4) ?: ""
                val fieldNames = getFieldNames(mid.toString())
                val values = flds.split(FIELD_SEP)
                val noteFields = linkedMapOf<String, String>()
                fieldNames.forEachIndexed { idx, name ->
                    noteFields[name] = values.getOrNull(idx)?.trim().orEmpty()
                }
                out.add(
                    CardInfoRow(
                        cardId = cardId,
                        noteId = noteId,
                        deckName = deckNameById[did.toString()].orEmpty(),
                        noteFields = noteFields,
                    ),
                )
            }
        }
        return out
    }

    private fun openFile(file: File): Boolean {
        return try {
            db = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            loadMetadata()
            true
        } catch (e: Exception) {
            Log.e(TAG, "openFile failed: ${file.absolutePath}", e)
            close()
            false
        }
    }

    private fun loadMetadata() {
        val db = db ?: return
        decksById = emptyMap()
        deckNameById = emptyMap()
        if (tableExists(db, "decks")) {
            loadDecksFromTable(db)
        }
        if (decksById.isEmpty()) {
            loadDecksFromColJson(db)
        }
        fieldsByNotetypeId = emptyMap()
        if (tableExists(db, "fields")) {
            loadFieldsFromTable(db)
        }
        loadModelsFromColJson(db)
    }

    private fun loadDecksFromTable(db: SQLiteDatabase) {
        val map = linkedMapOf<String, DeckEntry>()
        val names = hashMapOf<String, String>()
        db.rawQuery("SELECT id, name FROM decks", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0).toString()
                val name = humanizeDeckName(c.getString(1))
                map[id] = DeckEntry(name)
                names[id] = name
            }
        }
        decksById = map
        deckNameById = names
    }

    private fun loadDecksFromColJson(db: SQLiteDatabase) {
        db.rawQuery("SELECT decks FROM col WHERE id = 1", null).use { c ->
            if (!c.moveToFirst()) return
            val raw = c.getString(0) ?: return
            if (raw.isBlank() || raw == "{}") return
            val parsed = parseDecksJsonObject(raw) ?: parseDecksJsonRegex(raw)
            if (parsed.isEmpty()) return
            decksById = parsed
            deckNameById = parsed.mapValues { it.value.name }
        }
    }

    private fun loadFieldsFromTable(db: SQLiteDatabase) {
        val map = hashMapOf<String, MutableList<String>>()
        db.rawQuery(
            "SELECT ntid, ord, name FROM fields ORDER BY ntid, ord",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val ntid = c.getLong(0).toString()
                val ord = c.getInt(1)
                val name = c.getString(2)
                val list = map.getOrPut(ntid) { ArrayList() }
                while (list.size <= ord) list.add("")
                list[ord] = name
            }
        }
        fieldsByNotetypeId = map.mapValues { (_, v) -> v.filter { it.isNotEmpty() } }
    }

    private fun loadModelsFromColJson(db: SQLiteDatabase) {
        db.rawQuery("SELECT models FROM col WHERE id = 1", null).use { c ->
            if (!c.moveToFirst()) {
                modelsById = emptyMap()
                return
            }
            val modelsRaw = c.getString(0).orEmpty()
            modelsById = parseModelsJsonObject(modelsRaw) ?: parseModelsJsonRegex(modelsRaw)
        }
    }

    private fun getFieldNames(mid: String): List<String> {
        fieldsByNotetypeId[mid]?.let { if (it.isNotEmpty()) return it }
        return modelsById[mid]?.fieldNames ?: emptyList()
    }

  private fun resolveDeckIds(deckName: String): List<Long> {
        val ids = ArrayList<Long>()
        val target = deckName.trim()
        if (target.isEmpty()) return ids
        for ((id, deck) in decksById) {
            val name = deck.name
            if (name.equals(target, ignoreCase = true) ||
                name.startsWith("$target::", ignoreCase = true)
            ) {
                id.toLongOrNull()?.let { ids.add(it) }
            }
        }
        return ids
    }

    private fun tableExists(db: SQLiteDatabase, name: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(name),
        ).use { c -> return c.moveToFirst() }
    }

    private fun queryLongColumn(db: SQLiteDatabase, sql: String): List<Long> {
        val out = ArrayList<Long>()
        db.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                out.add(c.getLong(0))
            }
        }
        return out
    }

    data class CardInfoRow(
        val cardId: Long,
        val noteId: Long,
        val deckName: String,
        val noteFields: Map<String, String>,
    )

    private data class DeckEntry(val name: String)
    private data class ModelEntry(val fieldNames: List<String>)

    companion object {
        private const val TAG = "CollectionReader"
        private const val FIELD_SEP = '\u001f'
        private val DECK_QUERY_PATTERN = Regex("""(?:"deck:|deck:")([^"]+)""")

        fun defaultCollectionPaths(): List<String> {
            val base = Environment.getExternalStorageDirectory()
            return listOf(
                File(base, "com.ichi2.anki/collection.anki2").absolutePath,
                File(base, "AnkiDroid/collection.anki2").absolutePath,
                File(base, "Download/AnkiDroid/collection.anki2").absolutePath,
                File(base, "Android/data/com.ichi2.anki/files/collection.anki2").absolutePath,
            )
        }

        private fun humanizeDeckName(name: String?): String =
            (name ?: "").replace('\u001f', ':')

        private fun parseDecksJsonObject(raw: String): Map<String, DeckEntry>? {
            return try {
                val root = JSONObject(raw)
                val out = linkedMapOf<String, DeckEntry>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val deck = root.optJSONObject(id) ?: continue
                    val name = humanizeDeckName(deck.optString("name", ""))
                    if (name.isNotEmpty()) {
                        out[id] = DeckEntry(name)
                    }
                }
                out.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                Log.w(TAG, "parseDecksJsonObject failed, using regex fallback", e)
                null
            }
        }

        private fun parseDecksJsonRegex(raw: String): Map<String, DeckEntry> {
            val out = linkedMapOf<String, DeckEntry>()
            val idPattern = Regex(""""(\d+)"\s*:\s*\{""")
            val namePattern = Regex(""""name"\s*:\s*"((?:\\.|[^"\\])*)"""")
            for (match in idPattern.findAll(raw)) {
                val id = match.groupValues[1]
                val chunkStart = match.range.last + 1
                val chunk = raw.substring(chunkStart, minOf(chunkStart + 800, raw.length))
                val nameMatch = namePattern.find(chunk) ?: continue
                val name = nameMatch.groupValues[1]
                    .replace("\\\\", "\\")
                    .replace("\\\"", "\"")
                out[id] = DeckEntry(humanizeDeckName(name))
            }
            return out
        }

        private fun parseModelsJsonObject(raw: String): Map<String, ModelEntry>? {
            if (raw.isBlank() || raw == "{}") return emptyMap()
            return try {
                val root = JSONObject(raw)
                val out = linkedMapOf<String, ModelEntry>()
                val keys = root.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val model = root.optJSONObject(id) ?: continue
                    val flds = model.optJSONArray("flds") ?: continue
                    val names = ArrayList<String>()
                    for (i in 0 until flds.length()) {
                        val fld = flds.optJSONObject(i) ?: continue
                        val name = fld.optString("name", "")
                        if (name.isNotEmpty()) names.add(name)
                    }
                    if (names.isNotEmpty()) {
                        out[id] = ModelEntry(names)
                    }
                }
                out
            } catch (e: Exception) {
                Log.w(TAG, "parseModelsJsonObject failed, using regex fallback", e)
                null
            }
        }

        private fun parseModelsJsonRegex(raw: String): Map<String, ModelEntry> {
            if (raw.isBlank() || raw == "{}") return emptyMap()
            val out = linkedMapOf<String, ModelEntry>()
            val idPattern = Regex(""""(\d+)"\s*:\s*\{""")
            val fldPattern = Regex(""""name"\s*:\s*"((?:\\.|[^"\\])*)"""")
            for (match in idPattern.findAll(raw)) {
                val id = match.groupValues[1]
                val chunk = raw.substring(match.range.first, minOf(match.range.first + 4000, raw.length))
                if (!chunk.contains("\"flds\"")) continue
                val names = fldPattern.findAll(chunk).map { it.groupValues[1] }.toList()
                if (names.isNotEmpty()) {
                    out[id] = ModelEntry(names)
                }
            }
            return out
        }
    }
}
