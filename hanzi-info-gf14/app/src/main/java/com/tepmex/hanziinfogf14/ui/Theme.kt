package com.tepmex.hanziinfogf14.ui

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

private val Ink = Color(0xFF1C1915)
private val Paper = Color(0xFFF4F0E6)
private val Celadon = Color(0xFF1E4D45)
private val CeladonDeep = Color(0xFF0E2E2A)

private val LightColors = lightColorScheme(
    primary = Celadon,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E6E1),
    onPrimaryContainer = CeladonDeep,
    secondary = Color(0xFF8C3A32),
    onSecondary = Color.White,
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D4),
    onSurfaceVariant = Color(0xFF5C564C),
    outline = Color(0xFFC9BBA8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FCBBE),
    onPrimary = Color(0xFF08332E),
    primaryContainer = Celadon,
    onPrimaryContainer = Color(0xFFE5F4F0),
    secondary = Color(0xFFE7A59C),
    onSecondary = Color(0xFF3A1210),
    background = Color(0xFF141311),
    onBackground = Color(0xFFF3EBDF),
    surface = Color(0xFF221F1B),
    onSurface = Color(0xFFF3EBDF),
    surfaceVariant = Color(0xFF3A342C),
    onSurfaceVariant = Color(0xFFD4C8B8),
    outline = Color(0xFF6E655A),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
)

@Composable
fun HanziInfoGf14Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
