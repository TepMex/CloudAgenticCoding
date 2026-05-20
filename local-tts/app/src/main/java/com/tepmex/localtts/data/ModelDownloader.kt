package com.tepmex.localtts.data

import android.util.Log
import com.tepmex.localtts.tts.VoskTtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

private const val TAG = "LocalTtsDownload"

class ModelDownloader(
    private val client: OkHttpClient = defaultClient(),
) {

    suspend fun ensureModelExtracted(
        zipUrls: List<String> = VoskTtsEngine.MODEL_ZIP_URLS,
        modelDir: File,
        huggingFaceRepo: String = VoskTtsEngine.HUGGING_FACE_REPO,
        huggingFaceFiles: List<String> = VoskTtsEngine.HUGGING_FACE_FILES,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            if (modelDir.exists() && File(modelDir, "model.onnx").exists()) {
                onProgress(1, 1)
                return@withContext
            }
            modelDir.parentFile?.mkdirs()
            val zipFile = File(modelDir.parentFile, "${modelDir.name}.zip")
            val zipErrors = mutableListOf<String>()

            if (!zipFile.exists() || zipFile.length() < 1024L) {
                val downloaded = tryDownloadZip(zipUrls, zipFile, onProgress)
                if (!downloaded) {
                    zipErrors.add("zip mirrors failed")
                }
            }

            if (zipFile.exists() && zipFile.length() >= 1024L) {
                extractZip(zipFile, modelDir.parentFile!!)
                if (zipFile.exists()) {
                    zipFile.delete()
                }
            } else if (!File(modelDir, "model.onnx").exists()) {
                Log.i(TAG, "Trying Hugging Face file download")
                downloadHuggingFaceFiles(
                    repoBase = huggingFaceRepo,
                    relativePaths = huggingFaceFiles,
                    modelDir = modelDir,
                    onProgress = onProgress,
                )
            }

            if (!File(modelDir, "model.onnx").exists()) {
                val detail = zipErrors.joinToString("; ").ifEmpty { "all sources failed" }
                throw IllegalStateException("Model download failed ($detail): ${modelDir.absolutePath}")
            }
            Log.i(TAG, "Model ready at ${modelDir.absolutePath}")
        }
    }

    private suspend fun tryDownloadZip(
        zipUrls: List<String>,
        zipFile: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): Boolean {
        val clients = listOf(client to "default DNS", dnsFallbackClient() to "alphacephei DNS fallback")
        for ((httpClient, label) in clients) {
            for (url in zipUrls) {
                try {
                    if (zipFile.exists()) zipFile.delete()
                    downloadFile(httpClient, url, zipFile, onProgress)
                    return true
                } catch (e: Exception) {
                    Log.w(TAG, "Zip download failed ($label, $url): ${e.message}")
                    if (zipFile.exists()) zipFile.delete()
                }
            }
        }
        return false
    }

    private suspend fun downloadHuggingFaceFiles(
        repoBase: String,
        relativePaths: List<String>,
        modelDir: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) {
        modelDir.mkdirs()
        val fileSizes = relativePaths.associateWith { path ->
            headContentLength(client, "$repoBase/$path")
        }
        val totalBytes = fileSizes.values.filterNotNull().sum().takeIf { it > 0 }
        var downloadedOverall = 0L

        for (path in relativePaths) {
            val target = File(modelDir, path)
            if (target.exists() && target.length() > 0) {
                downloadedOverall += target.length()
                onProgress(downloadedOverall, totalBytes)
                continue
            }
            val url = "$repoBase/$path"
            downloadFile(client, url, target) { fileDownloaded, _ ->
                val fileStart = downloadedOverall
                onProgress(fileStart + fileDownloaded, totalBytes)
            }
            downloadedOverall += target.length()
            onProgress(downloadedOverall, totalBytes)
        }
    }

    private fun headContentLength(httpClient: OkHttpClient, url: String): Long? {
        val request = Request.Builder().url(url).head().build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            }
        } catch (e: Exception) {
            Log.w(TAG, "HEAD failed for $url: ${e.message}")
            null
        }
    }

    private suspend fun downloadFile(
        httpClient: OkHttpClient,
        url: String,
        target: File,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, target.name + ".part")
        if (temp.exists()) temp.delete()
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed: HTTP ${response.code} for $url")
            }
            val body = response.body ?: throw IllegalStateException("Empty body for $url")
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
            throw IllegalStateException("Could not finalize download: ${target.absolutePath}")
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

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.MINUTES)
            .readTimeout(30, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()

        private fun dnsFallbackClient(): OkHttpClient {
            val fallbackIp = VoskTtsEngine.ALPHACEPHEI_FALLBACK_IPV4
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.MINUTES)
                .readTimeout(30, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .dns(object : Dns {
                    override fun lookup(hostname: String): List<InetAddress> {
                        if (hostname.equals("alphacephei.com", ignoreCase = true)) {
                            return listOf(
                                InetAddress.getByAddress(hostname, fallbackIp.copyOf()),
                            )
                        }
                        return try {
                            Dns.SYSTEM.lookup(hostname)
                        } catch (e: UnknownHostException) {
                            throw e
                        }
                    }
                })
                .build()
        }
    }
}
