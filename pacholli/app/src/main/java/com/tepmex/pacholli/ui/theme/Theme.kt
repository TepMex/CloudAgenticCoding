package com.tepmex.pacholli.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val Ink = Color(0xFF2C2C2C)
val Paper = Color(0xFFF7F7F7)
val Teal = Color(0xFF148F82)
val CyanLine = Color(0xFF4FC3F7)
val KeyBlue = Color(0xFF1E88E5)
val KeyRed = Color(0xFFE53935)
val KeyGreen = Color(0xFF43A047)
val KeyOrange = Color(0xFFFB8C00)
val KeyGray = Color(0xFF616161)
val AccountGreen = Color(0xFF2E7D32)
val OutRed = Color(0xFFE53935)
val InGreen = Color(0xFF2E7D32)
val Drawer = Color(0xFF333333)

private val scheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    secondary = KeyBlue,
    background = Paper,
    surface = Color.White,
    onSurface = Ink,
    error = OutRed,
)

@Composable
fun PacholliTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    val lightNavigation = !isSystemInDarkTheme()
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = lightNavigation
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
