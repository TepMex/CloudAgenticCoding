package com.tepmex.hanziinfogf14.data

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDownloaderTest {
    @Test
    fun keepsTheFileOnlyWhenTheBodyHasTheExpectedLength() {
        val body = "embedding-bytes".toByteArray()
        withServer(mapOf("/model" to Response(200, body))) { base ->
            val destination = tempFile()
            val downloader = ModelDownloader("$base/model", destination, body.size.toLong())
            assertFalse(downloader.ready)
            downloader.download(onProgress = { _, _ -> })
            assertTrue(downloader.ready)
            assertEquals(body.size.toLong(), destination.length())
            downloader.delete()
            assertFalse(downloader.ready)
            assertFalse(destination.exists())
        }
    }

    @Test
    fun deletesAShortBody() {
        val body = "short".toByteArray()
        withServer(mapOf("/model" to Response(200, body))) { base ->
            val destination = tempFile()
            val downloader = ModelDownloader("$base/model", destination, body.size.toLong() + 8)
            assertThrows(IllegalStateException::class.java) {
                downloader.download(onProgress = { _, _ -> })
            }
            assertFalse(downloader.ready)
            assertFalse(destination.exists())
            assertFalse(File(destination.parentFile, destination.name + ".partial").exists())
        }
    }

    @Test
    fun followsARedirect() {
        val body = "redirected-model".toByteArray()
        lateinit var base: String
        withServer(
            mapOf(
                "/model" to Response(200, body),
                "/start" to Response(302, ByteArray(0), location = { "$base/model" }),
            ),
        ) { address ->
            base = address
            val destination = tempFile()
            val downloader = ModelDownloader("$address/start", destination, body.size.toLong())
            downloader.download(onProgress = { _, _ -> })
            assertTrue(downloader.ready)
            assertEquals(String(body), destination.readText())
        }
    }

    private class Response(
        val code: Int,
        val body: ByteArray,
        val location: () -> String? = { null },
    )

    private fun withServer(routes: Map<String, Response>, block: (String) -> Unit) {
        val server = ServerSocket(0)
        val thread = Thread {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: Exception) {
                    return@Thread
                }
                socket.use { serve(it, routes) }
            }
        }
        thread.isDaemon = true
        thread.start()
        try {
            block("http://127.0.0.1:${server.localPort}")
        } finally {
            server.close()
        }
    }

    private fun serve(socket: Socket, routes: Map<String, Response>) {
        val reader = socket.getInputStream().bufferedReader()
        val request = reader.readLine() ?: return
        val path = request.split(" ").getOrNull(1)?.substringBefore("?") ?: return
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val response = routes[path] ?: Response(404, ByteArray(0))
        val location = response.location()
        val head = buildString {
            append("HTTP/1.1 ${response.code} OK\r\n")
            if (location != null) append("Location: $location\r\n")
            append("Content-Length: ${response.body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        val output = socket.getOutputStream()
        output.write(head.toByteArray(Charsets.US_ASCII))
        output.write(response.body)
        output.flush()
    }

    private fun tempFile(): File {
        val file = File.createTempFile("embeddinggemma", ".litertlm")
        file.delete()
        return file
    }
}
