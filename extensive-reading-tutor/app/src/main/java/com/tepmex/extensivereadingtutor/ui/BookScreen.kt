package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BookScreen(
    book: ShelfBook,
    confirmRemove: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onAskRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = book.title, onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.title, book.author, book.coverSeed, large = true)
                Column(Modifier.weight(1f)) {
                    Text(book.title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TutorText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(book.author, color = TutorMuted, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${book.percent}%", color = TutorBlue, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    ProgressBar(book.percent)
                    Text(book.place, color = TutorSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (book.done) "Книга прочитана" else "Продолжить",
                enabled = !book.done,
                onClick = onContinue,
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat(book.chapters.size.toString(), "глав")
                Stat(book.completedLessons.toString(), "занятий")
                Stat(book.charsRead.toString(), "знаков")
            }
            SectionLabel("Карта чтения")
            if (book.chapters.isEmpty()) {
                Text("В книге нет глав.", color = TutorMuted, fontSize = 14.sp)
            }
            book.chapters.forEach { chapter ->
                val (color, caption) = when (chapter.mark) {
                    ChapterMark.DONE -> TutorGreen to "Пройдено"
                    ChapterMark.CURRENT -> TutorBlue to "Сейчас"
                    ChapterMark.AHEAD -> TutorTrack to "Дальше"
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .background(TutorCard)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Column(Modifier.weight(1f)) {
                        Text(chapter.title, fontWeight = FontWeight.SemiBold, color = TutorText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(caption, color = TutorMuted, fontSize = 12.sp)
                    }
                }
            }
            SectionLabel("Недавние занятия")
            if (book.sessions.isEmpty()) {
                Text("Занятий ещё не было.", color = TutorMuted, fontSize = 14.sp)
            }
            book.sessions.forEach { session ->
                Text(
                    "${formatDay(session.at)} · ${session.chapter}",
                    fontWeight = FontWeight.SemiBold,
                    color = TutorText,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(statusLabel(session.status), color = TutorSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(20.dp))
            if (!confirmRemove) {
                Text(
                    "Убрать с полки",
                    color = TutorRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onAskRemove).padding(vertical = 8.dp),
                )
                Text("Файл книги удалится. Занятия и наблюдения останутся.", color = TutorMuted, fontSize = 12.sp)
            } else {
                Text("Убрать «${book.title}» с полки?", color = TutorText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Убрать", onClick = onRemove)
                Spacer(Modifier.height(8.dp))
                Text("Отмена", color = TutorBlue, modifier = Modifier.clickable(onClick = onCancelRemove).padding(vertical = 8.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TutorText)
        Text(label, color = TutorMuted, fontSize = 12.sp)
    }
}
