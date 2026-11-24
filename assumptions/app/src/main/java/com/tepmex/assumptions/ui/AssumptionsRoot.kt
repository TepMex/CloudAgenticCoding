package com.tepmex.assumptions.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.tepmex.assumptions.domain.AssumptionReport

@Composable
fun AssumptionsRoot(viewModel: AssumptionsViewModel) {
    val section by viewModel.section.collectAsState()
    val wheel by viewModel.wheel.collectAsState()
    val emotions by viewModel.emotions.collectAsState()
    val expectations by viewModel.expectations.collectAsState()
    val draft by viewModel.draft.collectAsState()
    val quadrant by viewModel.quadrant.collectAsState()
    val report = remember(expectations) { AssumptionReport.from(expectations) }

    BackHandler(enabled = draft != null) { viewModel.cancelDraft() }
    BackHandler(enabled = draft == null && quadrant != null) { viewModel.clearQuadrant() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                SectionItem(Section.Emotions, "Эмоции", Icons.Outlined.DonutLarge, section, viewModel::open)
                SectionItem(Section.Expectations, "Ожидания", Icons.Outlined.HourglassEmpty, section, viewModel::open)
                SectionItem(Section.Report, "Отчёт", Icons.Outlined.GridView, section, viewModel::open)
            }
        },
    ) { padding ->
        when (section) {
            Section.Emotions -> EmotionsScreen(
                wheel = wheel,
                emotions = emotions,
                onHit = viewModel::onWheelHit,
                onRecord = viewModel::recordSelectedEmotion,
                onDelete = viewModel::deleteEmotion,
                modifier = Modifier.padding(padding),
            )
            Section.Expectations -> ExpectationsScreen(
                expectations = expectations,
                draft = draft,
                onBeginCreate = viewModel::beginCreate,
                onBeginResolve = viewModel::beginResolve,
                onEvent = viewModel::updateEvent,
                onOutcome = viewModel::updateOutcome,
                onScore = viewModel::updateScore,
                onNote = viewModel::updateNote,
                onSave = viewModel::saveDraft,
                onCancel = viewModel::cancelDraft,
                onDelete = viewModel::deleteExpectation,
                modifier = Modifier.padding(padding),
            )
            Section.Report -> ReportScreen(
                expectations = expectations,
                report = report,
                selected = quadrant,
                onToggle = viewModel::toggleQuadrant,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun RowScope.SectionItem(
    section: Section,
    label: String,
    icon: ImageVector,
    current: Section,
    onOpen: (Section) -> Unit,
) {
    NavigationBarItem(
        selected = current == section,
        onClick = { onOpen(section) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
}
