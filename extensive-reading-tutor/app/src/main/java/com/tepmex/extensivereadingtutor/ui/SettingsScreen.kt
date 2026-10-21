package com.tepmex.extensivereadingtutor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tepmex.extensivereadingtutor.domain.DifficultyPreference
import com.tepmex.extensivereadingtutor.domain.HintDepth
import com.tepmex.extensivereadingtutor.llm.chatCompletionsUrl

@Composable
fun SettingsScreen(state: TutorUiState, model: TutorViewModel) {
    var showKey by rememberSaveable { mutableStateOf(false) }
    val prefs = state.preferences
    val endpoint = state.baseUrl.trim().let { base ->
        if (base.isEmpty()) "" else chatCompletionsUrl(base)
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Настройки", onBack = { model.selectTab(Tab.PROFILE) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            SectionLabel("ИИ-модель")
            Text(
                "Любой OpenAI-compatible Chat Completions. Адрес можно указать как хост, с /v1 или целиком до /chat/completions. Ключ хранится в Android Keystore.",
                color = TutorSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(10.dp))
            SurfaceCard {
                Text(if (state.modelName.isBlank()) "OpenAI-compatible" else state.modelName, fontWeight = FontWeight.SemiBold, color = TutorText)
                Text(
                    if (endpoint.isBlank()) "Адрес не задан" else endpoint,
                    color = TutorMuted,
                    fontSize = 12.sp,
                )
                if (state.keyStored) {
                    Text("Ключ сохранён", color = TutorGreen, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Field("Базовый URL", state.baseUrl, "https://api.openai.com/v1") { model.editEndpoint(it, state.modelName, state.apiKey) }
            Field(
                "Ключ API",
                state.apiKey,
                if (state.keyStored) "Оставьте пустым, чтобы не менять" else "Нужен для запроса к модели",
                visual = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            ) { model.editEndpoint(state.baseUrl, state.modelName, it) }
            Text(
                if (showKey) "Скрыть ключ" else "Показать ключ",
                color = TutorBlue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp).clickable { showKey = !showKey },
            )
            Field("Модель", state.modelName, "gpt-4o") { model.editEndpoint(state.baseUrl, it, state.apiKey) }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Сохранить") { model.saveEndpoint() }
            Spacer(Modifier.height(8.dp))
            PrimaryButton("Проверить соединение") { model.probe() }
            state.probe?.let {
                Text(it, color = TutorSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            SectionLabel("Обучение")
            Text("Целевой уровень", color = TutorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = listOf("HSK 3" to "HSK 3", "HSK 4" to "HSK 4", "HSK 5" to "HSK 5", "HSK 6" to "HSK 6"),
                selected = prefs?.selfAssessedLevel ?: "HSK 4",
                onSelect = model::chooseLevel,
            )
            Spacer(Modifier.height(12.dp))
            Text("Язык объяснений", color = TutorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = listOf("ru" to "Русский", "en" to "English", "zh" to "中文"),
                selected = prefs?.explanationLanguage ?: "ru",
                onSelect = model::chooseLanguage,
            )
            Spacer(Modifier.height(12.dp))
            Text("Сложность", color = TutorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = listOf(
                    DifficultyPreference.EASY.name to "Лёгкая",
                    DifficultyPreference.MATCHED.name to "Средняя",
                    DifficultyPreference.STRETCH.name to "Высокая",
                ),
                selected = prefs?.preferredDifficulty?.name ?: DifficultyPreference.MATCHED.name,
                onSelect = { model.chooseDifficulty(DifficultyPreference.valueOf(it)) },
            )
            Spacer(Modifier.height(12.dp))
            Text("Глубина объяснений", color = TutorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = listOf(
                    HintDepth.MINIMAL.name to "Кратко",
                    HintDepth.MODERATE.name to "Обычно",
                    HintDepth.DETAILED.name to "Подробно",
                ),
                selected = prefs?.hintDepth?.name ?: HintDepth.MINIMAL.name,
                onSelect = { model.chooseDepth(HintDepth.valueOf(it)) },
            )
            Spacer(Modifier.height(12.dp))
            Text("Длина фрагмента", color = TutorMuted, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(
                options = listOf("120" to "Коротко", "180" to "Обычно", "280" to "Длиннее"),
                selected = (prefs?.targetFragmentCodePoints ?: 180).let { current ->
                    listOf(120, 180, 280).minBy { kotlin.math.abs(it - current) }.toString()
                },
                onSelect = { model.chooseLength(it.toInt()) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    hint: String,
    visual: VisualTransformation = VisualTransformation.None,
    onValue: (String) -> Unit,
) {
    Text(label, color = TutorMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    BasicTextField(
        value = value,
        onValueChange = onValue,
        visualTransformation = visual,
        textStyle = TextStyle(color = TutorText, fontSize = 16.sp),
        cursorBrush = SolidColor(TutorBlue),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(TutorCard, RoundedCornerShape(12.dp))
            .border(1.dp, TutorLine, RoundedCornerShape(12.dp))
            .padding(12.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, color = TutorMuted, fontSize = 14.sp)
                inner()
            }
        },
    )
}
