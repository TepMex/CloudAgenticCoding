package com.tepmex.runninglog.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tepmex.runninglog.data.RunningRepository

enum class LoginStep {
    Credentials,
    SmsCode,
}

data class LoginUiState(
    val step: LoginStep = LoginStep.Credentials,
    val username: String = "",
    val password: String = "",
    val region: String = RunningRepository.defaultRegion,
    val smsCode: String = "",
    val maskedPhone: String = "",
    val busy: Boolean = false,
    val waitingBrowser: Boolean = false,
    val showPasswordForm: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
)

@Composable
fun LoginScreen(
    state: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onSmsCodeChange: (String) -> Unit,
    onSignInBrowser: () -> Unit,
    onSignInWebView: () -> Unit,
    onCancelBrowser: () -> Unit,
    onTogglePasswordForm: () -> Unit,
    onSubmitCredentials: () -> Unit,
    onSubmitSms: () -> Unit,
    onBackToCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "running-log",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Import Mi Band runs into a local journal.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            AnimatedVisibility(visible = state.step == LoginStep.Credentials, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Text("Region", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RunningRepository.regions.forEach { r ->
                            FilterChip(
                                selected = state.region == r,
                                onClick = { onRegionChange(r) },
                                enabled = !state.busy,
                                label = { Text(r) },
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onSignInBrowser,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign in with browser")
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onSignInWebView,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sign in with in-app browser")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Uses Xiaomi account in Chrome (or an in-app page). " +
                            "If you are already signed in there, confirm and return here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (state.waitingBrowser) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = state.statusMessage ?: "Waiting for Xiaomi sign-in…",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onCancelBrowser, enabled = state.waitingBrowser) {
                            Text("Cancel")
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = onTogglePasswordForm,
                        enabled = !state.busy || state.waitingBrowser.not(),
                    ) {
                        Text(if (state.showPasswordForm) "Hide password sign-in" else "Use password instead")
                    }

                    AnimatedVisibility(visible = state.showPasswordForm) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.username,
                                onValueChange = onUsernameChange,
                                label = { Text("Xiaomi account") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.busy,
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = state.password,
                                onValueChange = onPasswordChange,
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !state.busy,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = onSubmitCredentials,
                                enabled = !state.busy &&
                                    state.username.isNotBlank() &&
                                    state.password.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Sign in with password")
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = state.step == LoginStep.SmsCode, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Text(
                        text = "SMS code sent to ${state.maskedPhone.ifBlank { "your phone" }}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.smsCode,
                        onValueChange = onSmsCodeChange,
                        label = { Text("6-digit code") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.busy,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onSubmitSms,
                        enabled = !state.busy && state.smsCode.length >= 4,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Confirm")
                    }
                    TextButton(onClick = onBackToCredentials, enabled = !state.busy) {
                        Text("Back")
                    }
                }
            }

            if (state.busy) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Unofficial Xiaomi Fitness cloud sync. Not affiliated with Xiaomi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
