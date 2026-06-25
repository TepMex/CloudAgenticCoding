package com.tepmex.zoulushang2.paint

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PaintSettings(
    val maxSpeedKmh: Float = DEFAULT_MAX_SPEED_KMH,
) {
    companion object {
        const val DEFAULT_MAX_SPEED_KMH = 10f
        const val MIN_MAX_SPEED_KMH = 1f
        const val MAX_MAX_SPEED_KMH = 50f
    }
}

object PaintSettingsStore {
    private val _settings = MutableStateFlow(PaintSettings())
    val settings: StateFlow<PaintSettings> = _settings.asStateFlow()

    fun setMaxSpeedKmh(maxSpeedKmh: Float) {
        _settings.value = _settings.value.copy(
            maxSpeedKmh = maxSpeedKmh.coerceIn(
                PaintSettings.MIN_MAX_SPEED_KMH,
                PaintSettings.MAX_MAX_SPEED_KMH,
            ),
        )
    }
}
