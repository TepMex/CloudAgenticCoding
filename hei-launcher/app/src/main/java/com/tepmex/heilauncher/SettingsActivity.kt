package com.tepmex.heilauncher

import android.app.role.RoleManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.tepmex.heilauncher.ui.GraphViewModelFactory
import com.tepmex.heilauncher.ui.HeiTheme
import com.tepmex.heilauncher.ui.SettingsScreen
import com.tepmex.heilauncher.ui.SettingsViewModel

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels {
        GraphViewModelFactory((application as HeiLauncherApp).graph)
    }

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        viewModel.refresh()
    }

    private val homeRole = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            HeiTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onRequestLocation = {
                        locationPermission.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    onRequestHomeRole = ::requestHomeRole,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refresh()
    }

    private fun requestHomeRole() {
        val roles = getSystemService(RoleManager::class.java) ?: return
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME)) return
        if (roles.isRoleHeld(RoleManager.ROLE_HOME)) return
        homeRole.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
    }
}
