package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.extensivereadingtutor.llm.JsonTone
import com.tepmex.extensivereadingtutor.llm.highlightJson

@Composable
fun AiJournalScreen(state: TutorUiState, model: TutorViewModel) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Журнал запросов", onBack = model::closeJournal)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(
                "Каждая попытка вызова модели: проверка соединения, подготовка и разбор. Ключ API в журнал не записывается.",
                color = TutorSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(12.dp))
            if (state.exchanges.isEmpty()) {
                SurfaceCard {
                    Text("Запросов ещё не было.", fontWeight = FontWeight.SemiBold, color = TutorText)
                    Text(
                        "Они появятся после проверки соединения или занятия.",
                        color = TutorMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                state.exchanges.forEach { row ->
                    SurfaceCard(Modifier.padding(bottom = 8.dp), onClick = { model.openExchange(row.id) }) {
                        Text(row.title, fontWeight = FontWeight.SemiBold, color = TutorText)
                        Text(row.model, color = TutorSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        Text(row.whenLabel, color = TutorMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        Text(
                            row.meta,
                            color = if (row.ok) TutorGreen else TutorRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                        Text(row.tokens, color = TutorSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
                Text(
                    if (confirmClear) "Удалить все записи" else "Очистить журнал",
                    color = TutorRed,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 12.dp).clickable {
                        if (confirmClear) {
                            confirmClear = false
                            model.clearJournal()
                        } else {
                            confirmClear = true
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun AiExchangeScreen(state: TutorUiState, model: TutorViewModel) {
    val exchange = state.exchange
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = exchange?.title ?: "Запрос", onBack = model::closeExchange)
        if (exchange == null) {
            Text("Этой записи нет в журнале.", color = TutorMuted, modifier = Modifier.padding(16.dp))
            return
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(exchange.model, fontWeight = FontWeight.SemiBold, color = TutorText, fontSize = 18.sp)
            Text(exchange.url, color = TutorMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(8.dp))
            Text("${exchange.whenLabel} · ${exchange.duration}", color = TutorSecondary, fontSize = 14.sp)
            Text(
                exchange.status,
                color = if (exchange.ok) TutorGreen else TutorRed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(exchange.tokens, color = TutorText, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(14.dp))
            RawCard("Запрос", exchange.requestPreview) { model.openRaw(RawSide.REQUEST) }
            Spacer(Modifier.height(8.dp))
            RawCard("Ответ", exchange.responsePreview) { model.openRaw(RawSide.RESPONSE) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun AiRawScreen(state: TutorUiState, model: TutorViewModel) {
    val side = (state.route as? Route.AiRaw)?.side ?: RawSide.REQUEST
    val exchange = state.exchange
    val title = if (side == RawSide.REQUEST) "Запрос" else "Ответ"
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = title, onBack = model::closeRaw)
        if (exchange == null) {
            Text("Этой записи нет в журнале.", color = TutorMuted, modifier = Modifier.padding(16.dp))
            return
        }
        val headers = if (side == RawSide.REQUEST) exchange.requestHeaders else exchange.responseHeaders
        val headersHighlighted = if (side == RawSide.REQUEST) exchange.requestHeadersHighlighted else exchange.responseHeadersHighlighted
        val body = if (side == RawSide.REQUEST) exchange.requestBody else exchange.responseBody
        val bodyHighlighted = if (side == RawSide.REQUEST) exchange.requestBodyHighlighted else exchange.responseBodyHighlighted
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (side == RawSide.REQUEST) {
                Text("POST", color = TutorMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(exchange.url, color = TutorSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                Text(
                    "Значение Authorization заменено на [redacted]. Ключ в журнал не пишется.",
                    color = TutorMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                Text(exchange.status, color = if (exchange.ok) TutorGreen else TutorRed, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            SectionLabel("Заголовки")
            JsonBlock(headers, headersHighlighted)
            SectionLabel("Тело")
            if (!bodyHighlighted && body != "—") {
                Text("Тело не JSON и показано как текст.", color = TutorMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
            }
            JsonBlock(body, bodyHighlighted)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RawCard(title: String, preview: String, onClick: () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Text(title, fontWeight = FontWeight.SemiBold, color = TutorText)
        Text(
            preview,
            color = TutorSecondary,
            fontSize = 13.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text("Сырой вид", color = TutorBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun JsonBlock(text: String, highlighted: Boolean) {
    val annotated = if (highlighted) {
        buildAnnotatedString {
            highlightJson(text).forEach { span ->
                withStyle(SpanStyle(color = toneColor(span.tone))) {
                    append(span.text)
                }
            }
        }
    } else {
        buildAnnotatedString {
            withStyle(SpanStyle(color = TutorText)) { append(text) }
        }
    }
    SelectionContainer {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF6F8FB))
                .border(1.dp, TutorLine, RoundedCornerShape(12.dp))
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                annotated,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                softWrap = false,
            )
        }
    }
}

private fun toneColor(tone: JsonTone): Color = when (tone) {
    JsonTone.Plain -> Color(0xFF1C1C1E)
    JsonTone.Key -> Color(0xFF0451A5)
    JsonTone.StringValue -> Color(0xFFA31515)
    JsonTone.Number -> Color(0xFF098658)
    JsonTone.Keyword -> Color(0xFF7A3E9D)
    JsonTone.Punctuation -> Color(0xFF5C6370)
}
