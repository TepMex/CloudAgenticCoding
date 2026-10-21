package com.tepmex.heilauncher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.heilauncher.R
import com.tepmex.heilauncher.domain.AverageCell
import com.tepmex.heilauncher.domain.AverageTrend
import com.tepmex.heilauncher.domain.FitnessAverages
import com.tepmex.heilauncher.domain.FitnessPeriodRow
import java.util.Locale

private val Ink = Color.White
private val Muted = Color(0xFF8A8A8A)

@Composable
fun FitnessAveragesPage(
    state: FitnessUiState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val averages = state.averages
        when {
            !state.signedIn -> StatusLine(stringResource(R.string.fitness_sign_in))
            averages != null -> AveragesTable(averages)
            state.failed -> StatusLine(stringResource(R.string.fitness_failed))
            else -> StatusLine(stringResource(R.string.fitness_loading))
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Text(
        text = text,
        color = Muted,
        fontSize = 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AveragesTable(averages: FitnessAverages) {
    val locale = locale()
    Column(Modifier.fillMaxWidth()) {
        PeriodRow(
            row = averages.steps,
            metric = stringResource(R.string.metric_steps),
            format = { FitnessAverages.formatSteps(it) },
        )
        Spacer(Modifier.height(22.dp))
        PeriodRow(
            row = averages.sleepHours,
            metric = stringResource(R.string.metric_sleep),
            format = { FitnessAverages.formatSleepHours(it, locale) },
        )
    }
}

@Composable
private fun PeriodRow(
    row: FitnessPeriodRow,
    metric: String,
    format: (Double) -> String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AverageReadout(row.year, metric, stringResource(R.string.period_year), format, Modifier.weight(1f))
        AverageReadout(row.month, metric, stringResource(R.string.period_month), format, Modifier.weight(1f))
        AverageReadout(row.week, metric, stringResource(R.string.period_week), format, Modifier.weight(1f))
    }
}

@Composable
private fun AverageReadout(
    cell: AverageCell,
    metric: String,
    period: String,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
) {
    val value = cell.value
    val text = value?.let(format) ?: "—"
    val description = when {
        value == null -> stringResource(R.string.fitness_empty, metric, period)
        cell.trend == AverageTrend.Up -> stringResource(R.string.fitness_up, metric, period, text)
        cell.trend == AverageTrend.Down -> stringResource(R.string.fitness_down, metric, period, text)
        else -> stringResource(R.string.fitness_flat, metric, period, text)
    }
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Ink,
            fontSize = 18.sp,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(9.dp), contentAlignment = Alignment.Center) {
            when (cell.trend) {
                AverageTrend.Up -> Pyramid(up = true, Modifier.size(9.dp))
                AverageTrend.Down -> Pyramid(up = false, Modifier.size(9.dp))
                AverageTrend.Flat -> Unit
            }
        }
    }
}

@Composable
private fun Pyramid(up: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawPyramid(up)
    }
}

private fun DrawScope.drawPyramid(up: Boolean) {
    val path = Path().apply {
        if (up) {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
        } else {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width / 2f, size.height)
        }
        close()
    }
    drawPath(path, Ink)
}

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]
