package com.tepmex.extensivereadingtutor.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface SecretStore {
    suspend fun put(accountId: String, secret: String)
    suspend fun get(accountId: String): String?
    suspend fun delete(accountId: String)
}

object SecretEnvelope {
    fun pack(iv: ByteArray, ciphertext: ByteArray): ByteArray {
        require(iv.size == 12)
        return ByteBuffer.allocate(4 + iv.size + ciphertext.size)
            .putInt(iv.size)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    fun unpack(packed: ByteArray): Pair<ByteArray, ByteArray> {
        val buffer = ByteBuffer.wrap(packed)
        val iv = ByteArray(buffer.int)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)
        return iv to ciphertext
    }
}

class AndroidKeystoreSecretStore(private val directory: File) : SecretStore {
    override suspend fun put(accountId: String, secret: String) = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val packed = SecretEnvelope.pack(cipher.iv, cipher.doFinal(secret.toByteArray(Charsets.UTF_8)))
        file(accountId).writeBytes(packed)
    }

    override suspend fun get(accountId: String): String? = withContext(Dispatchers.IO) {
        val target = file(accountId)
        if (!target.exists()) return@withContext null
        val (iv, ciphertext) = SecretEnvelope.unpack(target.readBytes())
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }

    override suspend fun delete(accountId: String) {
        withContext(Dispatchers.IO) { file(accountId).delete() }
        Unit
    }

    private fun file(accountId: String): File {
        directory.mkdirs()
        val name = com.tepmex.extensivereadingtutor.books.sha256(accountId)
        return File(directory, name)
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "reading_tutor_byok"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}

class MemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun put(accountId: String, secret: String) {
        values[accountId] = secret
    }
    override suspend fun get(accountId: String): String? = values[accountId]
    override suspend fun delete(accountId: String) {
        values.remove(accountId)
    }
}

fun redactSecrets(text: String, secrets: List<String>): String {
    var cleaned = text
    secrets.filter { it.length >= 4 }.forEach { cleaned = cleaned.replace(it, "[redacted]") }
    return cleaned.replace(Regex("""Bearer\s+\S+"""), "Bearer [redacted]")
}
