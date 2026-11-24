package com.tepmex.pacholli.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tepmex.pacholli.domain.Book
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.ui.theme.OutRed
import com.tepmex.pacholli.ui.theme.Paper
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val book by vm.book.collectAsState()
    var query by remember { mutableStateOf("") }
    val found = remember(book, query) { Ledger.search(book, query) }
    val code = book.operatingOrNull()?.code ?: ""
    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Поиск") },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                label = { Text("Счёт, описание, сумма") },
                singleLine = true,
            )
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (found.isEmpty()) {
                    Text("Ничего не найдено", modifier = Modifier.padding(16.dp), color = Color(0xFF757575))
                }
                found.take(200).forEach { entry ->
                    val debit = book.account(entry.debit.accountId)?.name ?: "?"
                    val credit = book.account(entry.credit.accountId)?.name ?: "?"
                    TextButton(
                        onClick = {
                            vm.beginEntry(entry)
                            nav.navigate(Routes.Entry)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${entry.date}  $debit ← $credit  ${com.tepmex.pacholli.domain.Money.display(entry.debit.baseAmount)} $code",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val book by vm.book.collectAsState()
    var title by remember(book.title) { mutableStateOf(book.title) }
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Настройки") },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название книги") },
                singleLine = true,
            )
            Button(onClick = { vm.setTitle(title) }, modifier = Modifier.padding(top = 12.dp)) {
                Text("Сохранить название")
            }
            Text(
                "Валюта учёта: ${book.operatingOrNull()?.code ?: "—"} ${book.operatingOrNull()?.name ?: ""}",
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                "Каждая проводка — дебет и кредит на одну сумму в валюте учёта. Если у счёта есть субсчета, в проводке используется только субсчёт.",
                modifier = Modifier.padding(top = 12.dp),
                color = Color(0xFF616161),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IoScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<Book?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { vm.closed.collect { pending = null } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) writeText(context, uri, vm.exportText())
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
        if (text == null) {
            errors = listOf("Файл не прочитан")
            return@rememberLauncherForActivityResult
        }
        val parsed = vm.importBook(text)
        if (!parsed.ok || parsed.value == null) {
            errors = parsed.errors
            pending = null
        } else {
            errors = emptyList()
            pending = parsed.value
        }
    }
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Импорт / Экспорт") },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Файл .beancount. В проводке два счёта. Директивы balance, pad и include не принимаются.")
            Button(
                onClick = { export.launch("pacholli.beancount") },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Экспорт") }
            Button(
                onClick = { import.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Импорт") }
            errors.forEach { line ->
                Text(line, color = OutRed, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    val incoming = pending
    if (incoming != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Заменить книгу?") },
            text = {
                Text("«${incoming.title}»: счетов ${incoming.accounts.size}, проводок ${incoming.entries.size}. Текущие данные будут заменены.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val book = incoming
                    pending = null
                    vm.replaceBook(book)
                }) { Text("Заменить") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Отмена") } },
        )
    }
}

private fun writeText(context: android.content.Context, uri: Uri, text: String) {
    context.contentResolver.openOutputStream(uri)?.use { stream ->
        stream.write(text.toByteArray(StandardCharsets.UTF_8))
    }
}
