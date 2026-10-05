package com.tepmex.byokassistedreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShelfScreen(
    status: String?,
    canContinue: Boolean,
    onOpen: () -> Unit,
    onContinue: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("BYOK-assisted-reader", style = MaterialTheme.typography.titleLarge)
        Text(
            "Читалка китайского EPUB. Четыре слоя: текст, пиньинь, справка и структура предложения. Справка открывает незнакомые слова, чэнъюи, имена, подстрочный перевод и пересказ. На пиньине свайп включает чтение для всех иероглифов.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onOpen) { Text("Открыть EPUB") }
        if (canContinue) {
            TextButton(onClick = onContinue) { Text("Продолжить") }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Настройки")
        }
        if (!status.isNullOrBlank()) {
            Text(status, color = MaterialTheme.colorScheme.error)
        }
    }
}
