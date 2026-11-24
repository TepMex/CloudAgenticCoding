package com.tepmex.pacholli.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tepmex.pacholli.domain.AccountSection
import com.tepmex.pacholli.domain.Book
import com.tepmex.pacholli.domain.Dates
import com.tepmex.pacholli.domain.Money
import com.tepmex.pacholli.domain.Pad
import com.tepmex.pacholli.domain.Side
import com.tepmex.pacholli.ui.theme.AccountGreen
import com.tepmex.pacholli.ui.theme.CyanLine
import com.tepmex.pacholli.ui.theme.Ink
import com.tepmex.pacholli.ui.theme.OutRed
import com.tepmex.pacholli.ui.theme.Paper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class Pick { DebitAccount, CreditAccount, DebitUnit, CreditUnit }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(vm: PacholliViewModel, nav: NavHostController) {
    val draft by vm.draft.collectAsState()
    val book by vm.book.collectAsState()
    var pick by remember { mutableStateOf<Pick?>(null) }
    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.closed.collect { nav.popBackStack() } }
    val current = draft
    if (current == null) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val choosing = pick
    if (choosing != null) {
        BackHandler { pick = null }
        PickerScreen(book, current, choosing, onBack = { pick = null }) { id ->
            when (choosing) {
                Pick.DebitAccount -> vm.setAccount(Side.DEBIT, id)
                Pick.CreditAccount -> vm.setAccount(Side.CREDIT, id)
                Pick.DebitUnit -> vm.setCommodity(Side.DEBIT, id)
                Pick.CreditUnit -> vm.setCommodity(Side.CREDIT, id)
            }
            pick = null
        }
        return
    }
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text(if (current.isNew) "Проводка" else "Проводка") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = {
                    TextButton(onClick = vm::toggleText) {
                        Text(if (current.textMode) "Форма" else "Текст", color = Color.White)
                    }
                    if (!current.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Удалить", tint = Color.White)
                        }
                    }
                    IconButton(onClick = vm::saveEntry, enabled = current.error == null || !current.textMode) {
                        Icon(Icons.Filled.Check, "Сохранить", tint = Color.White)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (current.textMode) {
                BasicTextField(
                    value = current.text,
                    onValueChange = vm::setText,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Ink,
                    ),
                    cursorBrush = SolidColor(Ink),
                )
            } else {
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                ) {
                    AmountHeader(book, current) {
                        pick = if (current.focus == Side.DEBIT) Pick.DebitUnit else Pick.CreditUnit
                    }
                    DateTimeRow(current, { vm.shiftDate(-1) }, { vm.shiftDate(1) }, { dateOpen = true }, { timeOpen = true })
                    AccountRow(
                        label = "Дебет",
                        name = current.debitAccountId?.let { book.account(it)?.name },
                        focused = current.focus == Side.DEBIT,
                        onFocus = { vm.focus(Side.DEBIT) },
                        onPick = { pick = Pick.DebitAccount },
                    )
                    AccountRow(
                        label = "Кредит",
                        name = current.creditAccountId?.let { book.account(it)?.name },
                        focused = current.focus == Side.CREDIT,
                        onFocus = { vm.focus(Side.CREDIT) },
                        onPick = { pick = Pick.CreditAccount },
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = vm::toggleLink) {
                            Icon(
                                if (current.linked) Icons.Filled.Link else Icons.Filled.LinkOff,
                                "Связать суммы",
                                tint = if (current.linked) AccountGreen else Color.Gray,
                            )
                        }
                        Text(
                            if (current.linked) "Кредит следует за дебетом" else "Суммы задаются отдельно",
                            color = Color(0xFF616161),
                            fontSize = 13.sp,
                        )
                    }
                    PriceFields(book, current, vm::setPriceText)
                    OutlinedTextField(
                        value = current.payee,
                        onValueChange = vm::setPayee,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        label = { Text("Контрагент") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = current.narration,
                        onValueChange = vm::setNarration,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        label = { Text("Описание") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            val error = current.error
            if (!error.isNullOrBlank()) {
                Text(error, color = OutRed, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
            if (!current.textMode) AmountKeypad(vm::pad, vm::confirmAmount)
        }
    }
    if (dateOpen) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        vm.setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    dateOpen = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Отмена") } },
        ) { DatePicker(state) }
    }
    if (timeOpen) {
        val initial = current.minutes ?: 0
        val state = rememberTimePickerState(initial / 60, initial % 60, true)
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setMinutes(state.hour * 60 + state.minute)
                    timeOpen = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text("Отмена") } },
            text = { TimePicker(state) },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteEntry() }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
            text = { Text("Удалить проводку?") },
        )
    }
}

