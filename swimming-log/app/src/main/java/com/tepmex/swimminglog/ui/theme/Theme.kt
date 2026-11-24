package com.tepmex.swimminglog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DeepWater = Color(0xFF0E4D6C)
private val Foam = Color(0xFFE7F2F6)
private val Ink = Color(0xFF12242C)
private val Coral = Color(0xFFC45C3E)
private val Mist = Color(0xFFF4F8F9)

private val LightColors = lightColorScheme(
    primary = DeepWater,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5DCE8),
    onPrimaryContainer = Ink,
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6D7CC),
    onSecondaryContainer = Ink,
    background = Mist,
    onBackground = Ink,
    surface = Foam,
    onSurface = Ink,
    surfaceVariant = Color(0xFFD3E3EA),
    onSurfaceVariant = Color(0xFF3A4E58),
    outline = Color(0xFF7A909A),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
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
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
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
fun SwimmingLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
