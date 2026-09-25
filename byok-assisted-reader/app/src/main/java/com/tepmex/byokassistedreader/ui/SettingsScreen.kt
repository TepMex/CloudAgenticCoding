package com.tepmex.byokassistedreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tepmex.byokassistedreader.data.ReaderSettings
import com.tepmex.byokassistedreader.domain.KnownLexicon

@Composable
fun SettingsScreen(
    initial: ReaderSettings,
    onSave: (String, String, String, String, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var baseUrl by rememberSaveable(initial.baseUrl) { mutableStateOf(initial.baseUrl) }
    var token by rememberSaveable(initial.token) { mutableStateOf(initial.token) }
    var model by rememberSaveable(initial.model) { mutableStateOf(initial.model) }
    var known by rememberSaveable(initial.knownWords) { mutableStateOf(initial.knownWords) }
    var volume by rememberSaveable(initial.volumeKeys) { mutableStateOf(initial.volumeKeys) }
    var showToken by rememberSaveable { mutableStateOf(false) }
    val hanzi = KnownLexicon.hanzi(known)
    val words = KnownLexicon.words(known)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge)
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
        )
        TextButton(onClick = { showToken = !showToken }) {
            Text(if (showToken) "Скрыть токен" else "Показать токен")
        }
        OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Модель") },
            placeholder = { Text("gpt-4o-mini") },
            singleLine = true,
        )
        OutlinedTextField(
            value = known,
            onValueChange = { known = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Известные слова") },
            placeholder = { Text("По одному слову на строку") },
            minLines = 6,
        )
        Text("Слов: ${words.size}. Иероглифов: ${hanzi.size}.")
        Text(hanzi.joinToString(""), style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = volume, onCheckedChange = { volume = it })
            Text(
                "Клавиши громкости переключают слои",
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Button(onClick = { onSave(baseUrl, token, model, known, volume) }) { Text("Сохранить") }
        TextButton(onClick = onBack) { Text("Назад") }
    }
}