@Composable
private fun AmountHeader(book: Book, draft: Draft, onCommodity: () -> Unit) {
    val commodityId = if (draft.focus == Side.DEBIT) draft.debitCommodityId else draft.creditCommodityId
    val commodity = book.commodity(commodityId)
    val operating = book.operatingOrNull()
    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                commodity?.code ?: "?",
                modifier = Modifier.clickable(onClick = onCommodity).padding(end = 12.dp),
                color = AccountGreen,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                Pad.display(draft.expression),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
                fontSize = 36.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Ink,
            )
        }
        Spacer(Modifier.height(6.dp).fillMaxWidth().background(CyanLine))
        if (commodity != null && operating != null && !commodity.operating) {
            val price = draft.prices[commodity.id]
            val qty = if (draft.focus == Side.DEBIT) draft.debitQty else draft.creditQty
            if (price != null) {
                Text(
                    "= ${Money.display(qty.multiply(price))} ${operating.code}",
                    modifier = Modifier.padding(top = 6.dp).align(Alignment.End),
                    color = Color(0xFF757575),
                )
            }
        }
    }
}

@Composable
private fun DateTimeRow(
    draft: Draft,
    previous: () -> Unit,
    next: () -> Unit,
    pickDate: () -> Unit,
    pickTime: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(Paper).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = previous) { Icon(Icons.Filled.ChevronLeft, "Предыдущий день") }
        Column(Modifier.weight(1f).clickable(onClick = pickDate), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Dates.label(draft.date), fontWeight = FontWeight.Medium)
            Text(Dates.timeLabel(draft.minutes), color = Color(0xFF616161))
        }
        IconButton(onClick = pickTime) { Icon(Icons.Filled.CalendarMonth, "Дата и время") }
        IconButton(onClick = next) { Icon(Icons.Filled.ChevronRight, "Следующий день") }
    }
}

@Composable
private fun AccountRow(
    label: String,
    name: String?,
    focused: Boolean,
    onFocus: () -> Unit,
    onPick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f).clickable(onClick = onFocus), color = Ink, fontSize = 16.sp)
            Text(
                name ?: "Выбрать",
                modifier = Modifier.clickable(onClick = onPick),
                color = if (name == null) Color(0xFF9E9E9E) else AccountGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Icon(
                Icons.Filled.AccountBalanceWallet,
                contentDescription = null,
                tint = AccountGreen,
                modifier = Modifier.padding(start = 8.dp).clickable(onClick = onPick),
            )
        }
        if (focused) {
            Spacer(Modifier.height(6.dp).fillMaxWidth().background(CyanLine))
        }
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
private fun PriceFields(book: Book, draft: Draft, onPrice: (String, String) -> Unit) {
    val ids = listOf(draft.debitCommodityId, draft.creditCommodityId).distinct()
    for (id in ids) {
        val commodity = book.commodity(id) ?: continue
        if (commodity.operating) continue
        OutlinedTextField(
            value = draft.priceText[id] ?: draft.prices[id]?.let { Money.render(it) } ?: "",
            onValueChange = { onPrice(id, it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            label = { Text("Цена 1 ${commodity.code}") },
            singleLine = true,
            suffix = { Text(book.operatingOrNull()?.code ?: "") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScreen(
    book: Book,
    draft: Draft,
    pick: Pick,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
) {
    val units = pick == Pick.DebitUnit || pick == Pick.CreditUnit
    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = {
                    Text(
                        when (pick) {
                            Pick.DebitAccount -> "Дебет"
                            Pick.CreditAccount -> "Кредит"
                            Pick.DebitUnit, Pick.CreditUnit -> "Единица"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (units) {
                book.commodities.sortedBy { it.code }.forEach { commodity ->
                    Text(
                        "${commodity.code}  ${commodity.name}",
                        modifier = Modifier.fillMaxWidth().clickable { onPick(commodity.id) }.padding(16.dp),
                        fontSize = 18.sp,
                    )
                    HorizontalDivider()
                }
            } else {
                AccountSection.entries.forEach { section ->
                    val accounts = book.accounts.filter { account ->
                        account.section == section &&
                            !book.hasChildren(account.id) &&
                            !draft.date.isBefore(account.openedOn) &&
                            (account.closedOn == null || draft.date.isBefore(account.closedOn))
                    }.sortedBy { it.beancountName }
                    if (accounts.isEmpty()) return@forEach
                    Text(
                        section.title,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color(0xFF757575),
                        fontWeight = FontWeight.Bold,
                    )
                    accounts.forEach { account ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onPick(account.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text(account.name, color = AccountGreen, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Text(book.path(account), color = Color(0xFF757575), fontSize = 12.sp)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
