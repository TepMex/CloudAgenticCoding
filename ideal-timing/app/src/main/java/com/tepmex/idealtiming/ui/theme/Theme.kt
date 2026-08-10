package com.tepmex.idealtiming.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Parchment / map-clock palette (not Material purple). */
val Parchment = Color(0xFFE6D7B8)
val ParchmentDeep = Color(0xFFC9B48A)
val Ink = Color(0xFF2A2118)
val Gold = Color(0xFFC9A227)
val GoldBright = Color(0xFFE6C65C)
val Sector1 = Color(0xFF3F6B4A) // health — moss green
val Sector2 = Color(0xFF5C4033) // tactics — dark brown
val Sector3 = Color(0xFF6B2E2E) // tactics afternoon — deep red
val Sector4 = Color(0xFF2F3A5C) // rest — dusk blue
val JewelBlue = Color(0xFF3A6EA5)

private val LightColors = lightColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = ParchmentDeep,
    onPrimaryContainer = Ink,
    secondary = JewelBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E2F0),
    onSecondaryContainer = Ink,
    background = Parchment,
    onBackground = Ink,
    surface = Color(0xFFF0E4C9),
    onSurface = Ink,
    surfaceVariant = Color(0xFFD8C9A8),
    onSurfaceVariant = Color(0xFF4A3F32),
    outline = Color(0xFF8A7A5C),
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
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Serif,
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
fun IdealTimingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
