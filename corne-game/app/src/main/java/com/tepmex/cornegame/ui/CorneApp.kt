package com.tepmex.cornegame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tepmex.cornegame.domain.UsbDiagnosticsController

@Composable
fun CorneApp(viewModel: TrainViewModel, usb: UsbDiagnosticsController) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val debugLog by viewModel.debugLog.collectAsStateWithLifecycle()
    val firmware by usb.state.collectAsStateWithLifecycle()
    ErrorFeedback(
        errorNonce = state.errorNonce,
        enabled = state.settings.vibrate,
    )
    BackHandler(
        enabled = state.screen == AppScreen.Progress ||
            state.screen == AppScreen.Settings ||
            state.screen == AppScreen.Usb,
    ) {
        viewModel.closeOverlay()
    }
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (state.screen) {
                    AppScreen.Train -> TrainScreen(
                        state = state,
                        onLanguage = viewModel::setLanguage,
                        onDrill = viewModel::setDrill,
                        onOpenProgress = viewModel::openProgress,
                        onOpenSettings = viewModel::openSettings,
                        onContact = viewModel::onKeyContact,
                        onPointer = viewModel::onPointer,
                        onScreenKey = viewModel::onScreenKey,
                        onReleaseHolds = viewModel::releaseScreenHolds,
                        firmware = firmware,
                    )
                    AppScreen.Results -> ResultsScreen(
                        state = state,
                        onAgain = viewModel::restart,
                        onLanguage = viewModel::setLanguage,
                        onOpenProgress = viewModel::openProgress,
                    )
                    AppScreen.Progress -> ProgressScreen(
                        history = state.history,
                        onBack = viewModel::closeOverlay,
                    )
                    AppScreen.Settings -> SettingsScreen(
                        settings = state.settings,
                        onBack = viewModel::closeOverlay,
                        onChange = viewModel::updateSettings,
                        onOpenUsb = viewModel::openUsb,
                    )
                    AppScreen.Usb -> UsbScreen(
                        controller = usb,
                        onBack = viewModel::closeOverlay,
                    )
                }
            }
            DebugLogBar(
                lines = debugLog,
                onClear = viewModel::clearDebug,
            )
        }
    }
}
