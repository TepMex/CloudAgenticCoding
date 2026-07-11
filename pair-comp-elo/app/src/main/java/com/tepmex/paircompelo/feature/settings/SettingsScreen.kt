package com.tepmex.paircompelo.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tepmex.paircompelo.data.importexport.ImportExportService
import com.tepmex.paircompelo.data.importexport.ImportMode
import com.tepmex.paircompelo.data.repository.PreferenceRepository
import com.tepmex.paircompelo.data.repository.RecalculationProgress
import com.tepmex.paircompelo.domain.model.PairSelectionStrategy
import com.tepmex.paircompelo.domain.model.RankingSettings
import com.tepmex.paircompelo.ui.components.AppScaffold
import com.tepmex.paircompelo.ui.components.ConfirmDialog
import com.tepmex.paircompelo.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val draft: RankingSettings = RankingSettings.Defaults,
    val error: String? = null,
    val status: String? = null,
    val recalculating: RecalculationProgress? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: PreferenceRepository,
    private val importExportService: ImportExportService,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeSettings().collect { settings ->
                _state.update { it.copy(draft = settings) }
            }
        }
        viewModelScope.launch {
            repository.recalculationProgress.collect { progress ->
                _state.update { it.copy(recalculating = progress) }
            }
        }
    }

    fun updateDraft(transform: (RankingSettings) -> RankingSettings) {
        _state.update { it.copy(draft = transform(it.draft), error = null) }
    }

    fun save() {
        viewModelScope.launch {
            runCatching {
                repository.updateSettings(_state.value.draft)
                _state.update { it.copy(status = "Settings saved") }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            repository.resetSettings()
            _state.update { it.copy(status = "Settings reset to defaults") }
        }
    }

    fun recalculate() {
        viewModelScope.launch {
            runCatching {
                repository.recalculateAllRankings()
                _state.update { it.copy(status = "Rankings recalculated") }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch {
            repository.deleteAllData()
            _state.update { it.copy(status = "All application data deleted") }
        }
    }

    suspend fun exportJson(): String = importExportService.exportJson()

    fun importJson(raw: String, mode: ImportMode) {
        viewModelScope.launch {
            runCatching {
                val report = importExportService.importJson(raw, mode)
                _state.update {
                    it.copy(
                        status = "Imported ${report.listsImported} lists, " +
                            "${report.itemsImported} items. Skipped ${report.skipped.size}.",
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val context = LocalContext.current
    var showReset by remember { mutableStateOf(false) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var strategyExpanded by remember { mutableStateOf(false) }
    var pendingImportMode by remember { mutableStateOf<ImportMode?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.viewModelScopeLaunch {
            val json = viewModel.exportJson()
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val mode = pendingImportMode ?: return@rememberLauncherForActivityResult
        pendingImportMode = null
        val raw = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return@rememberLauncherForActivityResult
        viewModel.importJson(raw, mode)
    }

    AppScaffold(title = "Settings", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel("Elo")
            HelpText("Starting score for new items and lists.")
            NumberField("Initial rating", draft.initialRating) {
                viewModel.updateDraft { s -> s.copy(initialRating = it) }
            }
            HelpText("How strongly each comparison moves ratings. Higher = faster change.")
            NumberField("K-factor", draft.kFactor) {
                viewModel.updateDraft { s -> s.copy(kFactor = it) }
            }
            HelpText("Controls how rating gaps translate into expected win chance.")
            NumberField("Rating scale", draft.ratingScale) {
                viewModel.updateDraft { s -> s.copy(ratingScale = it) }
            }

            SectionLabel("Decay")
            SwitchRow(
                title = "Older choices gradually matter less",
                checked = draft.decayEnabled,
                onChecked = { viewModel.updateDraft { s -> s.copy(decayEnabled = it) } },
            )
            HelpText("Daily multiplier toward the initial rating (e.g. 0.995). Must be > 0 and ≤ 1.")
            NumberField("Daily decay rate", draft.decayRatePerDay) {
                viewModel.updateDraft { s -> s.copy(decayRatePerDay = it) }
            }

            SectionLabel("Comparisons")
            NumberField("Minimum comparisons for stability", draft.minimumComparisonsBeforeStable.toDouble()) {
                viewModel.updateDraft { s -> s.copy(minimumComparisonsBeforeStable = it.toInt()) }
            }
            ExposedDropdownMenuBox(
                expanded = strategyExpanded,
                onExpandedChange = { strategyExpanded = it },
            ) {
                OutlinedTextField(
                    value = draft.pairSelectionStrategy.name,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pair selection strategy") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(strategyExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
                ExposedDropdownMenu(
                    expanded = strategyExpanded,
                    onDismissRequest = { strategyExpanded = false },
                ) {
                    PairSelectionStrategy.entries.forEach { strategy ->
                        DropdownMenuItem(
                            text = { Text(strategy.name) },
                            onClick = {
                                viewModel.updateDraft { s -> s.copy(pairSelectionStrategy = strategy) }
                                strategyExpanded = false
                            },
                        )
                    }
                }
            }
            SwitchRow("Allow draws", draft.allowDraws) {
                viewModel.updateDraft { s -> s.copy(allowDraws = it) }
            }
            SwitchRow("Allow skipping", draft.allowSkipping) {
                viewModel.updateDraft { s -> s.copy(allowSkipping = it) }
            }
            SwitchRow("Show ratings during comparisons", draft.showRatingsDuringComparison) {
                viewModel.updateDraft { s -> s.copy(showRatingsDuringComparison = it) }
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) { Text("Save settings") }

            SectionLabel("Maintenance")
            OutlinedButton(
                onClick = viewModel::recalculate,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.recalculating == null,
            ) { Text("Recalculate all rankings") }
            state.recalculating?.let { progress ->
                Text(progress.message)
                LinearProgressIndicator(
                    progress = { progress.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
            OutlinedButton(onClick = { showReset = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Reset all settings")
            }
            OutlinedButton(onClick = { showDeleteAll = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete all application data", color = MaterialTheme.colorScheme.error)
            }

            SectionLabel("Import / export")
            HelpText("Exports lists, items, comparison history, and settings as JSON via the system file picker.")
            OutlinedButton(
                onClick = { exportLauncher.launch("pair-comp-elo-backup.json") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export JSON") }
            OutlinedButton(
                onClick = {
                    pendingImportMode = ImportMode.REPLACE
                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import JSON (replace all)") }
            OutlinedButton(
                onClick = {
                    pendingImportMode = ImportMode.MERGE
                    importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import JSON (merge)") }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            state.status?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showReset) {
        ConfirmDialog(
            title = "Reset settings?",
            message = "Restore default Elo, decay, and comparison options.",
            confirmLabel = "Reset",
            onConfirm = {
                showReset = false
                viewModel.resetSettings()
            },
            onDismiss = { showReset = false },
        )
    }
    if (showDeleteAll) {
        ConfirmDialog(
            title = "Delete all data?",
            message = "This permanently removes every list, item, and comparison on this device.",
            confirmLabel = "Delete everything",
            onConfirm = {
                showDeleteAll = false
                viewModel.deleteAllData()
            },
            onDismiss = { showDeleteAll = false },
            destructive = true,
        )
    }
}

@Composable
private fun HelpText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun NumberField(label: String, value: Double, onChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        singleLine = true,
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
    ) {
        Text(title, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/** Helper so Compose can launch a coroutine without exposing viewModelScope in the composable API. */
private fun SettingsViewModel.viewModelScopeLaunch(block: suspend () -> Unit) {
    viewModelScope.launch { block() }
}
