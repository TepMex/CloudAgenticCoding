package com.tepmex.extensivereadingtutor

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tepmex.extensivereadingtutor.storage.room.MIGRATION_2_3
import com.tepmex.extensivereadingtutor.storage.room.TutorDatabase
import com.tepmex.extensivereadingtutor.storage.room.withTutorSchema
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RoomMigrationTest {
    @Test
    fun v1LessonSurvivesTheDocumentTableMigration() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = context.getDatabasePath("migrate-v1.db")
        file.parentFile?.mkdirs()
        if (file.exists()) file.delete()
        val schemaFile = File("schemas/com.tepmex.extensivereadingtutor.storage.room.TutorDatabase/1.json")
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val database = root.getValue("database").jsonObject
        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        raw.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '${database.getValue("identityHash").jsonPrimitive.content}')")
        for (entity in database.getValue("entities").jsonArray) {
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            raw.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            for (index in entity.jsonObject["indices"]?.jsonArray.orEmpty()) {
                raw.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        raw.execSQL("INSERT INTO learners (id, createdAt) VALUES ('local', 0)")
        raw.execSQL(
            "INSERT INTO lesson_sessions (id, bookId, fragmentId, learnerId, strategyVersion, createdAt, status, updatedAt, hintsJson) " +
                "VALUES ('s1', 'b1', 'f1', 'local', 'strategy-v1', 0, 'AWAITING_ANSWERS', 0, '[]')",
        )
        raw.execSQL("INSERT INTO submission_answers (sessionId, questionId, text) VALUES ('s1', 'q1', 'черновик ответа')")
        raw.version = 1
        raw.close()

        val migrated = Room.databaseBuilder(context, TutorDatabase::class.java, file.name)
            .withTutorSchema()
            .allowMainThreadQueries()
            .build()
        val session = migrated.openHelper.readableDatabase.query("SELECT status FROM lesson_sessions WHERE id = 's1'")
        assertTrue(session.moveToFirst())
        assertEquals("AWAITING_ANSWERS", session.getString(0))
        val answer = migrated.openHelper.readableDatabase.query("SELECT text FROM submission_answers WHERE sessionId = 's1'")
        assertTrue(answer.moveToFirst())
        assertEquals("черновик ответа", answer.getString(0))
        val table = migrated.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'pedagogy_documents'")
        assertTrue(table.moveToFirst())
        migrated.close()
    }

    @Test
    fun v1PartialIndexesStillOpenAfterTheBookIndexIsDropped() {
        openInstalledCopy(1)
    }

    @Test
    fun v2PartialIndexesStillOpenAfterTheBookIndexIsDropped() {
        openInstalledCopy(2)
    }

    private fun openInstalledCopy(version: Int) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = context.getDatabasePath("migrate-partial-$version.db")
        file.parentFile?.mkdirs()
        file.parentFile?.listFiles()?.filter { it.name.startsWith(file.name) }?.forEach { it.delete() }
        installExportedSchema(file, version)
        val raw = android.database.sqlite.SQLiteDatabase.openDatabase(file.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_active_book ON lesson_sessions(bookId) WHERE status != 'COMPLETED'")
        raw.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_active_fragment ON lesson_sessions(fragmentId) WHERE status != 'COMPLETED'")
        raw.execSQL(
            "INSERT INTO lesson_sessions (id, bookId, fragmentId, learnerId, strategyVersion, createdAt, status, updatedAt, hintsJson) " +
                "VALUES ('s1', 'b1', 'f1', 'local', 'strategy-v1', 0, 'AWAITING_ANSWERS', 0, '[]')",
        )
        raw.close()

        val migrated = Room.databaseBuilder(context, TutorDatabase::class.java, file.name)
            .withTutorSchema()
            .allowMainThreadQueries()
            .build()
        val sql = migrated.openHelper.writableDatabase
        val kept = sql.query("SELECT status FROM lesson_sessions WHERE id = 's1'")
        assertTrue(kept.moveToFirst())
        assertEquals("AWAITING_ANSWERS", kept.getString(0))
        val indexes = sql.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'lesson_sessions'")
        val names = mutableListOf<String>()
        while (indexes.moveToNext()) names += indexes.getString(0)
        assertTrue(names.contains("one_active_fragment"))
        assertTrue("one_active_book" !in names)
        sql.execSQL(
            "INSERT INTO lesson_sessions (id, bookId, fragmentId, learnerId, strategyVersion, createdAt, status, updatedAt, hintsJson) " +
                "VALUES ('s2', 'b1', 'f2', 'local', 'strategy-v2', 0, 'CREATED', 0, '[]')",
        )
        try {
            sql.execSQL(
                "INSERT INTO lesson_sessions (id, bookId, fragmentId, learnerId, strategyVersion, createdAt, status, updatedAt, hintsJson) " +
                    "VALUES ('s3', 'b1', 'f2', 'local', 'strategy-v2', 0, 'CREATED', 0, '[]')",
            )
            fail("two open lessons on one fragment")
        } catch (error: android.database.sqlite.SQLiteConstraintException) {
            assertTrue(error.message.orEmpty().isNotEmpty())
        }
        migrated.close()
    }

    @Test
    fun twoFragmentsOfOneBookCanBothKeepAnOpenLesson() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val file = context.getDatabasePath("two-open-lessons.db")
        if (file.exists()) file.delete()
        val database = Room.databaseBuilder(context, TutorDatabase::class.java, file.name)
            .withTutorSchema()
            .allowMainThreadQueries()
            .build()
        val sql = database.openHelper.writableDatabase
        sql.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS one_active_book ON lesson_sessions(bookId) WHERE status != 'COMPLETED'")
        MIGRATION_2_3.migrate(sql)
        sql.execSQL(
            "INSERT INTO lesson_sessions (id, bookId, fragmentId, learnerId, strategyVersion, createdAt, status, updatedAt, hintsJson) " +
                "VALUES ('s1', 'b1', 'f1', 'local', 'strategy-v2', 0, 'CREATED', 0, '[]')",
        )
        sql.execSQL(
            "INSERT INTO lesson_sessions (id, bookId, fragmentId, learnerId, strategyVersion, createdAt, status, updatedAt, hintsJson) " +
                "VALUES ('s2', 'b1', 'f2', 'local', 'strategy-v2', 0, 'CREATED', 0, '[]')",
        )
        val rows = sql.query("SELECT COUNT(*) FROM lesson_sessions WHERE bookId = 'b1' AND status != 'COMPLETED'")
        assertTrue(rows.moveToFirst())
        assertEquals(2, rows.getInt(0))
        database.close()
    }

    private fun installExportedSchema(file: File, version: Int) {
        val schemaFile = File("schemas/com.tepmex.extensivereadingtutor.storage.room.TutorDatabase/$version.json")
        val root = Json.parseToJsonElement(schemaFile.readText()).jsonObject
        val database = root.getValue("database").jsonObject
        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        raw.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
        raw.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, '${database.getValue("identityHash").jsonPrimitive.content}')")
        for (entity in database.getValue("entities").jsonArray) {
            val table = entity.jsonObject.getValue("tableName").jsonPrimitive.content
            raw.execSQL(entity.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            for (index in entity.jsonObject["indices"]?.jsonArray.orEmpty()) {
                raw.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            }
        }
        raw.execSQL("INSERT INTO learners (id, createdAt) VALUES ('local', 0)")
        raw.version = version
        raw.close()
    }
}
