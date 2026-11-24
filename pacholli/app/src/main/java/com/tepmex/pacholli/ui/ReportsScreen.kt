package com.tepmex.pacholli.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.tepmex.pacholli.domain.Figure
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.domain.Money
import com.tepmex.pacholli.ui.theme.InGreen
import com.tepmex.pacholli.ui.theme.OutRed
import com.tepmex.pacholli.ui.theme.Paper
import com.tepmex.pacholli.ui.theme.Teal
import java.math.BigDecimal
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(vm: PacholliViewModel, nav: NavHostController, openDrawer: () -> Unit) {
    val book by vm.book.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var asOf by remember { mutableStateOf(LocalDate.now()) }
    var from by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var to by remember { mutableStateOf(LocalDate.now()) }
    val code = book.operatingOrNull()?.code ?: ""
    Scaffold(
        containerColor = Paper,
        topBar = {
            TopAppBar(
                colors = barColors(),
                title = { Text("Отчёты") },
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Filled.Menu, "Меню") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = Teal) {
                listOf("Баланс", "Движение денег", "Результат").forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                when (tab) {
                    0 -> {
                        PeriodButtons(asOf.toString(), { asOf = asOf.minusDays(1) }, { asOf = asOf.plusDays(1) })
                        val sheet = remember(book, asOf) { Ledger.balanceSheet(book, asOf) }
                        Section("Активы", sheet.assets, code)
                        Total("Итого активы", sheet.assetTotal, code)
                        Section("Обязательства", sheet.liabilities, code)
                        Total("Итого обязательства", sheet.liabilityTotal, code)
                        Section("Капитал", sheet.equity, code)
                        Total("Счета капитала", sheet.equityAccounts, code)
                        Total("Финансовый результат", sheet.result, code)
                        Total("Переоценка", sheet.revaluation, code)
                        Total("Итого пассив", sheet.rightTotal, code)
                        if (Money.eq(sheet.assetTotal, sheet.rightTotal)) {
                            Text("Баланс сходится", color = InGreen, modifier = Modifier.padding(16.dp))
                        } else {
                            Text("Расхождение", color = OutRed, modifier = Modifier.padding(16.dp))
                        }
                    }
                    1 -> {
                        RangeButtons(from, to, { from = it }, { to = it })
                        val flow = remember(book, from, to) { Ledger.cashFlow(book, from, to) }
                        Total("Деньги на начало", flow.opening, code)
                        FlowPair("Операционные", flow.operatingIn, flow.operatingOut, code)
                        FlowPair("Инвестиционные", flow.investingIn, flow.investingOut, code)
                        FlowPair("Финансовые", flow.financingIn, flow.financingOut, code)
                        FlowPair("Переводы", flow.transferIn, flow.transferOut, code)
                        Total("Деньги на конец", flow.closing, code)
                        if (Money.eq(flow.opening.add(flow.net), flow.closing)) {
                            Text("Движение сходится с остатком", color = InGreen, modifier = Modifier.padding(16.dp))
                        }
                        flow.lines.forEach { line ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text("${line.date}  ${line.label}", modifier = Modifier.weight(1f))
                                Text(Money.display(line.amount), color = if (line.amount.signum() < 0) OutRed else InGreen)
                            }
                        }
                    }
                    else -> {
                        RangeButtons(from, to, { from = it }, { to = it })
                        val statement = remember(book, from, to) { Ledger.incomeStatement(book, from, to) }
                        Section("Доходы", statement.income, code)
                        Total("Итого доходы", statement.incomeTotal, code)
                        Section("Расходы", statement.expenses, code)
                        Total("Итого расходы", statement.expenseTotal, code)
                        Section("Курсовые и ценовые разницы", statement.realized, code)
                        Total("Итого разницы", statement.realizedTotal, code)
                        Total("Финансовый результат", statement.result, code, emphasize = true)
                        Text(
                            "Переоценка открытых остатков в результат не входит — она в балансе.",
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFF616161),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodButtons(label: String, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
        TextButton(onClick = previous) { Text("←") }
        Text(label, modifier = Modifier.weight(1f).padding(top = 12.dp), fontWeight = FontWeight.Medium)
        TextButton(onClick = next) { Text("→") }
    }
}

@Composable
private fun RangeButtons(
    from: LocalDate,
    to: LocalDate,
    setFrom: (LocalDate) -> Unit,
    setTo: (LocalDate) -> Unit,
) {
    Column {
        PeriodButtons("с $from", { setFrom(from.minusDays(1)) }, { setFrom(from.plusDays(1)) })
        PeriodButtons("по $to", { setTo(to.minusDays(1)) }, { setTo(to.plusDays(1)) })
    }
}

@Composable
private fun Section(title: String, rows: List<Figure>, code: String) {
    Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
    if (rows.isEmpty()) {
        Text("Нет сумм", modifier = Modifier.padding(horizontal = 16.dp), color = Color(0xFF757575))
    }
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth().padding(start = (16 + row.depth * 14).dp, end = 16.dp, top = 4.dp, bottom = 4.dp)) {
            Text(
                row.label,
                modifier = Modifier.weight(1f),
                fontWeight = if (row.header) FontWeight.SemiBold else FontWeight.Normal,
                color = if (row.header) Color(0xFF616161) else Color.Unspecified,
            )
            if (!row.header) Text(Money.display(row.amount) + " " + code)
        }
    }
}

@Composable
private fun Total(label: String, amount: BigDecimal, code: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
        val color = when {
            !emphasize -> Color.Unspecified
            amount.signum() < 0 -> OutRed
            else -> InGreen
        }
        Text(Money.display(amount) + " " + code, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FlowPair(label: String, incoming: BigDecimal, outgoing: BigDecimal, code: String) {
    Text(label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
    Total("Поступления", incoming, code)
    Total("Выбытия", outgoing, code)
}
