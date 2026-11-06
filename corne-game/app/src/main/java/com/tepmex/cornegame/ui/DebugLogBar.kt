package com.tepmex.cornegame.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Полоса внизу экрана. Свёрнутая показывает последние строки, развёрнутая — весь буфер.
 * Запись идёт и когда полоса свёрнута. «Копировать» и «Отправить» отдают весь текст.
 */
@Composable
fun DebugLogBar(
    lines: List<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    val text = if (lines.isEmpty()) "лог пуст" else lines.joinToString("\n")
    LaunchedEffect(lines.size, expanded) {
        if (!expanded) return@LaunchedEffect
        withFrameNanos { }
        scroll.scrollTo(scroll.maxValue)
    }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Свернуть" else "Лог ${lines.size}")
            }
            TextButton(
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Corne debug", text)))
                        copied = true
                    }
                },
            ) {
                Text(if (copied) "Скопировано" else "Копировать")
            }
            TextButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Corne debug")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    val chooser = Intent.createChooser(send, "Отправить лог")
                    try {
                        context.startActivity(chooser)
                    } catch (_: ActivityNotFoundException) {
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Corne debug", text)),
                            )
                            copied = true
                        }
                    }
                },
            ) {
                Text("Отправить")
            }
            TextButton(onClick = onClear) {
                Text("Очистить")
            }
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .verticalScroll(scroll)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        "Нажмите клавишу на Corne или на экране. Здесь будет код клавиши и что изменилось в интерфейсе.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                if (lines.isEmpty()) {
                    Text(
                        "Лог пишется. Нажмите клавишу — здесь появятся код и реакция экрана.",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    )
                } else {
                    lines.takeLast(3).forEach { line ->
                        Text(
                            text = abbreviateDebugLine(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
