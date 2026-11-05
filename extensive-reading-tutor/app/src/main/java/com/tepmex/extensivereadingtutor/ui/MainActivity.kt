package com.tepmex.extensivereadingtutor.ui

import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.extensivereadingtutor.TutorApplication

class MainActivity : ComponentActivity() {
    private var readerOpen: Boolean = false
    private var onVolume: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as TutorApplication).graph
        val incoming = intent?.data
        val freshStart = savedInstanceState == null
        setContent {
            val model: TutorViewModel = viewModel(factory = TutorViewModel.factory(graph))
            val state by model.state.collectAsStateWithLifecycle()
            readerOpen = state.route is Route.Reader
            onVolume = model::togglePinyin
            val context = LocalContext.current
            val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri == null) return@rememberLauncherForActivityResult
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
                model.import(bytes, uri.toString())
            }
            LaunchedEffect(incoming) {
                if (freshStart && incoming != null) {
                    val bytes = context.contentResolver.openInputStream(incoming)?.use { it.readBytes() }
                    if (bytes != null) model.import(bytes, incoming.toString())
                }
            }
            TutorApp(model, onImport = { open.launch(arrayOf("application/epub+zip", "application/octet-stream")) })
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (readerOpen && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    onVolume?.invoke()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
