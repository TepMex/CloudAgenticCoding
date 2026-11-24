package com.tepmex.pacholli.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tepmex.pacholli.domain.AccountSection
import com.tepmex.pacholli.domain.Book
import com.tepmex.pacholli.domain.Dates
import com.tepmex.pacholli.domain.Entry
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.domain.Money
import com.tepmex.pacholli.domain.Span
import com.tepmex.pacholli.ui.theme.InGreen
import com.tepmex.pacholli.ui.theme.Ink
import com.tepmex.pacholli.ui.theme.KeyBlue
import com.tepmex.pacholli.ui.theme.OutRed
import com.tepmex.pacholli.ui.theme.Paper
import com.tepmex.pacholli.ui.theme.Teal
import java.math.BigDecimal
import java.time.LocalDate

private val months = listOf(
    "январь", "февраль", "март", "апрель", "май", "июнь",
    "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val book by vm.book.collectAsState()
    val anchor by vm.anchor.collectAsState()
    val span by vm.span.collectAsState()
    val ready by vm.ready.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var spanMenu by remember { mutableStateOf(false) }
    val entries = remember(book, anchor, span) { Ledger.journal(book, anchor, span) }
    val cash = remember(book, anchor, span) { cashDelta(book, anchor, span) }
    val code = book.operatingOrNull()?.code ?: ""
    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = {
                    Box {
                        Text(
                            span.title,
                            modifier = Modifier.clickable { spanMenu = true },
                            fontWeight = FontWeight.SemiBold,
                        )
                        DropdownMenu(expanded = spanMenu, onDismissRequest = { spanMenu = false }) {
                            Span.entries.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item.title) },
                                    onClick = {
                                        vm.setSpan(item)
                                        spanMenu = false
                                    },
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Сегодня") }, onClick = { menu = false; vm.today() })
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    vm.beginEntry(null)
                    nav.navigate(Routes.Entry)
                },
                containerColor = Teal,
                contentColor = Color.White,
            ) {
                Icon(Icons.Filled.EditNote, "Новая проводка")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateStrip(spanLabel(anchor, span), cash, code, span != Span.ALL, vm::shift)
            if (!ready) {
                Text("Загрузка…", modifier = Modifier.padding(24.dp), color = Ink)
            } else if (entries.isEmpty()) {
                Text(
                    "Нет проводок",
                    modifier = Modifier.padding(24.dp),
                    color = InGreen,
                    fontSize = 18.sp,
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(entries, key = { it.id }) { entry ->
                        EntryRow(book, entry, code) {
                            vm.beginEntry(entry)
                            nav.navigate(Routes.Entry)
                        }
                        HorizontalDivider(color = Color(0xFFE6E6E6))
                    }
                }
            }
        }
    }
}

@Composable
private fun DateStrip(label: String, cash: BigDecimal?, code: String, arrows: Boolean, shift: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { if (arrows) shift(-1) }, enabled = arrows) {
            Icon(Icons.Filled.ChevronLeft, "Назад")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Medium, color = Ink)
            val shown = cash ?: BigDecimal.ZERO
            val color = when (shown.signum()) {
                1 -> InGreen
                -1 -> OutRed
                else -> Ink
            }
            Text("Деньги ${Money.display(shown)} $code", color = color, fontWeight = FontWeight.SemiBold)
        }
        IconButton(onClick = { if (arrows) shift(1) }, enabled = arrows) {
            Icon(Icons.Filled.ChevronRight, "Вперёд")
        }
    }
}

@Composable
private fun EntryRow(book: Book, entry: Entry, code: String, onClick: () -> Unit) {
    val debit = book.account(entry.debit.accountId)
    val credit = book.account(entry.credit.accountId)
    val commodity = book.commodity(entry.debit.commodityId)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(Color.White).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.timeMinutes != null) {
                    Text(Dates.timeLabel(entry.timeMinutes), color = Color(0xFF757575), fontSize = 13.sp)
                }
                Text(
                    entry.payee.ifBlank { entry.narration }.ifBlank { "Проводка" },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${debit?.name ?: "?"} ← ${credit?.name ?: "?"}",
                color = Color(0xFF616161),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                Money.display(entry.debit.baseAmount) + " " + code,
                color = tone(book, entry),
                fontWeight = FontWeight.SemiBold,
            )
            if (commodity != null && !commodity.operating) {
                Text(
                    "${Money.displayQty(entry.debit.quantity)} ${commodity.code}",
                    color = Color(0xFF757575),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun tone(book: Book, entry: Entry): Color {
    val debit = book.account(entry.debit.accountId)
    val credit = book.account(entry.credit.accountId)
    return when {
        debit?.cash == true && credit?.cash == true -> KeyBlue
        debit?.cash == true -> InGreen
        credit?.cash == true -> OutRed
        debit?.section == AccountSection.EXPENSE -> OutRed
        credit?.section == AccountSection.INCOME -> InGreen
        else -> Ink
    }
}

private fun cashDelta(book: Book, anchor: LocalDate, span: Span): BigDecimal? {
    val window = Dates.range(anchor, span)
    val from: LocalDate
    val to: LocalDate
    if (window == null) {
        val dates = book.entries.map { it.date }
        if (dates.isEmpty()) return BigDecimal.ZERO
        from = dates.min()
        to = dates.max()
    } else {
        from = window.first
        to = window.second
    }
    val flow = Ledger.cashFlow(book, from, to)
    return flow.closing.subtract(flow.opening)
}

private fun spanLabel(anchor: LocalDate, span: Span): String = when (span) {
    Span.DAY -> Dates.label(anchor)
    Span.WEEK -> {
        val range = Dates.range(anchor, span)!!
        "${Dates.label(range.first)} — ${range.second.dayOfMonth}"
    }
    Span.MONTH -> "${months[anchor.monthValue - 1]} ${anchor.year}"
    Span.YEAR -> anchor.year.toString()
    Span.ALL -> "Все проводки"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun barColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = com.tepmex.pacholli.ui.theme.Ink,
    titleContentColor = Color.White,
    navigationIconContentColor = Color.White,
    actionIconContentColor = Color.White,
)
