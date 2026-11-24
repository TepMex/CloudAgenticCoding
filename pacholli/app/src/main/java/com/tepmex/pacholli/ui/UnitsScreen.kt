package com.tepmex.pacholli.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.domain.Money
import com.tepmex.pacholli.ui.theme.OutRed
import com.tepmex.pacholli.ui.theme.Paper
import com.tepmex.pacholli.ui.theme.Teal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitsScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val book by vm.book.collectAsState()
    val today = remember { LocalDate.now() }
    val code = book.operatingOrNull()?.code ?: ""
    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Единицы") },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.beginUnit(null)
                    nav.navigate(Routes.Unit)
                },
                containerColor = Teal,
                contentColor = Color.White,
            ) { Icon(Icons.Filled.Add, "Новая единица") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Учёт ведётся в $code. У остальных единиц задаётся стоимость в $code.",
                modifier = Modifier.padding(16.dp),
                color = Color(0xFF616161),
            )
            book.commodities.sortedWith(compareByDescending<com.tepmex.pacholli.domain.Commodity> { it.operating }.thenBy { it.code })
                .forEach { commodity ->
                    val price = Ledger.quote(book, commodity.id, today)
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            vm.beginUnit(commodity.id)
                            nav.navigate(Routes.Unit)
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(commodity.code, fontWeight = FontWeight.Bold)
                            Text(commodity.name, color = Color(0xFF616161))
                        }
                        Text(
                            if (commodity.operating) "валюта учёта" else price?.let { "${Money.display(it)} $code" } ?: "нет цены",
                            color = if (commodity.operating || price != null) Teal else OutRed,
                        )
                    }
                    HorizontalDivider()
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitEditorScreen(vm: PacholliViewModel, nav: NavHostController) {
    val form by vm.unitForm.collectAsState()
    val book by vm.book.collectAsState()
    LaunchedEffect(Unit) { vm.closed.collect { nav.popBackStack() } }
    val current = form ?: return
    var dateOpen by remember { mutableStateOf(false) }
    val code = book.operatingOrNull()?.code ?: ""
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text(if (current.isNew) "Новая единица" else current.code) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = { TextButton(onClick = vm::saveUnit) { Text("Сохранить", color = Color.White) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(
                value = current.code,
                onValueChange = { value -> vm.updateUnit { it.copy(code = value.uppercase()) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Код") },
                singleLine = true,
                enabled = !current.operating,
            )
            OutlinedTextField(
                value = current.name,
                onValueChange = { value -> vm.updateUnit { it.copy(name = value) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Название") },
                singleLine = true,
            )
            if (!current.operating) {
                OutlinedTextField(
                    value = current.priceText,
                    onValueChange = { value -> vm.updateUnit { it.copy(priceText = value) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("Стоимость в $code") },
                    singleLine = true,
                )
                TextButton(onClick = { dateOpen = true }) { Text("Дата цены ${current.priceDate}") }
                val history = book.quotes.filter { it.commodityId == current.id }.sortedByDescending { it.date }
                history.forEach { quote ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${quote.date}   ${Money.display(quote.price)} $code", modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.deleteQuote(current.id, quote.date) }) { Text("Удалить") }
                    }
                }
            } else {
                Text("Это валюта учёта. Её стоимость всегда 1.", modifier = Modifier.padding(top = 12.dp))
            }
            current.error?.let { Text(it, color = OutRed, modifier = Modifier.padding(top = 8.dp)) }
            if (!current.isNew && !current.operating) {
                TextButton(onClick = vm::deleteUnit, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Удалить единицу", color = OutRed)
                }
            }
        }
    }
    if (dateOpen) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.priceDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.updateUnit { it.copy(priceDate = date) }
                    }
                    dateOpen = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Отмена") } },
        ) { DatePicker(state) }
    }
}
