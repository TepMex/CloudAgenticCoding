package com.tepmex.hanziinfogf14.data

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import kotlin.coroutines.cancellation.CancellationException

/**
 * Downloads [url] to [destination] only when the body is exactly [expectedBytes].
 * A short or interrupted body is left as a partial file and is not treated as ready.
 */
class ModelDownloader(
    private val url: String,
    val destination: File,
    private val expectedBytes: Long,
) {
    val ready: Boolean
        get() = destination.isFile && destination.length() == expectedBytes

    fun download(onProgress: (read: Long, total: Long) -> Unit, isCancelled: () -> Boolean = { false }) {
        if (ready) {
            onProgress(expectedBytes, expectedBytes)
            return
        }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".partial")
        if (partial.exists() && !partial.delete()) {
            error("Could not replace ${partial.name}")
        }
        val connection = open(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("HTTP $code")
            }
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var readTotal = 0L
                    while (true) {
                        if (isCancelled()) throw CancellationException("download cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        readTotal += count
                        onProgress(readTotal, expectedBytes)
                    }
                }
            }
        } catch (error: Throwable) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
        if (partial.length() != expectedBytes) {
            val length = partial.length()
            partial.delete()
            error("Downloaded $length bytes, expected $expectedBytes")
        }
        if (destination.exists() && !destination.delete()) {
            partial.delete()
            error("Could not replace ${destination.name}")
        }
        if (!partial.renameTo(destination)) {
            partial.copyTo(destination, overwrite = true)
            partial.delete()
        }
    }

    fun delete() {
        destination.delete()
        File(destination.parentFile, destination.name + ".partial").delete()
    }

    private fun open(target: String): HttpURLConnection {
        val connection = URI(target).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("User-Agent", "hanzi-info-gf14")
        return connection
    }
}
