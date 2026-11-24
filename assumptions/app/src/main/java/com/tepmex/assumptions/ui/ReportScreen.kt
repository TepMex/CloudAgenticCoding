package com.tepmex.assumptions.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tepmex.assumptions.domain.AssumptionReport
import com.tepmex.assumptions.domain.Expectation
import com.tepmex.assumptions.domain.Quadrant
import com.tepmex.assumptions.domain.formatScore
import com.tepmex.assumptions.domain.labelArgb
import com.tepmex.assumptions.domain.quadrantOf
import com.tepmex.assumptions.ui.theme.Ink
import com.tepmex.assumptions.ui.theme.InkSoft
import com.tepmex.assumptions.ui.theme.argb

@Composable
fun ReportScreen(
    expectations: List<Expectation>,
    report: AssumptionReport,
    selected: Quadrant?,
    onToggle: (Quadrant) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inCell = expectations.filter { quadrantOf(it.beforeScore, it.afterScore) == selected }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Отчёт", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Строки — ожидание до события, столбцы — ощущение после.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkSoft,
        )
        Matrix(
            report = report,
            selected = selected,
            onToggle = onToggle,
        )
        Text(
            text = "Ещё не произошли: ${report.pending}",
            style = MaterialTheme.typography.bodyLarge,
        )
        if (report.neutral > 0) {
            Text(
                text = "С нейтральной оценкой: ${report.neutral}",
                style = MaterialTheme.typography.bodyLarge,
                color = InkSoft,
            )
        }
        Legend()
        if (selected != null) {
            Text(text = selected.title, style = MaterialTheme.typography.titleLarge)
            if (inCell.isEmpty()) {
                Text(
                    text = "В этом квадранте пусто.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                )
            } else {
                inCell.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = item.eventText, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${formatScore(item.beforeScore)} → ${formatScore(item.afterScore ?: 0)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSoft,
                        )
                        Text(
                            text = item.expectedOutcome,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Matrix(
    report: AssumptionReport,
    selected: Quadrant?,
    onToggle: (Quadrant) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Spacer(Modifier.width(52.dp))
            HeaderCell("после −")
            Spacer(Modifier.width(8.dp))
            HeaderCell("после +")
        }
        MatrixRow(
            label = "до −",
            left = Quadrant.NegativeNegative,
            right = Quadrant.NegativePositive,
            report = report,
            selected = selected,
            onToggle = onToggle,
        )
        MatrixRow(
            label = "до +",
            left = Quadrant.PositiveNegative,
            right = Quadrant.PositivePositive,
            report = report,
            selected = selected,
            onToggle = onToggle,
        )
    }
}

@Composable
private fun MatrixRow(
    label: String,
    left: Quadrant,
    right: Quadrant,
    report: AssumptionReport,
    selected: Quadrant?,
    onToggle: (Quadrant) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(52.dp),
            style = MaterialTheme.typography.labelLarge,
            color = InkSoft,
        )
        QuadrantCell(
            quadrant = left,
            count = report.counts.getValue(left),
            picked = selected == left,
            onClick = { onToggle(left) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        QuadrantCell(
            quadrant = right,
            count = report.counts.getValue(right),
            picked = selected == right,
            onClick = { onToggle(right) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RowScope.HeaderCell(text: String) {
    Text(
        text = text,
        modifier = Modifier.weight(1f),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
        color = InkSoft,
    )
}

@Composable
private fun QuadrantCell(
    quadrant: Quadrant,
    count: Int,
    picked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = argb(quadrant.colorArgb)
    val label = argb(labelArgb(quadrant.colorArgb))
    Box(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(fill)
            .border(
                width = if (picked) 3.dp else 1.dp,
                color = if (picked) Ink else Color.Black.copy(alpha = 0.08f),
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${quadrant.title}: $count" }
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.displayLarge,
            color = label,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Legend() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Quadrant.entries.forEach { quadrant ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(argb(quadrant.colorArgb)),
                )
                Text(text = quadrant.title, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
