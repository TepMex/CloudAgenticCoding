package com.tepmex.extensivereadingtutor.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.extensivereadingtutor.TutorApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(TutorBg.toArgb(), TutorBg.toArgb()),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE),
        )
        val graph = (application as TutorApplication).graph
        val incoming = intent?.data
        val freshStart = savedInstanceState == null
        setContent {
            val model: TutorViewModel = viewModel(factory = TutorViewModel.factory(graph))
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
}
