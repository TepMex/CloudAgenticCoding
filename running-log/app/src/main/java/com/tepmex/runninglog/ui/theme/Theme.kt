package com.tepmex.runninglog.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TrailGreen = Color(0xFF1F4D3A)
private val Moss = Color(0xFF3A6B52)
private val Stone = Color(0xFFE8E2D6)
private val Bark = Color(0xFF2C241B)
private val Clay = Color(0xFFB85C38)
private val Mist = Color(0xFFF3EFE6)

private val LightColors = lightColorScheme(
    primary = TrailGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC5D9CC),
    onPrimaryContainer = Bark,
    secondary = Clay,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0D5C8),
    onSecondaryContainer = Bark,
    background = Mist,
    onBackground = Bark,
    surface = Stone,
    onSurface = Bark,
    surfaceVariant = Color(0xFFD9D2C4),
    onSurfaceVariant = Color(0xFF4A433A),
    outline = Color(0xFF8A8276),
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
fun RunningLogTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
