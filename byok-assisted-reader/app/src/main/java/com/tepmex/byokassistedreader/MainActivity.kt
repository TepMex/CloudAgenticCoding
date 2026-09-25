package com.tepmex.byokassistedreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.byokassistedreader.ui.ReaderScreen
import com.tepmex.byokassistedreader.ui.ReaderTheme
import com.tepmex.byokassistedreader.ui.ReaderViewModel
import com.tepmex.byokassistedreader.ui.Route
import com.tepmex.byokassistedreader.ui.SettingsScreen
import com.tepmex.byokassistedreader.ui.ShelfScreen

class MainActivity : ComponentActivity() {
    private var volumeKeysEnabled: Boolean = true
    private var readerOpen: Boolean = false
    private var onVolume: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val incoming = intent
        setContent {
            val model: ReaderViewModel = viewModel()
            val settings by model.settings.collectAsState()
            val route by model.route.collectAsState()
            val book by model.book.collectAsState()
            val pages by model.pages.collectAsState()
            val pageIndex by model.pageIndex.collectAsState()
            val layer by model.layer.collectAsState()
            val assist by model.assist.collectAsState()
            val status by model.status.collectAsState()
            volumeKeysEnabled = settings.volumeKeys
            readerOpen = route == Route.READER
            onVolume = { forward -> model.stepLayer(forward) }

            val openEpub = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    grant(uri)
                    model.openBook(uri)
                }
            }
            androidx.compose.runtime.LaunchedEffect(incoming) {
                if (incoming?.action == Intent.ACTION_VIEW) {
                    incoming.data?.let { uri ->
                        grant(uri)
                        model.openBook(uri)
                    }
                }
            }

            ReaderTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        when (route) {
                            Route.SHELF -> ShelfScreen(
                                status = status,
                                canContinue = book != null,
                                onOpen = { openEpub.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                                onContinue = model::continueReading,
                                onSettings = model::openSettings,
                            )
                            Route.READER -> ReaderScreen(
                                title = book?.title ?: "Книга",
                                pages = pages,
                                pageIndex = pageIndex,
                                layer = layer,
                                assist = assist,
                                onLayout = model::relayout,
                                onTurn = model::turnPage,
                                onSettings = model::openSettings,
                                onOpen = { openEpub.launch(arrayOf("application/epub+zip", "application/octet-stream")) },
                                onRetry = model::refreshAssist,
                            )
                            Route.SETTINGS -> SettingsScreen(
                                initial = settings,
                                onSave = model::saveSettings,
                                onBack = model::closeSettings,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (readerOpen && volumeKeysEnabled && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    onVolume?.invoke(true)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    onVolume?.invoke(false)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun grant(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // VIEW intents may already carry a temporary grant.
        }
    }
}
