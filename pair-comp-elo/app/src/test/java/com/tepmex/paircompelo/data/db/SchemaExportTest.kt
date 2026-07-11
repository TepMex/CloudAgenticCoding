package com.tepmex.paircompelo.data.db

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Verifies Room schema export for v1 is present and well-formed.
 * When adding schema version N+1, add a Migration and an instrumented
 * MigrationTestHelper test that upgrades from N → N+1 without destructive migration.
 */
class SchemaExportTest {

    @Test
    fun version1SchemaIsExported() {
        val stream = checkNotNull(
            javaClass.classLoader!!.getResourceAsStream(
                "com.tepmex.paircompelo.data.db.PairCompEloDatabase/1.json",
            ),
        ) { "Missing exported Room schema 1.json in test resources" }
        val json = stream.bufferedReader().use { it.readText() }
        val root = Json.parseToJsonElement(json).jsonObject
        assertThat(root["formatVersion"]!!.jsonPrimitive.int).isAtLeast(1)
        val database = root["database"]!!.jsonObject
        assertThat(database["version"]!!.jsonPrimitive.int).isEqualTo(1)
        val names = database["entities"]!!.jsonArray.map {
            it.jsonObject["tableName"]!!.jsonPrimitive.content
        }
        assertThat(names).containsAtLeast(
            "preference_lists",
            "preference_items",
            "item_comparisons",
            "list_comparisons",
        )
    }
}
