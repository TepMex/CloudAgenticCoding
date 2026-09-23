package com.tepmex.hanziinfogf14

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.hanziinfogf14.data.DeepLinkParser
import com.tepmex.hanziinfogf14.ui.HanziInfoGf14Theme
import com.tepmex.hanziinfogf14.ui.HanziMapScreen
import com.tepmex.hanziinfogf14.ui.MapViewModel

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<IncomingLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            applyLink(intent)
        }
        setContent {
            val viewModel: MapViewModel = viewModel()
            val link = incoming
            LaunchedEffect(link?.token) {
                if (link != null) {
                    viewModel.open(link.hanzi, recordHistory = true)
                }
            }
            HanziInfoGf14Theme {
                HanziMapScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLink(intent)
    }

    private fun applyLink(intent: Intent?) {
        val hanzi = DeepLinkParser.parse(intent?.dataString) ?: return
        incoming = IncomingLink(token = (incoming?.token ?: 0) + 1, hanzi = hanzi)
    }

    private data class IncomingLink(val token: Int, val hanzi: String)
}
