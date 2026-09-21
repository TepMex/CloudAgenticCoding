package com.tepmex.duoshaoqian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.duoshaoqian.data.Banknote

@Composable
fun RmbNoteCard(
    note: Banknote,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(if (compact) 6.dp else 8.dp)
    BoxWithConstraints(
        modifier = modifier
            .shadow(if (compact) 2.dp else 6.dp, shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .aspectRatio(2.05f),
    ) {
        val numberSize = if (compact) 18.sp else (maxWidth.value * 0.28f).sp
        val bankSize = if (compact) 7.sp else 10.sp
        NoteFace(
            note = note,
            numberSize = numberSize,
            bankSize = bankSize,
            showOrnament = !compact,
        )
    }
}

@Composable
private fun NoteFace(
    note: Banknote,
    numberSize: androidx.compose.ui.unit.TextUnit,
    bankSize: androidx.compose.ui.unit.TextUnit,
    showOrnament: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(note.background, note.background.copy(alpha = 0.86f), note.accent),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (showOrnament) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            )
            RepeatGuilloche(color = Color.White.copy(alpha = 0.18f))
        }
        Text(
            text = "中国人民银行",
            color = Color.White.copy(alpha = 0.92f),
            fontSize = bankSize,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Text(
            text = note.yuan.toString(),
            color = Color.White,
            fontSize = numberSize,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Column(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = note.chineseValue,
                color = Color.White,
                fontSize = (bankSize.value + 2).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
            )
            Text(
                text = "元",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = bankSize,
                fontFamily = FontFamily.Serif,
            )
        }
        Text(
            text = "¥${note.yuan}",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = bankSize,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun RepeatGuilloche(color: Color) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(color),
            )
        }
    }
}
