package com.tepmex.inbox

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.tepmex.inbox.ui.InboxAppShell
import com.tepmex.inbox.ui.InboxViewModelFactory
import com.tepmex.inbox.ui.LaunchEffect
import com.tepmex.inbox.ui.theme.InboxTheme
import kotlinx.coroutines.channels.Channel

class MainActivity : ComponentActivity() {
    private val effects = Channel<LaunchEffect>(capacity = 8)
    private var consumedArchiveId = Long.MIN_VALUE
    private var consumedShare: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        consumedArchiveId = savedInstanceState?.getLong(STATE_ARCHIVE, Long.MIN_VALUE) ?: Long.MIN_VALUE
        consumedShare = savedInstanceState?.getString(STATE_SHARE)
        enableEdgeToEdge()
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val app = application as InboxApp
        handleIntent(intent)
        setContent {
            InboxTheme {
                InboxAppShell(
                    factory = InboxViewModelFactory(app),
                    effects = effects,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_ARCHIVE, consumedArchiveId)
        consumedShare?.let { outState.putString(STATE_SHARE, it) }
    }

    private fun handleIntent(intent: Intent) {
        val archiveId = intent.getLongExtra(EXTRA_OPEN_ARCHIVE_ID, Long.MIN_VALUE)
        if (archiveId != Long.MIN_VALUE && archiveId != consumedArchiveId) {
            consumedArchiveId = archiveId
            effects.trySend(LaunchEffect.OpenArchive)
        }
        if (intent.action == Intent.ACTION_SEND) {
            var token = intent.getStringExtra(EXTRA_SHARE_TOKEN)
            if (token == null) {
                token = java.util.UUID.randomUUID().toString()
                intent.putExtra(EXTRA_SHARE_TOKEN, token)
            }
            if (token != consumedShare) {
                consumedShare = token
                val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                effects.trySend(LaunchEffect.SharedText(text))
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_ARCHIVE_ID = "inbox_open_archive_id"
        private const val EXTRA_SHARE_TOKEN = "inbox_share_token"
        private const val STATE_ARCHIVE = "consumed_archive"
        private const val STATE_SHARE = "consumed_share"
    }
}
