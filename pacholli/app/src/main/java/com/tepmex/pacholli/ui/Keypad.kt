package com.tepmex.pacholli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.pacholli.domain.PadKey
import com.tepmex.pacholli.ui.theme.KeyBlue
import com.tepmex.pacholli.ui.theme.KeyGray
import com.tepmex.pacholli.ui.theme.KeyGreen
import com.tepmex.pacholli.ui.theme.KeyOrange
import com.tepmex.pacholli.ui.theme.KeyRed

@Composable
fun AmountKeypad(onKey: (PadKey) -> Unit, onOk: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.background(Color.White).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            PadButton("AC", KeyRed, Modifier.weight(1f)) { onKey(PadKey.AC) }
            PadButton("DEL", KeyGray, Modifier.weight(1f)) { onKey(PadKey.DEL) }
            PadButton("OK", KeyGreen, Modifier.weight(1f), onOk)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            PadButton("7", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D7) }
            PadButton("8", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D8) }
            PadButton("9", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D9) }
            PadButton("/", KeyOrange, Modifier.weight(1f)) { onKey(PadKey.DIV) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            PadButton("4", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D4) }
            PadButton("5", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D5) }
            PadButton("6", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D6) }
            PadButton("×", KeyOrange, Modifier.weight(1f)) { onKey(PadKey.MUL) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            PadButton("1", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D1) }
            PadButton("2", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D2) }
            PadButton("3", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.D3) }
            PadButton("−", KeyOrange, Modifier.weight(1f)) { onKey(PadKey.MINUS) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            PadButton("0", KeyBlue, Modifier.weight(2f)) { onKey(PadKey.D0) }
            PadButton(".", KeyBlue, Modifier.weight(1f)) { onKey(PadKey.DOT) }
            PadButton("+", KeyOrange, Modifier.weight(1f)) { onKey(PadKey.PLUS) }
        }
    }
}

@Composable
private fun PadButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(56.dp)
            .background(color)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
