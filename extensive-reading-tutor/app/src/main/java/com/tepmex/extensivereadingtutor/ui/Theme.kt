package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class TutorColors(
    val bg: Color,
    val card: Color,
    val text: Color,
    val secondary: Color,
    val muted: Color,
    val line: Color,
    val blue: Color,
    val blueSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val red: Color,
    val redSoft: Color,
    val track: Color,
)

private val LightColors = TutorColors(
    bg = Color(0xFFF4F5F8),
    card = Color.White,
    text = Color(0xFF1C1C1E),
    secondary = Color(0xFF636366),
    muted = Color(0xFF8E8E93),
    line = Color(0xFFE7E8EE),
    blue = Color(0xFF2F6BFF),
    blueSoft = Color(0xFFEAF1FF),
    green = Color(0xFF1F9D57),
    greenSoft = Color(0xFFE8F7EE),
    amber = Color(0xFFC47A00),
    amberSoft = Color(0xFFFFF4E0),
    red = Color(0xFFE23B4A),
    redSoft = Color(0xFFFDECEE),
    track = Color(0xFFE6E8EE),
)

private val DarkColors = TutorColors(
    bg = Color(0xFF121214),
    card = Color(0xFF1C1C1E),
    text = Color(0xFFF2F2F7),
    secondary = Color(0xFFAEAEB2),
    muted = Color(0xFF8E8E93),
    line = Color(0xFF2C2C2E),
    blue = Color(0xFF7AA2FF),
    blueSoft = Color(0xFF1A2744),
    green = Color(0xFF4CD484),
    greenSoft = Color(0xFF123322),
    amber = Color(0xFFFFB340),
    amberSoft = Color(0xFF3A2A10),
    red = Color(0xFFFF6B74),
    redSoft = Color(0xFF3A161B),
    track = Color(0xFF2C2C2E),
)

val LocalTutorColors = staticCompositionLocalOf { LightColors }

val TutorBg: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.bg
val TutorCard: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.card
val TutorText: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.text
val TutorSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.secondary
val TutorMuted: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.muted
val TutorLine: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.line
val TutorBlue: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.blue
val TutorBlueSoft: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.blueSoft
val TutorGreen: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.green
val TutorGreenSoft: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.greenSoft
val TutorAmber: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.amber
val TutorAmberSoft: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.amberSoft
val TutorRed: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.red
val TutorRedSoft: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.redSoft
val TutorTrack: Color
    @Composable @ReadOnlyComposable get() = LocalTutorColors.current.track

@Composable
fun TutorTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = colors.blue, onPrimary = Color.White, background = colors.bg, surface = colors.card, onBackground = colors.text, onSurface = colors.text)
    } else {
        lightColorScheme(primary = colors.blue, onPrimary = Color.White, background = colors.bg, surface = colors.card, onBackground = colors.text, onSurface = colors.text)
    }
    CompositionLocalProvider(LocalTutorColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
