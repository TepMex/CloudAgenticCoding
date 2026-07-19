package com.tepmex.ankientertainer.data.hanzi

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HanziMetadataDatabaseTest {
    private lateinit var db: HanziMetadataDatabase
    private lateinit var dao: HanziDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HanziMetadataDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.hanziDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun daoBatchedQueriesAndSchemaMetadata() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO hanzi (
              character, codePoint, decomposition, etymologyType, etymologyHint,
              semanticComponent, phoneticComponent, primarySource, sourceRecordId
            ) VALUES ('清', ${'清'.code}, '⿰氵青', 'pictophonetic', 'water', '氵', '青', 'test', '1')
            """.trimIndent(),
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO variant (
              sourceCharacter, targetCharacter, direction, localeOrStandard,
              isPreferred, isAmbiguous, source, sourceRecordId
            ) VALUES ('清', '清', 's2t', NULL, 1, 0, 'unihan', '1')
            """.trimIndent(),
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO simplification (
              inputCharacter, simplifiedCharacter, traditionalCharacter, classification,
              explanation, changedComponentsJson, evidenceType, confidence, source, sourceRecordId
            ) VALUES (
              '清', '清', '清', 'UNCHANGED', '清 → 清', NULL, 'derived', 1.0, 'derived_ids', '1'
            )
            """.trimIndent(),
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO mnemonic (
              character, story, language, rawScore, normalizedScore, sourcePriority,
              source, sourceRecordId, attribution, license, contentHash
            ) VALUES ('清', 'clear water', 'en', 1, 10, 100, 'seed', '1', 't', 'CC0-1.0', 'abc')
            """.trimIndent(),
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO dataset_metadata (
              id, schemaVersion, datasetVersion, buildTimestamp, sourceVersionsJson,
              sourceChecksumsJson, recordCountsJson, licenseIdentifiersJson, roomIdentityHash
            ) VALUES (
              1, 1, '1.0.0-test', '2026-01-01T00:00:00Z', '{}', '{}', '{}', '[]', NULL
            )
            """.trimIndent(),
        )

        assertEquals(1, dao.getHanzi(listOf("清")).size)
        assertEquals("氵", dao.getHanzi(listOf("清")).first().semanticComponent)
        assertEquals(1, dao.getVariants(listOf("清")).size)
        assertEquals("UNCHANGED", dao.getSimplifications(listOf("清")).first().classification)
        assertEquals("clear water", dao.getMnemonics(listOf("清")).first().story)
        val meta = dao.getDatasetMetadata()
        assertNotNull(meta)
        assertEquals(1, meta!!.schemaVersion)
        assertEquals("1.0.0-test", meta.datasetVersion)
    }

    @Test
    fun repositoryBatchesAndFormatsThroughEngine() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO hanzi (
              character, codePoint, decomposition, etymologyType, etymologyHint,
              semanticComponent, phoneticComponent, primarySource, sourceRecordId
            ) VALUES ('清', ${'清'.code}, '⿰氵青', 'pictophonetic', 'water', '氵', '青', 'test', '1')
            """.trimIndent(),
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO dataset_metadata (
              id, schemaVersion, datasetVersion, buildTimestamp, sourceVersionsJson,
              sourceChecksumsJson, recordCountsJson, licenseIdentifiersJson, roomIdentityHash
            ) VALUES (1, 1, '1.0.0-test', 't', '{}', '{}', '{}', '[]', NULL)
            """.trimIndent(),
        )
        val repo = RoomHanziMetadataRepository(databaseProvider = { db }, cacheSize = 8)
        val engine = DefaultPromptTemplateEngine(repo)
        val result = engine.expand("{SEMANTIC}|{PHONETIC}", "清")
        assertEquals("清: semantic component 氵|清: phonetic component 青", result.prompt)
        assertTrue(repo.datasetStatus().available)
    }

    @Test
    fun curatedSimplificationPreferredInFixtureRows() = runBlocking {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO simplification (
              inputCharacter, simplifiedCharacter, traditionalCharacter, classification,
              explanation, changedComponentsJson, evidenceType, confidence, source, sourceRecordId
            ) VALUES (
              '貓', '猫', '貓', 'SINGLE_COMPONENT_REPLACEMENT',
              'curated explanation', '[]', 'curated', 0.95, 'project_curated', 'curated:貓'
            )
            """.trimIndent(),
        )
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO dataset_metadata (
              id, schemaVersion, datasetVersion, buildTimestamp, sourceVersionsJson,
              sourceChecksumsJson, recordCountsJson, licenseIdentifiersJson, roomIdentityHash
            ) VALUES (1, 1, '1.0.0-test', 't', '{}', '{}', '{}', '[]', NULL)
            """.trimIndent(),
        )
        val repo = RoomHanziMetadataRepository(databaseProvider = { db })
        val batch = repo.loadForCharacters(
            characters = listOf("貓"),
            truncated = false,
            needsHanzi = false,
            needsVariants = false,
            needsSimplifications = true,
            needsMnemonics = false,
        )
        assertEquals("curated", batch.byCharacter.getValue("貓").simplification!!.evidenceType)
        assertEquals(
            "curated explanation",
            batch.byCharacter.getValue("貓").simplification!!.explanation,
        )
    }
}
