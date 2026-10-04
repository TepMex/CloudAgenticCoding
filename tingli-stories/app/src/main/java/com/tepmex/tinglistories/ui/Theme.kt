package com.tepmex.tinglistories.ui

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

private val Ink = Color(0xFF1C1916)
private val Paper = Color(0xFFF3EEE6)
private val Cinnabar = Color(0xFF8E3428)
private val Night = Color(0xFF141210)

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D2CB),
    onPrimaryContainer = Color(0xFF3C120E),
    secondary = Color(0xFF1F6B4A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5EDE1),
    onSecondaryContainer = Color(0xFF0C2E1F),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF6),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D6),
    onSurfaceVariant = Color(0xFF5C564C),
    outline = Color(0xFFD0C4B6),
    error = Color(0xFF8C3A32),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE7B2A8),
    onPrimary = Color(0xFF3C120E),
    primaryContainer = Color(0xFF6E2A22),
    onPrimaryContainer = Color(0xFFF3D2CB),
    secondary = Color(0xFF9ED4B8),
    onSecondary = Color(0xFF0C2E1F),
    secondaryContainer = Color(0xFF1F4A36),
    onSecondaryContainer = Color(0xFFD5EDE1),
    background = Night,
    onBackground = Color(0xFFF3EDE3),
    surface = Color(0xFF1C1A18),
    onSurface = Color(0xFFF3EDE3),
    surfaceVariant = Color(0xFF2A2724),
    onSurfaceVariant = Color(0xFFD0C6B8),
    outline = Color(0xFF5C564C),
    error = Color(0xFFFFB4AB),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
)

@Composable
fun TingliTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
