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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LibraryScreen(state: TutorUiState, onOpen: (String) -> Unit, onImport: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("reading") }
    val reading = state.shelf.count { !it.done }
    val done = state.shelf.count { it.done }
    val books = state.shelf.filter { book ->
        when (filter) {
            "done" -> book.done
            "all" -> true
            else -> !book.done
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Мои книги", modifier = Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TutorText)
            BoxPlus(onImport)
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Segmented(
                options = listOf(
                    "reading" to "Читаю $reading",
                    "done" to "Завершено $done",
                    "all" to "Все ${state.shelf.size}",
                ),
                value = filter,
                onChange = { filter = it },
            )
            Spacer(Modifier.height(12.dp))
            if (books.isEmpty()) {
                Text(
                    if (state.shelf.isEmpty()) "Полка пустая. Добавьте DRM-free EPUB кнопкой +." else "В этой полке пока пусто.",
                    color = TutorMuted,
                    fontSize = 14.sp,
                )
            }
            books.forEach { book ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                        .background(TutorCard)
                        .clickable { onOpen(book.id) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BookCover(book.title, book.author, book.coverSeed, large = false)
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                book.title,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = TutorText,
                            )
                            Text("${book.percent}%", color = TutorBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(book.author, color = TutorMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        ProgressBar(book.percent)
                        Spacer(Modifier.height(6.dp))
                        Text("Последнее занятие", color = TutorSecondary, fontSize = 13.sp)
                        Text(book.lastSession, color = TutorText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BoxPlus(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(36.dp).clip(CircleShape).background(TutorBlue).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
