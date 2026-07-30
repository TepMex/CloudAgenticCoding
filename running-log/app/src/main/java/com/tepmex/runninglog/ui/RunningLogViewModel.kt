package com.tepmex.runninglog.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.runninglog.data.RunningRepository
import com.tepmex.runninglog.mi.AuthException
import com.tepmex.runninglog.mi.CaptchaRequiredException
import com.tepmex.runninglog.mi.DeviceUntrustedException
import com.tepmex.runninglog.ui.journal.JournalUiState
import com.tepmex.runninglog.ui.login.LoginStep
import com.tepmex.runninglog.ui.login.LoginUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RunningLogViewModel(
    private val repository: RunningRepository,
) : ViewModel() {
    private val _signedIn = MutableStateFlow(repository.isSignedIn())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _journalMeta = MutableStateFlow(JournalUiState())
    val journal: StateFlow<JournalUiState> = combine(
        repository.activities,
        _journalMeta,
    ) { activities, meta ->
        meta.copy(activities = activities)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun onUsernameChange(v: String) = _login.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _login.update { it.copy(password = v, error = null) }
    fun onRegionChange(v: String) = _login.update { it.copy(region = v) }
    fun onSmsCodeChange(v: String) = _login.update { it.copy(smsCode = v, error = null) }

    fun backToCredentials() = _login.update {
        it.copy(step = LoginStep.Credentials, smsCode = "", error = null, busy = false)
    }

    fun submitCredentials() {
        val s = _login.value
        viewModelScope.launch {
            _login.update { it.copy(busy = true, error = null) }
            try {
                repository.login(s.username.trim(), s.password, s.region)
                _signedIn.value = true
                _login.update { LoginUiState(region = s.region, username = s.username) }
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
                            error = "Captcha required by Xiaomi (${e.captchaUrl}). Try again later or from a trusted device.",
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
