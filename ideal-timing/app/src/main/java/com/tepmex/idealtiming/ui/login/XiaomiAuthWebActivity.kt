package com.tepmex.idealtiming.ui.login

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import com.tepmex.idealtiming.mi.MiAuth
import com.tepmex.idealtiming.mi.MiConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * In-app Xiaomi account WebView.
 *
 * Two modes:
 * - [MODE_LONG_POLL]: show [EXTRA_URL] while the caller polls Xiaomi long-poll.
 * - [MODE_COOKIE]: capture `passToken` + `userId` from CookieManager after the user signs in.
 */
class XiaomiAuthWebActivity : ComponentActivity() {
    private var pollJob: Job? = null
    private var finished = false
    private var mode: String = MODE_LONG_POLL

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activeRef = WeakReference(this)
        val startUrl = intent.getStringExtra(EXTRA_URL)
            ?: MiConstants.accountWebLoginUrl()
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_LONG_POLL

        CookieManager.getInstance().setAcceptCookie(true)

        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = MiConstants.DEFAULT_LOGIN_USER_AGENT
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (mode == MODE_COOKIE) maybeFinishWithCookies()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (mode == MODE_COOKIE) maybeFinishWithCookies()
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    if (mode == MODE_COOKIE) maybeFinishWithCookies()
                    return false
                }
            }
        }
        setContentView(webView)
        webView.loadUrl(startUrl)

        if (mode == MODE_COOKIE) {
            pollJob = lifecycleScope.launch {
                while (isActive && !finished) {
                    maybeFinishWithCookies()
                    delay(750)
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishCancelled()
                }
            },
        )
    }

    private fun maybeFinishWithCookies() {
        if (finished) return
        val header = CookieManager.getInstance()
            .getCookie(MiConstants.XIAOMI_ACCOUNT_ORIGIN)
            ?: CookieManager.getInstance().getCookie("https://account.xiaomi.com/")
        val cookies = MiAuth.parseCookieHeader(header)
        val pair = MiAuth.passTokenFromCookies(cookies) ?: return
        finished = true
        pollJob?.cancel()
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_PASS_TOKEN, pair.first)
                .putExtra(EXTRA_USER_ID, pair.second)
                .putExtra(EXTRA_DEVICE_ID, cookies["deviceId"].orEmpty()),
        )
        finish()
    }

    private fun finishCancelled() {
        if (finished) return
        finished = true
        pollJob?.cancel()
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun finishBecauseLongPollSucceeded() {
        if (finished) return
        finished = true
        pollJob?.cancel()
        setResult(RESULT_LONG_POLL_DONE)
        finish()
    }

    override fun onDestroy() {
        if (activeRef?.get() === this) {
            activeRef = null
        }
        pollJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_MODE = "mode"
        const val EXTRA_PASS_TOKEN = "pass_token"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_DEVICE_ID = "device_id"

        const val MODE_LONG_POLL = "long_poll"
        const val MODE_COOKIE = "cookie"

        const val RESULT_LONG_POLL_DONE = RESULT_FIRST_USER

        @Volatile
        private var activeRef: WeakReference<XiaomiAuthWebActivity>? = null

        fun finishIfOpen() {
            val act = activeRef?.get() ?: return
            act.runOnUiThread { act.finishBecauseLongPollSucceeded() }
        }

        fun intent(
            context: Context,
            url: String,
            mode: String = MODE_LONG_POLL,
        ): Intent = Intent(context, XiaomiAuthWebActivity::class.java)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_MODE, mode)
    }
}
