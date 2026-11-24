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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tepmex.pacholli.domain.AccountSection
import com.tepmex.pacholli.domain.AccountType
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.domain.Money
import com.tepmex.pacholli.ui.theme.AccountGreen
import com.tepmex.pacholli.ui.theme.OutRed
import com.tepmex.pacholli.ui.theme.Paper
import com.tepmex.pacholli.ui.theme.Teal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val book by vm.book.collectAsState()
    val today = remember { LocalDate.now() }
    val shot = remember(book) { Ledger.snapshot(book, today) }
    val code = book.operatingOrNull()?.code ?: ""
    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Счета") },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.beginAccount(null, null)
                    nav.navigate(Routes.Account)
                },
                containerColor = Teal,
                contentColor = Color.White,
            ) { Icon(Icons.Filled.Add, "Новый счёт") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            AccountSection.entries.forEach { section ->
                val rows = book.accounts.filter { it.section == section }.sortedBy { it.beancountName }
                if (rows.isEmpty()) return@forEach
                Text(
                    section.title,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF616161),
                )
                rows.forEach { account ->
                    val depth = (account.beancountName.count { it == ':' } - 1).coerceAtLeast(0)
                    val value = if (account.section.balanceSheet) Ledger.accountValue(book, shot, account, today) else null
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            vm.beginAccount(account.id, null)
                            nav.navigate(Routes.Account)
                        }.padding(start = (16 + depth * 16).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(account.name, modifier = Modifier.weight(1f), fontSize = 17.sp)
                            if (value != null && value.signum() != 0) {
                                Text(Money.display(value) + " " + code, fontWeight = FontWeight.Medium)
                            }
                        }
                        val flags = buildString {
                            append(account.shortType())
                            append(if (account.allowsSubaccounts) " · можно субсчета" else " · без субсчетов")
                            if (account.cash) append(" · денежный")
                        }
                        Text(flags, color = Color(0xFF757575), fontSize = 12.sp)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorScreen(vm: PacholliViewModel, nav: NavHostController) {
    val form by vm.accountForm.collectAsState()
    val book by vm.book.collectAsState()
    LaunchedEffect(Unit) { vm.closed.collect { nav.popBackStack() } }
    val current = form ?: return
    var sectionOpen by remember { mutableStateOf(false) }
    var typeOpen by remember { mutableStateOf(false) }
    var parentOpen by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text(if (current.isNew) "Новый счёт" else current.name.ifBlank { "Счёт" }) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = { TextButton(onClick = vm::saveAccount) { Text("Сохранить", color = Color.White) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { name ->
                    vm.updateAccount { form ->
                        val leaf = if (form.isNew && !form.leafTouched) com.tepmex.pacholli.domain.Names.leafFromDisplay(name) else form.leaf
                        form.copy(name = name, leaf = leaf)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название") },
                singleLine = true,
            )
            OutlinedTextField(
                value = current.leaf,
                onValueChange = { vm.updateAccount { form -> form.copy(leaf = it, leafTouched = true) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Имя Beancount") },
                singleLine = true,
            )
            val parents = book.accounts.filter { account ->
                account.id != current.id &&
                    account.section == current.section &&
                    account.allowsSubaccounts &&
                    account.parentId != current.id
            }
            ExposedDropdownMenuBox(expanded = parentOpen, onExpandedChange = { parentOpen = it }) {
                OutlinedTextField(
                    value = current.parentId?.let { book.account(it)?.name } ?: "Без родителя",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Родитель") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(parentOpen) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().padding(top = 8.dp),
                )
                ExposedDropdownMenu(expanded = parentOpen, onDismissRequest = { parentOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Без родителя") },
                        onClick = {
                            vm.updateAccount { it.copy(parentId = null) }
                            parentOpen = false
                        },
                    )
                    parents.forEach { parent ->
                        DropdownMenuItem(
                            text = { Text(parent.name) },
                            onClick = {
                                vm.updateAccount { it.copy(parentId = parent.id, section = parent.section) }
                                parentOpen = false
                            },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = sectionOpen, onExpandedChange = { if (current.parentId == null) sectionOpen = it }) {
                OutlinedTextField(
                    value = current.section.title,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Раздел") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sectionOpen) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().padding(top = 8.dp),
                )
                ExposedDropdownMenu(expanded = sectionOpen, onDismissRequest = { sectionOpen = false }) {
                    AccountSection.entries.forEach { section ->
                        DropdownMenuItem(
                            text = { Text(section.title) },
                            onClick = {
                                vm.updateAccount { it.copy(section = section, type = section.defaultType, parentId = null) }
                                sectionOpen = false
                            },
                        )
                    }
                }
            }
            ExposedDropdownMenuBox(expanded = typeOpen, onExpandedChange = { typeOpen = it }) {
                OutlinedTextField(
                    value = when (current.type) {
                        AccountType.ACTIVE -> "Активный"
                        AccountType.PASSIVE -> "Пассивный"
                        AccountType.ACTIVE_PASSIVE -> "Активно-пассивный"
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Тип") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeOpen) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().padding(top = 8.dp),
                )
                ExposedDropdownMenu(expanded = typeOpen, onDismissRequest = { typeOpen = false }) {
                    listOf(
                        AccountType.ACTIVE to "Активный",
                        AccountType.PASSIVE to "Пассивный",
                        AccountType.ACTIVE_PASSIVE to "Активно-пассивный",
                    ).forEach { (type, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                vm.updateAccount { it.copy(type = type) }
                                typeOpen = false
                            },
                        )
                    }
                }
            }
            SwitchRow("Можно заводить субсчета", current.allowsSubaccounts) {
                vm.updateAccount { form -> form.copy(allowsSubaccounts = it) }
            }
            SwitchRow("Денежный счёт", current.cash) { vm.updateAccount { form -> form.copy(cash = it) } }
            Text(
                "Денежный счёт попадает в движение денег: кошелёк, дебетовая карта, вклад до востребования.",
                color = Color(0xFF757575),
                fontSize = 13.sp,
            )
            Text("Открыт ${current.openedOn}", modifier = Modifier.padding(top = 12.dp), color = AccountGreen)
            SwitchRow("Счёт закрыт", current.closedOn != null) { closed ->
                vm.updateAccount { it.copy(closedOn = if (closed) LocalDate.now() else null) }
            }
            current.error?.let { Text(it, color = OutRed, modifier = Modifier.padding(top = 8.dp)) }
            if (!current.isNew) {
                TextButton(onClick = vm::deleteAccount, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Удалить счёт", color = OutRed)
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
