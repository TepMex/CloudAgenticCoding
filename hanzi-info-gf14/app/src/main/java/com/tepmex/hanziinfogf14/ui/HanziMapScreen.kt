package com.tepmex.hanziinfogf14.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.hanziinfogf14.R
import com.tepmex.hanziinfogf14.data.ComponentGlyph
import com.tepmex.hanziinfogf14.domain.HexLayout

@Composable
fun HanziMapScreen(viewModel: MapViewModel) {
    val snackbar = remember { SnackbarHostState() }
    val copied = stringResource(R.string.link_copied)
    val clipboard = LocalClipboardManager.current

    BackHandler(enabled = viewModel.canGoBack) {
        viewModel.back()
    }

    LaunchedEffect(viewModel.copyPulse) {
        if (viewModel.copyPulse > 0) {
            snackbar.showSnackbar(copied)
        }
    }

    val catalog = viewModel.catalog
    val center = viewModel.center
    val branches = if (center.isEmpty()) emptyList() else catalog?.branchesOf(center).orEmpty()
    val visible = HexLayout.visibleBranches(branches)
    val known = catalog?.contains(center) == true

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row {
                    if (viewModel.canGoBack) {
                        TextButton(onClick = viewModel::back) {
                            Text(stringResource(R.string.back))
                        }
                    }
                    TextButton(
                        onClick = {
                            val link = viewModel.linkForCenter() ?: return@TextButton
                            clipboard.setText(AnnotatedString(link))
                            viewModel.noteLinkCopied()
                        },
                        enabled = center.isNotEmpty(),
                    ) {
                        Text(stringResource(R.string.copy_link))
                    }
                }
            }
            OutlinedTextField(
                value = viewModel.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.search_label)) },
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                textStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            )
            when {
                catalog == null && viewModel.loadError == null -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
                }
                viewModel.loadError != null && catalog == null -> {
                    Text(
                        stringResource(R.string.load_failed, viewModel.loadError ?: ""),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                center.isNotEmpty() && !known && catalog != null -> {
                    Text(
                        stringResource(R.string.missing),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(R.string.hint_gestures),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                center.isNotEmpty() && known && catalog != null -> {
                    val parts = catalog.componentsOf(center)
                        .joinToString(" + ") { ComponentGlyph.display(it) }
                    Text(
                        text = parts.ifEmpty { "—" },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                    if (visible.isEmpty()) {
                        Text(
                            stringResource(R.string.no_neighbors),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            visible.forEachIndexed { index, branch ->
                                Text(
                                    text = "${HexLayout.directionArrows[index]} ${ComponentGlyph.display(branch.component)} ${branch.characters.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.hint_gestures),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (center.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_map),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HexMap(
                    center = center,
                    branches = branches,
                    contentDescription = stringResource(R.string.map_description, center),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onSelect = { viewModel.open(it, recordHistory = true) },
                )
            }
        }
    }
}
