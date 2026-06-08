package com.tepmex.zoulushang.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class FillMapSessionState(
    val isRunning: Boolean = false,
    val cityId: Long? = null,
    val endsAtMillis: Long? = null,
    val samplesTaken: Int = 0,
)

object FillMapSession {
    private val _state = MutableStateFlow(FillMapSessionState())
    val state: StateFlow<FillMapSessionState> = _state.asStateFlow()

    fun start(cityId: Long, endsAtMillis: Long) {
        _state.value = FillMapSessionState(
            isRunning = true,
            cityId = cityId,
            endsAtMillis = endsAtMillis,
            samplesTaken = 0,
        )
    }

    fun recordSample() {
        _state.update { it.copy(samplesTaken = it.samplesTaken + 1) }
    }

    fun stop() {
        _state.value = FillMapSessionState()
    }
}
