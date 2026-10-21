package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.absoluteValue

private data class CoverPalette(val top: Color, val bottom: Color, val ink: Color)

private val CoverPalettes = listOf(
    CoverPalette(Color(0xFF6A4A2A), Color(0xFF24180F), Color(0xFFF3DDB0)),
    CoverPalette(Color(0xFF244066), Color(0xFF0C1626), Color(0xFFD7E6F6)),
    CoverPalette(Color(0xFF44524C), Color(0xFF1B2421), Color(0xFFF4ECDC)),
    CoverPalette(Color(0xFF8A3A38), Color(0xFF3F1717), Color(0xFFF7E8CC)),
)

@Composable
fun ScreenHeader(
    title: String? = null,
    large: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (onBack != null || title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(40.dp)) {
                if (onBack != null) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("‹", fontSize = 28.sp, color = TutorText)
                    }
                } else {
                    Spacer(Modifier.width(36.dp))
                }
                Text(
                    title.orEmpty(),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TutorText,
                )
                Box(Modifier.widthIn(min = 36.dp), contentAlignment = Alignment.CenterEnd) { trailing() }
            }
        }
        if (large != null) {
            Text(large, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TutorText, letterSpacing = (-0.6).sp)
        }
    }
}

@Composable
fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) TutorBlue else TutorTrack)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else TutorMuted, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE8EAEF))
            .padding(3.dp),
    ) {
        options.forEach { (id, label) ->
            val selected = id == value
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onChange(id) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) TutorText else TutorSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ProgressBar(percent: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(TutorTrack),
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(5.dp)
                .background(TutorBlue),
        )
    }
}

@Composable
fun BookCover(title: String, author: String, seed: Int, large: Boolean) {
    val palette = CoverPalettes[seed.absoluteValue % CoverPalettes.size]
    val glyphs = titleCodePoints(title).take(4)
    Column(
        Modifier
            .size(if (large) 86.dp else 54.dp, if (large) 122.dp else 78.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.linearGradient(listOf(palette.top, palette.bottom)))
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
            .padding(horizontal = 3.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            glyphs.forEach { glyph ->
                Text(glyph, color = palette.ink, fontWeight = FontWeight.Bold, fontSize = if (large) 18.sp else 13.sp, lineHeight = if (large) 20.sp else 15.sp)
            }
        }
        Text(author.take(10), color = palette.ink.copy(alpha = 0.8f), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = TutorMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TutorCard)
            .border(1.dp, TutorLine, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) { content() }
}

@Composable
fun Pill(text: String, fg: Color, bg: Color) {
    Text(
        text,
        color = fg,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun ChoiceChips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { (id, label) ->
            val on = id == selected
            Text(
                label,
                color = if (on) Color.White else TutorText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (on) TutorBlue else TutorCard)
                    .border(1.dp, if (on) TutorBlue else TutorLine, RoundedCornerShape(99.dp))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

private fun titleCodePoints(title: String): List<String> {
    val glyphs = mutableListOf<String>()
    var index = 0
    while (index < title.length && glyphs.size < 4) {
        val codePoint = title.codePointAt(index)
        glyphs += String(Character.toChars(codePoint))
        index += Character.charCount(codePoint)
    }
    return glyphs.ifEmpty { listOf("书") }
}
