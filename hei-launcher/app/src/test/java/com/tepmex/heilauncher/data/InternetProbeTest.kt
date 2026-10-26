package com.tepmex.heilauncher.data

import com.tepmex.heilauncher.domain.InternetAccess
import java.net.ServerSocket
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetProbeTest {
    @Test
    fun productionProbesAreHttpsAndCappedAtThirtySeconds() {
        assertEquals("https://ya.ru/", InternetProbe.YA)
        assertEquals("https://google.com/", InternetProbe.GOOGLE)
        assertEquals("https://youtube.com/", InternetProbe.YOUTUBE)
        assertEquals(30_000L, InternetProbe.TIMEOUT_MS)
        val client = InternetProbe.defaultClient()
        assertEquals(30_000, client.callTimeoutMillis)
        assertTrue(client.connectTimeoutMillis <= 30_000)
        assertTrue(client.readTimeoutMillis <= 30_000)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }

    @Test
    fun anyHttpResponseCountsAsReachable() {
        MockWebServer().use { server ->
            server.start()
            val client = InternetProbe.defaultClient(timeoutMs = 2_000)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.test/"))
            server.enqueue(MockResponse().setResponseCode(500))
            val base = server.url("/").toString()
            runBlocking {
                assertTrue(InternetProbe.reachable(client, base + "redirect"))
                assertTrue(InternetProbe.reachable(client, base + "error"))
            }
            assertEquals("/redirect", server.takeRequest().path)
        }
    }

    @Test
    fun aClosedPortAndAHangCountAsUnreachable() {
        val client = InternetProbe.defaultClient(timeoutMs = 400)
        val closed = ServerSocket(0).use { it.localPort }
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.start()
            runBlocking {
                assertFalse(InternetProbe.reachable(client, "http://127.0.0.1:$closed/"))
                assertFalse(InternetProbe.reachable(client, server.url("/hang").toString()))
            }
        }
    }

    @Test
    fun yaAndGoogleWithoutYoutubeIsRussian() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setResponseCode(204))
            server.start()
            val base = server.url("/").toString()
            val closed = ServerSocket(0).use { it.localPort }
            val probe = InternetProbe(
                client = InternetProbe.defaultClient(timeoutMs = 1_000),
                yaUrl = base + "ya",
                googleUrl = base + "google",
                youtubeUrl = "http://127.0.0.1:$closed/",
            )
            val access = runBlocking { probe.check() }
            assertEquals(InternetAccess.Russian, access)
        }
    }
}
