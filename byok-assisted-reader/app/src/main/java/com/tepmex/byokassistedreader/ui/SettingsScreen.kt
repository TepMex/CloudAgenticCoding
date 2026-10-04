package com.tepmex.byokassistedreader.ui

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tepmex.byokassistedreader.data.ReaderSettings
import com.tepmex.byokassistedreader.domain.KnownLexicon
import com.tepmex.byokassistedreader.domain.RubyFit
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The known-words editor never grows past this share of the settings viewport. */
internal const val KnownWordsFieldMaxFraction = 0.5f

@Composable
fun SettingsScreen(
    initial: ReaderSettings,
    onSave: (String, String, String, String, Boolean, Int) -> Unit,
    onBack: () -> Unit,
) {
    var baseUrl by rememberSaveable(initial.baseUrl) { mutableStateOf(initial.baseUrl) }
    var token by rememberSaveable(initial.token) { mutableStateOf(initial.token) }
    var model by rememberSaveable(initial.model) { mutableStateOf(initial.model) }
    var known by rememberSaveable(initial.knownWords) { mutableStateOf(initial.knownWords) }
    var volume by rememberSaveable(initial.volumeKeys) { mutableStateOf(initial.volumeKeys) }
    var chars by rememberSaveable(initial.charsPerLine) { mutableIntStateOf(initial.charsPerLine) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    val hanzi = KnownLexicon.hanzi(known)
    val words = KnownLexicon.words(known)
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val padPx = with(density) { ReaderHorizontalPadding.roundToPx() }
        val contentWidth = (constraints.maxWidth - padPx * 2).coerceAtLeast(1)
        val range = gridMetrics(contentWidth, chars).range
        val shown = RubyFit.resolveColumns(chars, range)
        val knownFieldMax = maxHeight * KnownWordsFieldMaxFraction
        val knownFieldMin = 160.dp.coerceAtMost(knownFieldMax)
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                    Text("Настройки", style = MaterialTheme.typography.titleLarge)
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Токен доступа") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showToken) "Скрыть токен" else "Показать токен",
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Модель") },
                    placeholder = { Text("gpt-4o-mini") },
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = known,
                onValueChange = { known = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = knownFieldMin, max = knownFieldMax),
                label = { Text("Известные слова") },
                placeholder = { Text("По одному слову на строку") },
            )
            IconButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("Известные слова", known)),
                    )
                }
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать")
            }
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Слов: ${words.size}. Иероглифов: ${hanzi.size}.")
                Text(
                    hanzi.joinToString(""),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("Символов в строке: $shown", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Крупный шрифт — ${range.first} в строке. Мелкий — ${range.last}. Пиньинь уменьшается пропорционально клетке, в том числе с 9 по 12 символов.",
                )
                if (range.first < range.last) {
                    Slider(
                        value = shown.toFloat(),
                        onValueChange = { chars = it.roundToInt() },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = (range.last - range.first - 1).coerceAtLeast(0),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = volume, onCheckedChange = { volume = it })
                    Text(
                        "Клавиши громкости переключают слои",
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                FilledIconButton(onClick = {
                    val stored = if (shown == range.first) 0 else shown
                    onSave(baseUrl, token, model, known, volume, stored)
                }) {
                    Icon(Icons.Filled.Save, contentDescription = "Сохранить")
                }
            }
        }
    }
}
