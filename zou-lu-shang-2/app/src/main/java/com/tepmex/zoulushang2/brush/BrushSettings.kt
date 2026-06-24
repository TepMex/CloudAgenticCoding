package com.tepmex.zoulushang2.brush

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BrushSettings(
    val colorArgb: Int = DEFAULT_COLOR,
    val thicknessMeters: Float = DEFAULT_THICKNESS_METERS,
) {
    companion object {
        const val DEFAULT_COLOR = 0xFF7B1FA2.toInt()
        const val DEFAULT_THICKNESS_METERS = 8f
        const val MIN_THICKNESS_METERS = 2f
        const val MAX_THICKNESS_METERS = 50f

        val PALETTE = listOf(
            0xFFE53935.toInt(),
            0xFFFB8C00.toInt(),
            0xFFFDD835.toInt(),
            0xFF43A047.toInt(),
            0xFF1E88E5.toInt(),
            0xFF7B1FA2.toInt(),
            0xFF212121.toInt(),
            0xFFFFFFFF.toInt(),
        )
    }
}

object BrushSettingsStore {
    private val _settings = MutableStateFlow(BrushSettings())
    val settings: StateFlow<BrushSettings> = _settings.asStateFlow()

    fun setColor(colorArgb: Int) {
        _settings.value = _settings.value.copy(colorArgb = colorArgb)
    }

    fun setThickness(thicknessMeters: Float) {
        _settings.value = _settings.value.copy(
            thicknessMeters = thicknessMeters.coerceIn(
                BrushSettings.MIN_THICKNESS_METERS,
                BrushSettings.MAX_THICKNESS_METERS,
            ),
        )
    }
}
