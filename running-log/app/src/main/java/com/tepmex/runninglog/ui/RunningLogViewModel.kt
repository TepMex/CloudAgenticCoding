package com.tepmex.runninglog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.runninglog.data.RunningRepository
import com.tepmex.runninglog.mi.AuthException
import com.tepmex.runninglog.mi.BrowserLoginCancelledException
import com.tepmex.runninglog.mi.BrowserLoginSession
import com.tepmex.runninglog.mi.BrowserLoginTimeoutException
import com.tepmex.runninglog.mi.CaptchaRequiredException
import com.tepmex.runninglog.mi.DeviceUntrustedException
import com.tepmex.runninglog.mi.NotificationUrlRequiredException
import com.tepmex.runninglog.ui.journal.JournalUiState
import com.tepmex.runninglog.ui.login.LoginStep
import com.tepmex.runninglog.ui.login.LoginUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LoginNavEvent {
    data class OpenBrowser(val url: String) : LoginNavEvent
    data class OpenWebView(val url: String, val cookieMode: Boolean = false) : LoginNavEvent
    data object CloseWebView : LoginNavEvent
}

class RunningLogViewModel(
    private val repository: RunningRepository,
) : ViewModel() {
    private val _signedIn = MutableStateFlow(repository.isSignedIn())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _nav = MutableSharedFlow<LoginNavEvent>(extraBufferCapacity = 4)
    val navEvents = _nav.asSharedFlow()

    private val _journalMeta = MutableStateFlow(JournalUiState())
    val journal: StateFlow<JournalUiState> = combine(
        repository.activities,
        _journalMeta,
    ) { activities, meta ->
        meta.copy(activities = activities)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    private var browserJob: Job? = null
    private var activeSession: BrowserLoginSession? = null

    fun onUsernameChange(v: String) = _login.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _login.update { it.copy(password = v, error = null) }
    fun onRegionChange(v: String) = _login.update { it.copy(region = v) }
    fun onSmsCodeChange(v: String) = _login.update { it.copy(smsCode = v, error = null) }

    fun togglePasswordForm() = _login.update {
        it.copy(showPasswordForm = !it.showPasswordForm, error = null)
    }

    fun backToCredentials() = _login.update {
        it.copy(step = LoginStep.Credentials, smsCode = "", error = null, busy = false)
    }

    fun startBrowserSignIn() = startInteractiveSignIn(useWebView = false)

    fun startWebViewSignIn() = startInteractiveSignIn(useWebView = true)

    fun cancelBrowserSignIn() {
        browserJob?.cancel()
        browserJob = null
        activeSession = null
        _nav.tryEmit(LoginNavEvent.CloseWebView)
        _login.update {
            it.copy(
                busy = false,
                waitingBrowser = false,
                statusMessage = null,
                error = null,
            )
        }
    }

    fun onWebViewCookies(
        passToken: String,
        userId: String,
        deviceId: String,
    ) {
        val region = _login.value.region
        viewModelScope.launch {
            _login.update {
                it.copy(busy = true, waitingBrowser = false, error = null, statusMessage = "Exchanging Xiaomi tokens…")
            }
            try {
                repository.loginWithPassToken(passToken, userId, region, deviceId)
                _signedIn.value = true
                _login.update { LoginUiState(region = region) }
            } catch (e: Exception) {
                _login.update {
                    it.copy(busy = false, error = e.message ?: "Token exchange failed")
                }
            }
        }
    }

    fun onWebViewCancelled() {
        if (_login.value.waitingBrowser) {
            cancelBrowserSignIn()
            _login.update { it.copy(error = "Sign-in cancelled") }
        }
    }

    private fun startInteractiveSignIn(useWebView: Boolean) {
        val region = _login.value.region
        browserJob?.cancel()
        browserJob = viewModelScope.launch {
            _login.update {
                it.copy(
                    busy = true,
                    waitingBrowser = true,
                    error = null,
                    statusMessage = "Opening Xiaomi sign-in…",
                )
            }
            try {
                val session = repository.startBrowserLogin()
                activeSession = session
                if (useWebView) {
                    _nav.emit(LoginNavEvent.OpenWebView(session.loginUrl, cookieMode = false))
                } else {
                    _nav.emit(LoginNavEvent.OpenBrowser(session.loginUrl))
                }
                _login.update {
                    it.copy(
                        statusMessage = if (useWebView) {
                            "Complete sign-in in the in-app browser…"
                        } else {
                            "Complete sign-in in the browser, then return here…"
                        },
                    )
                }
                repository.completeBrowserLogin(session, region)
                _nav.emit(LoginNavEvent.CloseWebView)
                _signedIn.value = true
                activeSession = null
                _login.update { LoginUiState(region = region) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: BrowserLoginCancelledException) {
                _login.update {
                    it.copy(busy = false, waitingBrowser = false, statusMessage = null, error = "Sign-in cancelled")
                }
            } catch (e: BrowserLoginTimeoutException) {
                _nav.emit(LoginNavEvent.CloseWebView)
                _login.update {
                    it.copy(
                        busy = false,
                        waitingBrowser = false,
                        statusMessage = null,
                        error = e.message ?: "Sign-in timed out",
                    )
                }
            } catch (e: Exception) {
                _nav.emit(LoginNavEvent.CloseWebView)
                _login.update {
                    it.copy(
                        busy = false,
                        waitingBrowser = false,
                        statusMessage = null,
                        error = e.message ?: "Browser sign-in failed",
                    )
                }
            } finally {
                activeSession = null
                browserJob = null
            }
        }
    }

    fun submitCredentials() {
        val s = _login.value
        viewModelScope.launch {
            _login.update { it.copy(busy = true, error = null) }
            try {
                repository.login(s.username.trim(), s.password, s.region)
                _signedIn.value = true
                _login.update { LoginUiState(region = s.region, username = s.username) }
            } catch (e: NotificationUrlRequiredException) {
                _login.update {
                    it.copy(
                        busy = true,
                        waitingBrowser = true,
                        statusMessage = "Xiaomi needs extra verification…",
                        error = null,
                    )
                }
                // Fall back to cookie-capture WebView on the notification URL.
                _nav.emit(LoginNavEvent.OpenWebView(e.notificationUrl, cookieMode = true))
                _login.update { it.copy(busy = false) }
            } catch (_: DeviceUntrustedException) {
                try {
                    val phone = repository.sendSmsCode()
                    _login.update {
                        it.copy(
                            busy = false,
                            step = LoginStep.SmsCode,
                            maskedPhone = phone,
                            error = null,
                        )
                    }
                } catch (e: CaptchaRequiredException) {
                    _login.update {
                        it.copy(
                            busy = false,
                            error = "Captcha required. Prefer Sign in with browser / in-app browser.",
                        )
                    }
                } catch (e: Exception) {
                    _login.update { it.copy(busy = false, error = e.message ?: "SMS send failed") }
                }
            } catch (e: Exception) {
                _login.update { it.copy(busy = false, error = e.message ?: "Login failed") }
            }
        }
    }

    fun submitSms() {
        val code = _login.value.smsCode.trim()
        viewModelScope.launch {
            _login.update { it.copy(busy = true, error = null) }
            try {
                repository.confirmSms(code)
                _signedIn.value = true
                _login.update { LoginUiState() }
            } catch (e: Exception) {
                _login.update { it.copy(busy = false, error = e.message ?: "Verification failed") }
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            _journalMeta.update { it.copy(syncing = true, error = null, statusMessage = null) }
            try {
                val result = repository.sync()
                _journalMeta.update {
                    it.copy(syncing = false, statusMessage = result.message)
                }
            } catch (e: AuthException) {
                _journalMeta.update { it.copy(syncing = false, error = e.message) }
                if (e.message?.contains("Not signed in", ignoreCase = true) == true) {
                    _signedIn.value = false
                }
            } catch (e: Exception) {
                _journalMeta.update { it.copy(syncing = false, error = e.message ?: "Sync failed") }
            }
        }
    }

    fun signOut() {
        cancelBrowserSignIn()
        repository.signOut()
        _signedIn.value = false
        _journalMeta.value = JournalUiState()
        _login.value = LoginUiState()
    }
}

class RunningLogViewModelFactory(
    private val repository: RunningRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RunningLogViewModel::class.java)) {
            return RunningLogViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
