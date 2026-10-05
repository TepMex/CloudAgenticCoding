package com.tepmex.cornegame.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun CorneApp(viewModel: TrainViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ErrorFeedback(
        errorNonce = state.errorNonce,
        enabled = state.settings.vibrate,
    )
    BackHandler(
        enabled = state.screen == AppScreen.Progress || state.screen == AppScreen.Settings,
    ) {
        viewModel.closeOverlay()
    }
    Scaffold { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (state.screen) {
                AppScreen.Train -> TrainScreen(
                    state = state,
                    onAction = viewModel::onTrainerAction,
                    onLanguage = viewModel::setLanguage,
                    onOpenProgress = viewModel::openProgress,
                    onOpenSettings = viewModel::openSettings,
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
                )
            }
        }
    }
}
