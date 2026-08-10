package com.tepmex.idealtiming.mi

import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Xiaomi cloud RC4 request encryption / response decryption.
 * Protocol matches the publicly reverse-engineered Mi Fitness client flow.
 */
object MiCrypto {
    private const val RC4_SKIP = 1024

    fun generateNonce(nowMillis: Long = System.currentTimeMillis()): String {
        val random = ByteArray(8)
        SecureRandom().nextBytes(random)
        val minutes = (nowMillis / 60_000L).toInt()
        val buf = ByteBuffer.allocate(12)
        buf.put(random)
        buf.putInt(minutes)
        return Base64.getEncoder().encodeToString(buf.array())
    }

    fun computeSignedNonce(ssecurity: String, nonce: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(Base64.getDecoder().decode(ssecurity))
        md.update(Base64.getDecoder().decode(nonce))
        return Base64.getEncoder().encodeToString(md.digest())
    }

    fun buildEncryptedParams(
        method: String,
        urlPath: String,
        ssecurity: String,
        params: Map<String, Any?>?,
        nonce: String = generateNonce(),
    ): Map<String, String> {
        val snonce = computeSignedNonce(ssecurity, nonce)
        val snonceBytes = Base64.getDecoder().decode(snonce)

        val rawTree = linkedMapOf<String, String>()
        if (!params.isNullOrEmpty()) {
            val data = JSONObject()
            for ((k, v) in params) {
                data.put(k, v)
            }
            rawTree["data"] = data.toString()
        }

        val rc4Hash = sha1B64(buildSigMessage(method, urlPath, rawTree, snonce))
        rawTree["rc4_hash__"] = rc4Hash

        val sorted = rawTree.toSortedMap()
        val encrypted = rc4StreamEncryptValues(snonceBytes, sorted.toList())
        val signature = sha1B64(buildSigMessage(method, urlPath, encrypted, snonce))

        return buildMap {
            putAll(encrypted)
            put("signature", signature)
            put("_nonce", nonce)
        }
    }

    fun decryptResponse(ssecurity: String, nonce: String, ciphertextB64: String): Any {
        val snonce = computeSignedNonce(ssecurity, nonce)
        val plaintext = decryptData(snonce, ciphertextB64)
        return try {
            JSONObject(plaintext)
        } catch (_: Exception) {
            plaintext
        }
    }

    fun encryptData(signedNonce: String, plaintext: String): String {
        val key = Base64.getDecoder().decode(signedNonce)
        val encrypted = rc4Crypt(key, plaintext.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encrypted)
    }

    fun decryptData(signedNonce: String, ciphertextB64: String): String {
        val key = Base64.getDecoder().decode(signedNonce)
        val decrypted = rc4Crypt(key, Base64.getDecoder().decode(ciphertextB64))
        return String(decrypted, Charsets.UTF_8)
    }

    internal fun buildSigMessage(
        method: String,
        urlPath: String,
        params: Map<String, String>,
        signedNonce: String,
    ): String {
        val parts = ArrayList<String>(2 + params.size + 1)
        parts += method.uppercase()
        parts += urlPath
        for (k in params.keys.sorted()) {
            parts += "$k=${params[k]}"
        }
        parts += signedNonce
        return parts.joinToString("&")
    }

    internal fun rc4StreamEncryptValues(
        keyBytes: ByteArray,
        sortedEntries: List<Pair<String, String>>,
    ): Map<String, String> {
        val allBytes = sortedEntries.joinToString("") { it.second }.toByteArray(Charsets.UTF_8)
        val encryptedAll = rc4Crypt(keyBytes, allBytes, skip = RC4_SKIP)
        val result = linkedMapOf<String, String>()
        var pos = 0
        for ((k, v) in sortedEntries) {
            val vlen = v.toByteArray(Charsets.UTF_8).size
            val slice = encryptedAll.copyOfRange(pos, pos + vlen)
            result[k] = Base64.getEncoder().encodeToString(slice)
            pos += vlen
        }
        return result
    }

    internal fun rc4Crypt(key: ByteArray, data: ByteArray, skip: Int = RC4_SKIP): ByteArray {
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + (key[i % key.size].toInt() and 0xFF)) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
        }
        var i = 0
        j = 0
        repeat(skip) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
        }
        val out = ByteArray(data.size)
        for (idx in data.indices) {
            i = (i + 1) and 0xFF
            j = (j + s[i]) and 0xFF
            val tmp = s[i]
            s[i] = s[j]
            s[j] = tmp
            out[idx] = (data[idx].toInt() xor s[(s[i] + s[j]) and 0xFF]).toByte()
        }
        return out
    }

    private fun sha1B64(message: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(message.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}
