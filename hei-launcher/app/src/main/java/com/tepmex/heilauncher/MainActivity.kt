package com.tepmex.heilauncher

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.tepmex.heilauncher.domain.LaunchableApp
import com.tepmex.heilauncher.ui.GraphViewModelFactory
import com.tepmex.heilauncher.ui.HeiHome
import com.tepmex.heilauncher.ui.HeiTheme
import com.tepmex.heilauncher.ui.HomeViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels {
        GraphViewModelFactory((application as HeiLauncherApp).graph)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            HeiTheme {
                HeiHome(
                    viewModel = viewModel,
                    onOpenSystemSettings = ::openSystemSettings,
                    onOpenLauncherSettings = ::openLauncherSettings,
                    onLaunch = ::launch,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.setActive(true)
    }

    override fun onStop() {
        viewModel.setActive(false)
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.onHomePressed()
        }
    }

    private fun openSystemSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun openLauncherSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun launch(app: LaunchableApp) {
        val component = ComponentName.unflattenFromString(app.component) ?: return
        val launch = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            this.component = component
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        try {
            startActivity(launch)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
