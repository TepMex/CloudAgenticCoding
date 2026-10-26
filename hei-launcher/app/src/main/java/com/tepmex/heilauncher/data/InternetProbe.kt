package com.tepmex.heilauncher.data

import com.tepmex.heilauncher.domain.InternetAccess
import com.tepmex.heilauncher.domain.classifyInternet
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Probes ya.ru, google.com, and youtube.com over HTTPS.
 * A site counts as reachable when any HTTP response arrives.
 * A timeout, TLS failure, or dropped connection counts as unreachable.
 * Each site is capped at [TIMEOUT_MS]; the three probes run together.
 */
class InternetProbe(
    private val client: OkHttpClient = defaultClient(),
    private val yaUrl: String = YA,
    private val googleUrl: String = GOOGLE,
    private val youtubeUrl: String = YOUTUBE,
) {
    suspend fun check(): InternetAccess = coroutineScope {
        val ya = async { reachable(client, yaUrl) }
        val google = async { reachable(client, googleUrl) }
        val youtube = async { reachable(client, youtubeUrl) }
        classifyInternet(ya.await(), google.await(), youtube.await())
    }

    companion object {
        const val TIMEOUT_MS = 30_000L
        const val YA = "https://ya.ru/"
        const val GOOGLE = "https://google.com/"
        const val YOUTUBE = "https://youtube.com/"

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        fun defaultClient(timeoutMs: Long = TIMEOUT_MS): OkHttpClient {
            return OkHttpClient.Builder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .build()
        }

        internal suspend fun reachable(client: OkHttpClient, url: String): Boolean {
            val request = try {
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
            } catch (_: IllegalArgumentException) {
                return false
            }
            return suspendCancellableCoroutine { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resume(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.close()
                        if (cont.isActive) cont.resume(true)
                    }
                })
            }
        }
    }
}
