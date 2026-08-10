package com.tepmex.idealtiming.mi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class MiCryptoTest {
    @Test
    fun encryptDecryptRoundTrip() {
        val ssecurity = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        val nonce = MiCrypto.generateNonce(1_700_000_000_000L)
        val snonce = MiCrypto.computeSignedNonce(ssecurity, nonce)
        val plain = """{"hello":"world","n":42}"""
        val cipher = MiCrypto.encryptData(snonce, plain)
        assertEquals(plain, MiCrypto.decryptData(snonce, cipher))
    }

    @Test
    fun buildEncryptedParams_hasRequiredKeys() {
        val ssecurity = Base64.getEncoder().encodeToString(ByteArray(16) { 7 })
        val enc = MiCrypto.buildEncryptedParams(
            method = "GET",
            urlPath = "/app/v1/data/get_sport_records_by_watermark",
            ssecurity = ssecurity,
            params = mapOf("relative_uid" to 123, "watermark" to 0, "limit" to 50),
            nonce = MiCrypto.generateNonce(1_700_000_000_000L),
        )
        assertTrue(enc.containsKey("data"))
        assertTrue(enc.containsKey("rc4_hash__"))
        assertTrue(enc.containsKey("signature"))
        assertTrue(enc.containsKey("_nonce"))
    }

    @Test
    fun decryptResponse_returnsJsonObject() {
        val ssecurity = Base64.getEncoder().encodeToString(ByteArray(16) { 3 })
        val nonce = MiCrypto.generateNonce(1_700_000_000_000L)
        val snonce = MiCrypto.computeSignedNonce(ssecurity, nonce)
        val cipher = MiCrypto.encryptData(snonce, """{"code":0,"result":{"ok":true}}""")
        val decrypted = MiCrypto.decryptResponse(ssecurity, nonce, cipher) as JSONObject
        assertEquals(0, decrypted.getInt("code"))
        assertTrue(decrypted.getJSONObject("result").getBoolean("ok"))
    }
}
