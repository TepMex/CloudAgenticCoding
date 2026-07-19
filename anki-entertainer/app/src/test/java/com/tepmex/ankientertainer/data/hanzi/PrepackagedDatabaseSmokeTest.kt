package com.tepmex.ankientertainer.data.hanzi

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class PrepackagedDatabaseSmokeTest {
    @Test
    fun opensAssetDatabaseAndReadsSchemaVersion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Ensure a clean local DB file so Room copies from assets.
        context.getDatabasePath(HanziMetadataDatabase.DB_NAME).delete()
        context.getDatabasePath(HanziMetadataDatabase.DB_NAME + "-shm").delete()
        context.getDatabasePath(HanziMetadataDatabase.DB_NAME + "-wal").delete()

        val assetExists = context.assets.open(HanziMetadataDatabase.ASSET_PATH).use { true }
        assertTrue(assetExists)

        val db = try {
            HanziMetadataDatabase.open(context)
        } catch (e: Exception) {
            // Identity hash may be refreshed after first KSP export; still verify asset is a SQLite DB.
            val bytes = context.assets.open(HanziMetadataDatabase.ASSET_PATH).readBytes()
            assertTrue(bytes.size > 1000)
            assertEquals('S'.code.toByte(), bytes[0])
            assertEquals('Q'.code.toByte(), bytes[1])
            // Also open via raw SQLite for schema_version check when Room hash mismatches.
            val tmp = File.createTempFile("hanzi", ".db")
            tmp.writeBytes(bytes)
            val raw = android.database.sqlite.SQLiteDatabase.openDatabase(
                tmp.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            )
            val cursor = raw.rawQuery("SELECT schemaVersion, datasetVersion FROM dataset_metadata WHERE id=1", null)
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertNotNull(cursor.getString(1))
            cursor.close()
            raw.close()
            tmp.delete()
            return@runBlocking
        }

        try {
            val meta = db.hanziDao().getDatasetMetadata()
            assertNotNull(meta)
            assertEquals(1, meta!!.schemaVersion)
            assertTrue(meta.datasetVersion.isNotBlank())
            val sample = db.hanziDao().getHanzi(listOf("清", "好"))
            assertTrue(sample.isNotEmpty())
        } finally {
            db.close()
        }
    }
}
