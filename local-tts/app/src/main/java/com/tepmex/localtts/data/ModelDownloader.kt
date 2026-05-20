package com.tepmex.localtts.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

private const val TAG = "LocalTtsDownload"

class ModelDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.MINUTES)
        .readTimeout(30, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build(),
) {

    suspend fun ensureModelExtracted(
        zipUrl: String,
        modelDir: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            if (modelDir.exists() && File(modelDir, "model.onnx").exists()) {
                onProgress(1, 1)
                return@withContext
            }
            modelDir.parentFile?.mkdirs()
            val zipFile = File(modelDir.parentFile, "${modelDir.name}.zip")
            if (!zipFile.exists() || zipFile.length() < 1024L) {
                downloadFile(zipUrl, zipFile, onProgress)
            }
            extractZip(zipFile, modelDir.parentFile!!)
            if (zipFile.exists()) {
                zipFile.delete()
            }
            if (!File(modelDir, "model.onnx").exists()) {
                throw IllegalStateException("Model extraction failed: ${modelDir.absolutePath}")
            }
            Log.i(TAG, "Model ready at ${modelDir.absolutePath}")
        }
    }

    private suspend fun downloadFile(url: String, target: File, onProgress: (Long, Long?) -> Unit) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
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
                        if (!currentCoroutineContext().isActive) {
                            throw kotlinx.coroutines.CancellationException()
                        }
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
        if (!temp.renameTo(target)) {
            throw IllegalStateException("Could not finalize download")
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outFile = File(destDir, entry.name)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }
}
