package com.tepmex.ankidroidllm.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

private const val TAG = "ModelDownload"

class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build(),
) {

    suspend fun ensureModelFile(url: String, targetFile: File, onProgress: (downloaded: Long, total: Long?) -> Unit) {
        withContext(Dispatchers.IO) {
            if (targetFile.exists() && targetFile.length() > 1024L * 1024L) {
                onProgress(targetFile.length(), targetFile.length())
                return@withContext
            }
            targetFile.parentFile?.mkdirs()
            val temp = File(targetFile.parentFile, targetFile.name + ".part")
            if (temp.exists()) temp.delete()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed: HTTP ${response.code}")
                }
                val body = response.body ?: throw IllegalStateException("Empty body")
                val total = body.contentLength().takeIf { it > 0 }
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { out ->
                        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = 0L
                        while (true) {
                            ensureActive()
                            val n = input.read(buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            downloaded += n
                            onProgress(downloaded, total)
                        }
                        out.flush()
                    }
                }
            }
            if (!temp.renameTo(targetFile)) {
                throw IllegalStateException("Could not finalize model file")
            }
            Log.i(TAG, "Model saved to ${targetFile.absolutePath} (${targetFile.length()} bytes)")
        }
    }
}
