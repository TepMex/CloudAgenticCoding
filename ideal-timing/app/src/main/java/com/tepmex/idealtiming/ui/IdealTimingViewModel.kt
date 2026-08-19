package com.tepmex.idealtiming.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.idealtiming.data.DeviceLocationSource
import com.tepmex.idealtiming.data.IdealTimingRepository
import com.tepmex.idealtiming.domain.DialSunMarkers
import com.tepmex.idealtiming.domain.GeoPoint
import com.tepmex.idealtiming.domain.IdealClock
import com.tepmex.idealtiming.domain.SunCalculator
import com.tepmex.idealtiming.mi.AuthException
import com.tepmex.idealtiming.mi.BrowserLoginCancelledException
import com.tepmex.idealtiming.mi.BrowserLoginSession
import com.tepmex.idealtiming.mi.BrowserLoginTimeoutException
import com.tepmex.idealtiming.mi.CaptchaRequiredException
import com.tepmex.idealtiming.mi.DeviceUntrustedException
import com.tepmex.idealtiming.mi.NotificationUrlRequiredException
import com.tepmex.idealtiming.notification.SectionNotificationScheduler
import com.tepmex.idealtiming.ui.clock.ClockUiState
import com.tepmex.idealtiming.ui.login.LoginStep
import com.tepmex.idealtiming.ui.login.LoginUiState
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface LoginNavEvent {
    data class OpenBrowser(val url: String) : LoginNavEvent
    data class OpenWebView(val url: String, val cookieMode: Boolean = false) : LoginNavEvent
    data object CloseWebView : LoginNavEvent
}

class IdealTimingViewModel(
    private val repository: IdealTimingRepository,
    private val sectionNotifications: SectionNotificationScheduler,
    private val locationSource: DeviceLocationSource,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _signedIn = MutableStateFlow(repository.isSignedIn())
    val signedIn = _signedIn.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login = _login.asStateFlow()

    private val _nav = MutableSharedFlow<LoginNavEvent>(extraBufferCapacity = 4)
    val navEvents = _nav.asSharedFlow()

    private var location: GeoPoint? = locationSource.cached()

    private val _clock = MutableStateFlow(buildClockState())
    val clock = _clock.asStateFlow()

    private var browserJob: Job? = null
    private var activeSession: BrowserLoginSession? = null
    private var tickJob: Job? = null

    init {
        startTicker()
        refreshLocation()
        if (_signedIn.value) {
            onSignedInSessionStart()
        }
    }

    /**
     * First open of the local day: sync Mi Fitness so wake appears, then schedule
     * same-day section notifications. Later opens the same day skip auto-sync.
     */
    private fun onSignedInSessionStart() {
        if (sectionNotifications.needsDailySchedule()) {
            sync(scheduleAfter = true)
        } else if (repository.currentWake() == null) {
            sync(scheduleAfter = true)
        }
    }

    private fun refreshLocation() {
        viewModelScope.launch {
            location = locationSource.resolve() ?: location
            _clock.update { cur ->
                cur.copy(sunMarkers = computeSunMarkers())
            }
        }
    }

    /** Call after the user grants location permission so markers can appear. */
    fun onLocationPermissionChanged() {
        refreshLocation()
    }

    private fun computeSunMarkers(): DialSunMarkers? {
        val wake = repository.currentWake()?.wakeEpochSec ?: return null
        val point = location ?: return null
        return SunCalculator.dialMarkers(wake, point, zoneId)
    }

    private fun buildClockState(
        syncing: Boolean = false,
        statusMessage: String? = null,
        error: String? = null,
    ): ClockUiState {
        val snap = repository.currentWake()
        val now = System.currentTimeMillis() / 1000L
        val reading = snap?.let { IdealClock.reading(it.wakeEpochSec, now) }
        return ClockUiState(
            reading = reading,
            syncedAtEpochSec = snap?.syncedAtEpochSec ?: 0L,
            sleepScore = snap?.sleepScore ?: 0,
            syncing = syncing,
            statusMessage = statusMessage,
            error = error,
            sunMarkers = computeSunMarkers(),
        )
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                _clock.update { cur ->
                    val snap = repository.currentWake()
                    val now = System.currentTimeMillis() / 1000L
                    cur.copy(
                        reading = snap?.let { IdealClock.reading(it.wakeEpochSec, now) },
                        syncedAtEpochSec = snap?.syncedAtEpochSec ?: cur.syncedAtEpochSec,
                        sleepScore = snap?.sleepScore ?: cur.sleepScore,
                        sunMarkers = computeSunMarkers(),
                    )
                }
                delay(30_000)
            }
        }
    }

    private fun scheduleSectionNotifications() {
        val wake = repository.currentWake()?.wakeEpochSec ?: return
        sectionNotifications.scheduleForDay(wakeEpochSec = wake)
    }

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
                sync(scheduleAfter = true)
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
                sync(scheduleAfter = true)
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
                sync(scheduleAfter = true)
            } catch (e: NotificationUrlRequiredException) {
                _login.update {
                    it.copy(
                        busy = true,
                        waitingBrowser = true,
                        statusMessage = "Xiaomi needs extra verification…",
                        error = null,
                    )
                }
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
                } catch (_: CaptchaRequiredException) {
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
                sync(scheduleAfter = true)
            } catch (e: Exception) {
                _login.update { it.copy(busy = false, error = e.message ?: "Verification failed") }
            }
        }
    }

    fun sync(scheduleAfter: Boolean = true) {
        viewModelScope.launch {
            _clock.update { it.copy(syncing = true, error = null, statusMessage = null) }
            location = locationSource.resolve() ?: location
            try {
                val result = repository.syncWake()
                _clock.value = buildClockState(
                    syncing = false,
                    statusMessage = result.message,
                )
                if (scheduleAfter) {
                    scheduleSectionNotifications()
                }
            } catch (e: AuthException) {
                _clock.update {
                    it.copy(
                        syncing = false,
                        error = e.message,
                        sunMarkers = computeSunMarkers(),
                    )
                }
                if (e.message?.contains("Not signed in", ignoreCase = true) == true) {
                    _signedIn.value = false
                }
                // First open of day with a prior wake: still schedule from stored wake.
                if (scheduleAfter && repository.currentWake() != null) {
                    scheduleSectionNotifications()
                }
            } catch (e: Exception) {
                _clock.update {
                    it.copy(
                        syncing = false,
                        error = e.message ?: "Sync failed",
                        sunMarkers = computeSunMarkers(),
                    )
                }
                if (scheduleAfter && repository.currentWake() != null) {
                    scheduleSectionNotifications()
                }
            }
        }
    }

    fun consumeClockMessage() {
        _clock.update { it.copy(statusMessage = null, error = null) }
    }

    fun signOut() {
        cancelBrowserSignIn()
        repository.signOut()
        sectionNotifications.cancelAll()
        _signedIn.value = false
        _clock.value = buildClockState()
        _login.value = LoginUiState()
    }
}

class IdealTimingViewModelFactory(
    private val repository: IdealTimingRepository,
    private val sectionNotifications: SectionNotificationScheduler,
    private val locationSource: DeviceLocationSource,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(IdealTimingViewModel::class.java)) {
            return IdealTimingViewModel(repository, sectionNotifications, locationSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel ${modelClass.name}")
    }
}
