package com.tepmex.ctxcalendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tepmex.ctxcalendar.data.GalleryPhoto
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

data class CalendarDayCell(
    val date: LocalDate,
    val inCurrentMonth: Boolean,
)

fun buildMonthGrid(yearMonth: YearMonth, locale: Locale = Locale.getDefault()): List<CalendarDayCell> {
    val weekFields = WeekFields.of(locale)
    val firstDayOfWeek = weekFields.firstDayOfWeek
    val start = yearMonth.atDay(1).with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return (0 until 42).map { offset ->
        val date = start.plusDays(offset.toLong())
        CalendarDayCell(
            date = date,
            inCurrentMonth = date.month == yearMonth.month,
        )
    }
}

fun weekdayHeaders(locale: Locale = Locale.getDefault()): List<String> {
    val weekFields = WeekFields.of(locale)
    val first = weekFields.firstDayOfWeek
    return (0 until 7).map { index ->
        first.plus(index.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
    }
}

@Composable
fun CalendarMonthGrid(
    yearMonth: YearMonth,
    photosByDay: Map<LocalDate, List<GalleryPhoto>>,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val days = buildMonthGrid(yearMonth, locale)
    val headers = weekdayHeaders(locale)

    Column(modifier = modifier.fillMaxHeight()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            headers.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        days.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                week.forEach { cell ->
                    DayCell(
                        cell = cell,
                        photos = photosByDay[cell.date].orEmpty(),
                        isToday = cell.date == LocalDate.now(),
                        onClick = { onDayClick(cell.date) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarDayCell,
    photos: List<GalleryPhoto>,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isToday) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .border(0.5.dp, borderColor)
            .background(
                if (isToday) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .alpha(if (cell.inCurrentMonth) 1f else 0.45f)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Column(
            modifier = Modifier.matchParentSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = cell.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 10.sp,
                ),
                color = if (cell.inCurrentMonth) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (photos.isNotEmpty()) {
                val previews = photos.take(4)
                previews.chunked(2).forEach { rowPhotos ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        rowPhotos.forEach { photo ->
                            AsyncImage(
                                model = photo.uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(2.dp)),
                            )
                        }
                        if (rowPhotos.size == 1) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (photos.size > 4) {
                    Text(
                        text = "+${photos.size - 4}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}
