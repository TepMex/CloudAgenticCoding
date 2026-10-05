package com.tepmex.byokassistedreader.domain

import org.json.JSONObject

fun extractJsonObject(raw: String): String {
    val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(raw)
    val body = fenced?.groupValues?.get(1) ?: raw
    val start = body.indexOf('{')
    val end = body.lastIndexOf('}')
    if (start < 0 || end < start) throw IllegalArgumentException("В ответе нет JSON")
    return body.substring(start, end + 1)
}

data class ParsedSentence(val text: String, val parts: List<StpvoPart>)

fun parseStpvo(raw: String): List<ParsedSentence> {
    val root = JSONObject(extractJsonObject(raw))
    val sentences = root.optJSONArray("sentences") ?: return emptyList()
    val out = ArrayList<ParsedSentence>(sentences.length())
    for (i in 0 until sentences.length()) {
        val item = sentences.optJSONObject(i) ?: continue
        val text = item.optString("text")
        val partsJson = item.optJSONArray("parts")
        val parts = ArrayList<StpvoPart>()
        if (partsJson != null) {
            for (j in 0 until partsJson.length()) {
                val part = partsJson.optJSONObject(j) ?: continue
                val role = StpvoRole.fromName(part.optString("role")) ?: continue
                val span = part.optString("text")
                if (span.isNotEmpty()) parts.add(StpvoPart(role, span))
            }
        }
        if (text.isNotEmpty()) out.add(ParsedSentence(text, parts))
    }
    return out
}

fun parseGloss(raw: String): GlossPage {
    val root = JSONObject(extractJsonObject(raw))
    return GlossPage(
        words = readEntries(root, "words"),
        chengyu = readEntries(root, "chengyu").ifEmpty { readEntries(root, "idioms") },
    )
}

fun parseReference(raw: String): ReferencePage {
    val root = JSONObject(extractJsonObject(raw))
    val flat = readFlatReference(root)
    return ReferencePage(
        names = readReference(root, "names", "people", "persons") + flat.named(ReferenceKind.NAME),
        places = readReference(root, "places", "locations") + flat.named(ReferenceKind.PLACE),
        terms = readReference(root, "terms", "terminology") + flat.named(ReferenceKind.TERM),
    )
}

private fun List<KindedReference>.named(kind: ReferenceKind): List<ReferenceEntry> =
    filter { it.kind == kind }.map { it.entry }

private data class KindedReference(val kind: ReferenceKind, val entry: ReferenceEntry)

private fun readReference(root: JSONObject, vararg keys: String): List<ReferenceEntry> {
    for (key in keys) {
        val found = readEntries(root, key).map { ReferenceEntry(it.word, it.explanation) }
        if (found.isNotEmpty()) return found
    }
    return emptyList()
}

private fun readFlatReference(root: JSONObject): List<KindedReference> {
    val array = root.optJSONArray("items") ?: return emptyList()
    val out = ArrayList<KindedReference>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val kind = referenceKind(
            item.optString("kind").ifEmpty { item.optString("role") }.ifEmpty { item.optString("type") },
        ) ?: continue
        val word = item.optString("word").ifEmpty { item.optString("hanzi") }.trim()
        val explanation = item.optString("explanation").ifEmpty { item.optString("gloss") }.trim()
        if (word.isNotEmpty() && explanation.isNotEmpty()) {
            out.add(KindedReference(kind, ReferenceEntry(word, explanation)))
        }
    }
    return out
}

private fun referenceKind(raw: String): ReferenceKind? = when (raw.trim().lowercase()) {
    "name", "names", "person", "people", "proper" -> ReferenceKind.NAME
    "place", "places", "location", "locations" -> ReferenceKind.PLACE
    "term", "terms", "terminology" -> ReferenceKind.TERM
    else -> null
}

private fun readEntries(root: JSONObject, key: String): List<GlossEntry> {
    val array = root.optJSONArray(key) ?: return emptyList()
    val out = ArrayList<GlossEntry>(array.length())
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val word = item.optString("word").ifEmpty { item.optString("hanzi") }
        val explanation = item.optString("explanation").ifEmpty { item.optString("gloss") }
        if (word.isNotBlank() && explanation.isNotBlank()) {
            out.add(GlossEntry(word.trim(), explanation.trim()))
        }
    }
    return out
}

fun parsePageReading(raw: String): String {
    val root = JSONObject(extractJsonObject(raw))
    val text = root.optString("text").ifEmpty { root.optString("translation") }.trim()
    if (text.isEmpty()) throw IllegalArgumentException("В ответе нет перевода")
    return text
}

fun chatCompletionsUrl(baseUrl: String): String {
    val root = baseUrl.trim().trimEnd('/')
    return when {
        root.endsWith("/chat/completions") -> root
        root.endsWith("/v1") -> "$root/chat/completions"
        else -> "$root/v1/chat/completions"
    }
}
