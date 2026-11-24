package com.tepmex.hanziinfogf14.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tepmex.hanziinfogf14.R

@Composable
fun SettingsScreen(viewModel: MapViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            TextButton(onClick = viewModel::closeSettings) {
                Text(stringResource(R.string.close_settings))
            }
        }
        Text(
            text = stringResource(R.string.settings_body),
            style = MaterialTheme.typography.bodyMedium,
        )
        val status = when (viewModel.modelPhase) {
            ModelPhase.Absent -> stringResource(R.string.model_absent)
            ModelPhase.Downloading -> stringResource(
                R.string.model_downloading,
                (viewModel.downloadFraction * 100).toInt(),
            )
            ModelPhase.Ready -> stringResource(R.string.model_ready)
            ModelPhase.Failed -> stringResource(
                R.string.model_failed,
                viewModel.downloadError ?: "",
            )
        }
        Text(text = status, style = MaterialTheme.typography.titleMedium)
        if (viewModel.modelPhase == ModelPhase.Downloading) {
            LinearProgressIndicator(
                progress = { viewModel.downloadFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = viewModel::cancelDownload) {
                Text(stringResource(R.string.cancel_download))
            }
        }
        if (viewModel.modelPhase != ModelPhase.Ready && viewModel.modelPhase != ModelPhase.Downloading) {
            Button(onClick = viewModel::downloadModel) {
                Text(stringResource(R.string.download_model))
            }
        }
        if (viewModel.modelPhase == ModelPhase.Ready) {
            TextButton(onClick = viewModel::deleteModel) {
                Text(stringResource(R.string.delete_model))
            }
        }
    }
}
