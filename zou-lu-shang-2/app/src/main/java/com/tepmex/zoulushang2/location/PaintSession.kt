package com.tepmex.zoulushang2.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PaintSessionState(
    val isRunning: Boolean = false,
    val strokesApplied: Int = 0,
    val lastLatitude: Double? = null,
    val lastLongitude: Double? = null,
)

object PaintSession {
    private val _state = MutableStateFlow(PaintSessionState())
    val state: StateFlow<PaintSessionState> = _state.asStateFlow()

    fun start() {
        _state.value = PaintSessionState(isRunning = true)
    }

    fun stop() {
        _state.value = PaintSessionState(isRunning = false)
    }

    fun recordStroke(
        latitude: Double,
        longitude: Double,
        strokesApplied: Int,
    ) {
        _state.value = _state.value.copy(
            strokesApplied = _state.value.strokesApplied + strokesApplied,
            lastLatitude = latitude,
            lastLongitude = longitude,
        )
    }
}
