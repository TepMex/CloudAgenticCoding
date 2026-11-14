package dev.eareye.t.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Eye = Color(0xFF1F6FEB)
private val Ear = Color(0xFFC2410C)
private val Overlap = Color(0xFF6D28D9)
private val Unknown = Color(0xFF6B7280)
private val Idle = Color(0xFFD6DEDE)
private val Off = Color(0xFF9CA3AF)

object TrackColors {
    val eye: Color = Eye
    val ear: Color = Ear
    val overlap: Color = Overlap
    val unknown: Color = Unknown
    val idle: Color = Idle
    val poweredOff: Color = Off
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6E6E),
    onPrimary = Color.White,
    secondary = Color(0xFF1F6FEB),
    tertiary = Color(0xFFC2410C),
    background = Color(0xFFF4F7F7),
    surface = Color(0xFFF4F7F7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7DDEDE),
    secondary = Color(0xFF9CC2FF),
    tertiary = Color(0xFFF0B089),
    background = Color(0xFF101414),
    surface = Color(0xFF101414),
)

@Composable
fun EarEyeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
