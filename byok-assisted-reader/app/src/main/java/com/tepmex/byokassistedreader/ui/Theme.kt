package com.tepmex.byokassistedreader.ui

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
import com.tepmex.byokassistedreader.domain.StpvoRole

private val Ink = Color(0xFF1C1915)
private val Paper = Color(0xFFF6F1E7)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B3A4B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E3EA),
    onPrimaryContainer = Color(0xFF0E2430),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF5),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE7E0D4),
    onSurfaceVariant = Color(0xFF5C564C),
    outline = Color(0xFFC9BBA8),
    error = Color(0xFF8C3A32),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9EC9D6),
    onPrimary = Color(0xFF08303A),
    primaryContainer = Color(0xFF1B3A4B),
    onPrimaryContainer = Color(0xFFD5E3EA),
    background = Color(0xFF141311),
    onBackground = Color(0xFFF3EDE3),
    surface = Color(0xFF1C1B19),
    onSurface = Color(0xFFF3EDE3),
    surfaceVariant = Color(0xFF2A2825),
    onSurfaceVariant = Color(0xFFD0C6B8),
    outline = Color(0xFF5C564C),
)

private val AppTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
)

@Composable
fun ReaderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

fun roleColor(role: StpvoRole, dark: Boolean): Color = when (role) {
    StpvoRole.SUBJECT -> if (dark) Color(0xFF3D5F8A) else Color(0xFFBBD4F5)
    StpvoRole.TIME -> if (dark) Color(0xFF7A5A20) else Color(0xFFF6D7A2)
    StpvoRole.PLACE -> if (dark) Color(0xFF2E5C3A) else Color(0xFFC8E6C9)
    StpvoRole.VERB -> if (dark) Color(0xFF7A3030) else Color(0xFFF5C2C0)
    StpvoRole.OBJECT -> if (dark) Color(0xFF5C3D78) else Color(0xFFE4D0F5)
}
