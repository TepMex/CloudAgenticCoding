package com.tepmex.heilauncher.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tepmex.heilauncher.domain.FitnessAverages
import com.tepmex.heilauncher.timing.data.IdealTimingRepository
import com.tepmex.heilauncher.timing.mi.AuthException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class FitnessUiState(
    val signedIn: Boolean,
    val loading: Boolean,
    val averages: FitnessAverages?,
    val failed: Boolean,
)

class FitnessViewModel(
    private val repository: IdealTimingRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val active = MutableStateFlow(false)
    private val _ui = MutableStateFlow(
        FitnessUiState(
            signedIn = repository.isSignedIn(),
            loading = false,
            averages = null,
            failed = false,
        ),
    )
    val ui = _ui.asStateFlow()

    private var fetchedOn: LocalDate? = null
    private var fetchedAtMs: Long = 0L

    init {
        viewModelScope.launch {
            active.collectLatest { on ->
                if (!on) return@collectLatest
                while (isActive) {
                    refreshIfStale()
                    delay(STALE_MS)
                }
            }
        }
    }

    fun setActive(value: Boolean) {
        active.value = value
    }

    private suspend fun refreshIfStale() {
        val today = LocalDate.now(zone)
        if (!repository.isSignedIn()) {
            fetchedOn = null
            fetchedAtMs = 0L
            _ui.value = FitnessUiState(
                signedIn = false,
                loading = false,
                averages = null,
                failed = false,
            )
            return
        }
        val fresh = fetchedOn == today &&
            System.currentTimeMillis() - fetchedAtMs < STALE_MS &&
            _ui.value.averages != null
        if (fresh) return
        _ui.update { it.copy(signedIn = true, loading = it.averages == null, failed = false) }
        try {
            val history = repository.loadFitnessHistory(today, zone)
            val averages = FitnessAverages.compute(history.steps, history.sleepHours, today)
            fetchedOn = today
            fetchedAtMs = System.currentTimeMillis()
            _ui.value = FitnessUiState(
                signedIn = true,
                loading = false,
                averages = averages,
                failed = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: AuthException) {
            val stillSignedIn = repository.isSignedIn()
            _ui.value = FitnessUiState(
                signedIn = stillSignedIn,
                loading = false,
                averages = if (stillSignedIn) _ui.value.averages else null,
                failed = stillSignedIn,
            )
        } catch (_: Exception) {
            _ui.update { it.copy(signedIn = true, loading = false, failed = true) }
        }
    }

    private companion object {
        const val STALE_MS = 30 * 60 * 1000L
    }
}
