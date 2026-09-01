package com.tepmex.wodeluyou.ui.theme

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

val Cinnabar = Color(0xFFC23A2B)
val CinnabarDeep = Color(0xFF8B1E18)
val Cream = Color(0xFFFBF4EA)
val Paper = Color(0xFFFFFDF8)
val Ink = Color(0xFF1C1410)
val Gold = Color(0xFFC9A227)
val Moss = Color(0xFF3F6B4A)
val Dust = Color(0xFF6B5E52)

private val LightColors = lightColorScheme(
    primary = Cinnabar,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3D0C8),
    onPrimaryContainer = CinnabarDeep,
    secondary = Moss,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E4D6),
    onSecondaryContainer = Color(0xFF1C3322),
    tertiary = Gold,
    background = Cream,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE4D4),
    onSurfaceVariant = Dust,
    outline = Color(0xFFC9B8A4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE07A6A),
    onPrimary = Color(0xFF3A0C08),
    primaryContainer = CinnabarDeep,
    onPrimaryContainer = Color(0xFFFFE8E2),
    secondary = Color(0xFF9BC4A4),
    onSecondary = Color(0xFF102016),
    background = Color(0xFF16110E),
    onBackground = Color(0xFFF4E8D8),
    surface = Color(0xFF221C18),
    onSurface = Color(0xFFF4E8D8),
    surfaceVariant = Color(0xFF3A312B),
    onSurfaceVariant = Color(0xFFD4C4B4),
    outline = Color(0xFF6B5E52),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
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
fun WoDeLuyouTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
