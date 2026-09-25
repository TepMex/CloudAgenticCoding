package com.tepmex.instantpinyin.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.instantpinyin.R
import com.tepmex.instantpinyin.domain.KnownReading
import com.tepmex.instantpinyin.domain.PlecoLinks
import com.tepmex.instantpinyin.domain.ShownToken
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StillReader(
    state: StillState,
    known: Set<String>,
    onlyKnown: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val plecoMissing = stringResource(R.string.pleco_missing)

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.still_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            StillState.Idle, StillState.Processing -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(R.string.ocr_working),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            is StillState.Failed -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.message, textAlign = TextAlign.Center)
                    FilledTonalButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.back))
                    }
                }
            }
            is StillState.Ready -> {
                val lines = remember(state.lines, known, onlyKnown) {
                    KnownReading.present(state.lines, known, onlyKnown)
                }
                val hasReading = lines.any { line -> line.any { it.glossQuery != null || it.chars.isNotEmpty() } }
                if (!hasReading) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.still_empty),
                            textAlign = TextAlign.Center,
                        )
                        FilledTonalButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                            Text(stringResource(R.string.back))
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.still_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        for (line in lines) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                for (token in line) {
                                    if (token.chars.isEmpty()) {
                                        Text(
                                            text = token.surface,
                                            style = MaterialTheme.typography.headlineSmall,
                                            modifier = Modifier.align(Alignment.CenterVertically),
                                        )
                                    } else {
                                        WordCell(
                                            token = token,
                                            onCharacter = { query ->
                                                if (!openPleco(context, query)) {
                                                    scope.launch { snackbar.showSnackbar(plecoMissing) }
                                                }
                                            },
                                            onGloss = {
                                                val query = token.glossQuery ?: return@WordCell
                                                if (!openPleco(context, query)) {
                                                    scope.launch { snackbar.showSnackbar(plecoMissing) }
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordCell(
    token: ShownToken,
    onCharacter: (String) -> Unit,
    onGloss: () -> Unit,
) {
    val russian = token.russian?.ifBlank { stringResource(R.string.gloss_missing) }
    Card(
        modifier = Modifier.widthIn(min = 72.dp, max = 280.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (char in token.chars) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onCharacter(char.hanzi) },
                    ) {
                        if (char.pinyin.isNotEmpty()) {
                            Text(
                                text = char.pinyin,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Text(
                            text = char.hanzi,
                            fontSize = 28.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            if (russian != null) {
                Text(
                    text = russian,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onGloss),
                )
            }
        }
    }
}

private fun openPleco(context: android.content.Context, query: String): Boolean {
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(PlecoLinks.searchUri(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
