package com.tepmex.extensivereadingtutor.pedagogy

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun jsonSchema(serializer: KSerializer<*>, id: String): String {
    val node = schemaNode(serializer.descriptor)
    val wrapped = buildJsonObject {
        put("\$schema", "https://json-schema.org/draft/2020-12/schema")
        put("\$id", id)
        node.forEach { (key, value) -> put(key, value) }
        put("additionalProperties", false)
    }
    return wrapped.toString()
}

fun schemaNode(descriptor: SerialDescriptor): JsonObject {
    val core = coreSchema(descriptor)
    if (!descriptor.isNullable) return core
    return buildJsonObject {
        put("anyOf", buildJsonArray {
            add(core)
            add(buildJsonObject { put("type", "null") })
        })
    }
}

private fun coreSchema(descriptor: SerialDescriptor): JsonObject = when (descriptor.kind) {
    PrimitiveKind.STRING -> type("string")
    PrimitiveKind.BOOLEAN -> type("boolean")
    PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> type("integer")
    PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> type("number")
    PrimitiveKind.CHAR -> type("string")
    StructureKind.LIST -> buildJsonObject {
        put("type", "array")
        put("items", schemaNode(descriptor.getElementDescriptor(0)))
    }
    SerialKind.ENUM -> buildJsonObject {
        put("type", "string")
        put("enum", JsonArray((0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }))
    }
    StructureKind.CLASS -> buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
        val required = buildJsonArray {
            for (index in 0 until descriptor.elementsCount) {
                add(JsonPrimitive(descriptor.getElementName(index)))
            }
        }
        put("required", required)
        put("properties", buildJsonObject {
            for (index in 0 until descriptor.elementsCount) {
                put(descriptor.getElementName(index), schemaNode(descriptor.getElementDescriptor(index)))
            }
        })
    }
    StructureKind.OBJECT -> buildJsonObject {
        put("type", "object")
        put("additionalProperties", false)
    }
    is PolymorphicKind, StructureKind.MAP -> buildJsonObject { put("type", "object") }
    else -> buildJsonObject { put("type", "string") }
}

private fun type(name: String): JsonObject = buildJsonObject { put("type", name) }

val PREPARE_SCHEMA_JSON: String = jsonSchema(PrepareOutputV2.serializer(), "prepare-v2")
val EVALUATE_SCHEMA_JSON: String = jsonSchema(EvaluateOutputV2.serializer(), "evaluate-v2")
val EXPLAIN_SCHEMA_JSON: String = jsonSchema(ExplainOutputV1.serializer(), "explain-selection-v1")
