package com.tepmex.zoulushang2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PurplePrimary = Color(0xFF7B1FA2)
private val PurpleDark = Color(0xFF4A148C)

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    secondary = PurpleDark,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCE93D8),
    onPrimary = Color.Black,
    secondary = Color(0xFFE1BEE7),
)

@Composable
fun ZouLuShang2Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
