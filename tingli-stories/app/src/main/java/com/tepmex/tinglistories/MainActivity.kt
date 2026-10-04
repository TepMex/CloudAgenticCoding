package com.tepmex.tinglistories

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.tinglistories.ui.TingliApp
import com.tepmex.tinglistories.ui.TingliTheme
import com.tepmex.tinglistories.ui.TingliViewModel

class MainActivity : ComponentActivity() {
    private var incomingZip by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            incomingZip = zipUri(intent)
        }
        setContent {
            val viewModel: TingliViewModel = viewModel()
            val uri = incomingZip
            LaunchedEffect(uri) {
                val current = uri ?: return@LaunchedEffect
                incomingZip = null
                viewModel.importFromUri(current)
            }
            TingliTheme {
                TingliApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingZip = zipUri(intent)
    }
}

private fun zipUri(intent: Intent): Uri? {
    if (intent.action != Intent.ACTION_VIEW) return null
    return intent.data
}
