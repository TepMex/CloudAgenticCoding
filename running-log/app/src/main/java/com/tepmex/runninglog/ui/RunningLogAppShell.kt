package com.tepmex.runninglog.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.runninglog.ui.journal.JournalScreen
import com.tepmex.runninglog.ui.login.LoginScreen

@Composable
fun RunningLogAppShell(
    factory: RunningLogViewModelFactory,
    modifier: Modifier = Modifier,
) {
    val vm: RunningLogViewModel = viewModel(factory = factory)
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    val journal by vm.journal.collectAsStateWithLifecycle()

    if (!signedIn) {
        LoginScreen(
            state = login,
            onUsernameChange = vm::onUsernameChange,
            onPasswordChange = vm::onPasswordChange,
            onRegionChange = vm::onRegionChange,
            onSmsCodeChange = vm::onSmsCodeChange,
            onSubmitCredentials = vm::submitCredentials,
            onSubmitSms = vm::submitSms,
            onBackToCredentials = vm::backToCredentials,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        JournalScreen(
            state = journal,
            onSync = vm::sync,
            onSignOut = vm::signOut,
            modifier = modifier.fillMaxSize(),
        )
    }
}
