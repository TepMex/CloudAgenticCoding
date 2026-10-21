package com.tepmex.extensivereadingtutor

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tepmex.extensivereadingtutor.storage.room.MIGRATION_1_2
import com.tepmex.extensivereadingtutor.storage.room.TutorDatabase
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
            .addMigrations(MIGRATION_1_2)
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
}
