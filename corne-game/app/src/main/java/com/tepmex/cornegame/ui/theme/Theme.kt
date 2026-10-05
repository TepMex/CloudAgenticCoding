package com.tepmex.cornegame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val HighlightYellow = Color(0xFFFFD54A)
val TypedGreenLight = Color(0xFF1B7A32)
val TypedGreenDark = Color(0xFF8FD48A)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A6A00),
    onPrimary = Color(0xFF1C1B16),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFF3E5C4A),
    onSecondary = Color.White,
    background = Color(0xFFF3F1EC),
    onBackground = Color(0xFF1C1B19),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1C1B19),
    surfaceVariant = Color(0xFFE4E0D8),
    onSurfaceVariant = Color(0xFF4A463E),
    outline = Color(0xFF8A8478),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFD54A),
    onPrimary = Color(0xFF241A00),
    primaryContainer = Color(0xFF5C4A00),
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFA8CDB6),
    onSecondary = Color(0xFF143224),
    background = Color(0xFF121410),
    onBackground = Color(0xFFE8E6E1),
    surface = Color(0xFF1C1F1A),
    onSurface = Color(0xFFE8E6E1),
    surfaceVariant = Color(0xFF2A2E28),
    onSurfaceVariant = Color(0xFFC9C4B8),
    outline = Color(0xFF8E8A80),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
)

@Composable
fun CorneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

@Composable
fun typedColor(): Color = if (isSystemInDarkTheme()) TypedGreenDark else TypedGreenLight
