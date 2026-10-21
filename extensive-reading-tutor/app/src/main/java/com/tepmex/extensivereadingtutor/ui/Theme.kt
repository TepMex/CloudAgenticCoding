package com.tepmex.extensivereadingtutor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TutorBg = Color(0xFFF4F5F8)
val TutorCard = Color.White
val TutorText = Color(0xFF1C1C1E)
val TutorSecondary = Color(0xFF636366)
val TutorMuted = Color(0xFF8E8E93)
val TutorLine = Color(0xFFE7E8EE)
val TutorBlue = Color(0xFF2F6BFF)
val TutorBlueSoft = Color(0xFFEAF1FF)
val TutorGreen = Color(0xFF1F9D57)
val TutorGreenSoft = Color(0xFFE8F7EE)
val TutorAmber = Color(0xFFC47A00)
val TutorAmberSoft = Color(0xFFFFF4E0)
val TutorRed = Color(0xFFE23B4A)
val TutorRedSoft = Color(0xFFFDECEE)
val TutorTrack = Color(0xFFE6E8EE)

private val Colors = lightColorScheme(
    primary = TutorBlue,
    onPrimary = Color.White,
    background = TutorBg,
    surface = TutorCard,
    onBackground = TutorText,
    onSurface = TutorText,
)

@Composable
fun TutorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
