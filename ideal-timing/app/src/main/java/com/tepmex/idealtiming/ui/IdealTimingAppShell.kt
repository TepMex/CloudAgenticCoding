package com.tepmex.idealtiming.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tepmex.idealtiming.ui.clock.ClockScreen
import com.tepmex.idealtiming.ui.login.LoginScreen
import com.tepmex.idealtiming.ui.login.XiaomiAuthWebActivity
import com.tepmex.idealtiming.ui.login.XiaomiBrowserAuth
import kotlinx.coroutines.flow.collectLatest

@Composable
fun IdealTimingAppShell(
    factory: IdealTimingViewModelFactory,
    modifier: Modifier = Modifier,
) {
    val vm: IdealTimingViewModel = viewModel(factory = factory)
    val signedIn by vm.signedIn.collectAsStateWithLifecycle()
    val login by vm.login.collectAsStateWithLifecycle()
    val clock by vm.clock.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val webLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val data = result.data
                val pass = data?.getStringExtra(XiaomiAuthWebActivity.EXTRA_PASS_TOKEN).orEmpty()
                val userId = data?.getStringExtra(XiaomiAuthWebActivity.EXTRA_USER_ID).orEmpty()
                val deviceId = data?.getStringExtra(XiaomiAuthWebActivity.EXTRA_DEVICE_ID).orEmpty()
                if (pass.isNotBlank() && userId.isNotBlank()) {
                    vm.onWebViewCookies(pass, userId, deviceId)
                }
            }
            XiaomiAuthWebActivity.RESULT_LONG_POLL_DONE -> {
                // Long-poll already completed in ViewModel.
            }
            else -> vm.onWebViewCancelled()
        }
    }

    LaunchedEffect(vm) {
        vm.navEvents.collectLatest { event ->
            when (event) {
                is LoginNavEvent.OpenBrowser -> {
                    XiaomiBrowserAuth.openCustomTab(context, event.url)
                }
                is LoginNavEvent.OpenWebView -> {
                    val mode = if (event.cookieMode) {
                        XiaomiAuthWebActivity.MODE_COOKIE
                    } else {
                        XiaomiAuthWebActivity.MODE_LONG_POLL
                    }
                    webLauncher.launch(
                        XiaomiAuthWebActivity.intent(context, event.url, mode),
                    )
                }
                LoginNavEvent.CloseWebView -> {
                    XiaomiAuthWebActivity.finishIfOpen()
                }
            }
        }
    }

    if (!signedIn) {
        LoginScreen(
            state = login,
            onUsernameChange = vm::onUsernameChange,
            onPasswordChange = vm::onPasswordChange,
            onRegionChange = vm::onRegionChange,
            onSmsCodeChange = vm::onSmsCodeChange,
            onSignInBrowser = vm::startBrowserSignIn,
            onSignInWebView = vm::startWebViewSignIn,
            onCancelBrowser = vm::cancelBrowserSignIn,
            onTogglePasswordForm = vm::togglePasswordForm,
            onSubmitCredentials = vm::submitCredentials,
            onSubmitSms = vm::submitSms,
            onBackToCredentials = vm::backToCredentials,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        ClockScreen(
            state = clock,
            onSync = vm::sync,
            onSignOut = vm::signOut,
            onMessageConsumed = vm::consumeClockMessage,
            modifier = modifier.fillMaxSize(),
        )
    }
}
