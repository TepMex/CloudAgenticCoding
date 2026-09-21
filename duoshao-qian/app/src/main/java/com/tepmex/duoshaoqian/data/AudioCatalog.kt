package com.tepmex.duoshaoqian.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.util.Base64

data class MoneyClip(
    val amountYuan: Int,
    val hanzi: String?,
    val pinyin: String?,
    val audioBase64: String,
) {
    fun audioBytes(): ByteArray = decodeBase64Audio(audioBase64)
}

data class AudioCatalog(
    val clips: Map<Int, MoneyClip>,
) {
    val amounts: Set<Int> get() = clips.keys
    fun clip(amountYuan: Int): MoneyClip? = clips[amountYuan]
}

object AudioCatalogParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): AudioCatalog {
        val root = json.parseToJsonElement(text)
        val clips = linkedMapOf<Int, MoneyClip>()
        collect(root, clips)
        return AudioCatalog(clips)
    }

    private fun collect(element: JsonElement, out: MutableMap<Int, MoneyClip>) {
        when (element) {
            is JsonObject -> {
                val asEntry = clipFromObject(element, amountHint = null)
                if (asEntry != null) {
                    out.putIfAbsent(asEntry.amountYuan, asEntry)
                }
                for ((key, value) in element) {
                    val keyAmount = parseAmount(key)
                    val nested = clipFromElement(value, keyAmount)
                    if (nested != null) {
                        out.putIfAbsent(nested.amountYuan, nested)
                    } else {
                        collect(value, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collect(it, out) }
            else -> Unit
        }
    }

    private fun clipFromElement(element: JsonElement, amountHint: Int?): MoneyClip? {
        return when (element) {
            is JsonPrimitive -> {
                val amount = amountHint ?: return null
                val audio = element.contentOrNull?.takeIf { looksLikeAudio(it) } ?: return null
                MoneyClip(amount, hanzi = null, pinyin = null, audioBase64 = stripDataUri(audio))
            }
            is JsonObject -> clipFromObject(element, amountHint)
            else -> null
        }
    }

    private fun clipFromObject(obj: JsonObject, amountHint: Int?): MoneyClip? {
        val amount = amountHint
            ?: firstInt(obj, "number", "value", "n", "yuan", "amount", "price", "int")
            ?: return null
        val audio = firstAudio(obj) ?: return null
        val hanzi = firstString(obj, "hanzi", "zh", "text", "spoken", "label")
        val pinyin = firstString(obj, "pinyin", "py")
        return MoneyClip(amount, hanzi, pinyin, audio)
    }

    private fun firstAudio(obj: JsonObject): String? {
        for (key in listOf(
            "audio_base64", "audioBase64", "base64", "audio", "mp3", "wav", "ogg", "data",
        )) {
            val value = obj[key] ?: continue
            when (value) {
                is JsonPrimitive -> value.contentOrNull?.takeIf { looksLikeAudio(it) }?.let {
                    return stripDataUri(it)
                }
                is JsonObject -> firstAudio(value)?.let { return it }
                else -> Unit
            }
        }
        return null
    }

    private fun firstInt(obj: JsonObject, vararg keys: String): Int? {
        for (key in keys) {
            val value = obj[key] ?: continue
            parseAmountElement(value)?.let { return it }
        }
        return null
    }

    private fun firstString(obj: JsonObject, vararg keys: String): String? {
        for (key in keys) {
            val value = obj[key] as? JsonPrimitive ?: continue
            val text = value.contentOrNull?.trim().orEmpty()
            if (text.isNotEmpty()) return text
        }
        return null
    }

    private fun parseAmountElement(element: JsonElement): Int? {
        val primitive = element as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { if (it > 0) return it }
        return parseAmount(primitive.content)
    }

    internal fun parseAmount(raw: String): Int? {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { if (it > 0) return it }
        val normalized = trimmed
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace("块", "")
            .replace("KUAI", "", ignoreCase = true)
            .replace("YUAN", "", ignoreCase = true)
            .trim()
        normalized.toIntOrNull()?.let { if (it > 0) return it }
        normalized.toDoubleOrNull()?.let { number ->
            val asInt = number.toInt()
            if (number == asInt.toDouble() && asInt > 0) return asInt
        }
        return null
    }

    internal fun looksLikeAudio(value: String): Boolean {
        val compact = stripDataUri(value).replace("\\s".toRegex(), "")
        if (compact.length < 32) return false
        return compact.matches(Regex("^[A-Za-z0-9+/=]+$"))
    }

    internal fun stripDataUri(value: String): String {
        val comma = value.indexOf("base64,")
        val raw = if (value.startsWith("data:", ignoreCase = true) && comma >= 0) {
            value.substring(comma + "base64,".length)
        } else {
            value
        }
        return raw.replace("\\s".toRegex(), "")
    }
}

internal fun decodeBase64Audio(value: String): ByteArray {
    val compact = AudioCatalogParser.stripDataUri(value)
    return Base64.getDecoder().decode(compact)
}
