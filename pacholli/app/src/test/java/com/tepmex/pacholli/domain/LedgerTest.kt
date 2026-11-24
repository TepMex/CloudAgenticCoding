package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {
    private val day = LocalDate.of(2026, 1, 10)
    private val rub = Commodity("rub", "RUB", "Рубль", true, LocalDate.of(2020, 1, 1))
    private val aapl = Commodity("aapl", "AAPL", "Apple", false, LocalDate.of(2020, 1, 1))
    private val usd = Commodity("usd", "USD", "Доллар", false, LocalDate.of(2020, 1, 1))

    @Test
    fun balanceSheetTiesAfterRevaluationAndSale() {
        val cash = acc("cash", "Наличные", "Assets:Cash", AccountSection.ASSET, cash = true)
        val broker = acc("broker", "Брокер", "Assets:Broker", AccountSection.ASSET, cash = false)
        val equity = acc("equity", "Капитал", "Equity:Opening", AccountSection.EQUITY, cash = false)
        var book = base(cash, broker, equity).copy(
            commodities = listOf(rub, aapl),
            quotes = listOf(Quote("aapl", day, BigDecimal("100"))),
        )
        book = book.plus(entry(book, "open", day, "cash", "equity", "1000"))
        book = book.plus(entry(book, "buy", day.plusDays(1), "broker", "cash", "200", "aapl", "2", "100"))
        val held = Ledger.balanceSheet(book.copy(quotes = listOf(Quote("aapl", day, BigDecimal("150")))), day.plusDays(2))
        assertMoney("1100", held.assetTotal)
        assertMoney("1000", held.equityAccounts)
        assertMoney("100", held.revaluation)
        assertMoney("0", held.result)
        assertMoney(held.assetTotal, held.rightTotal)

        val sold = book.plus(
            entry(
                book,
                "sell",
                day.plusDays(3),
                "cash",
                "broker",
                "300",
                creditCommodity = "aapl",
                creditQty = "2",
                creditPrice = "150",
            ),
        )
        val after = Ledger.balanceSheet(sold, day.plusDays(3))
        assertMoney("1100", after.assetTotal)
        assertMoney("100", after.result)
        assertMoney("0", after.revaluation)
        assertMoney(after.assetTotal, after.rightTotal)
        val pnl = Ledger.incomeStatement(sold, day, day.plusDays(3))
        assertMoney("100", pnl.realizedTotal)
        assertMoney("100", pnl.result)
    }

    @Test
    fun expenseIncomeAndOverdraft() {
        val cash = acc("cash", "Наличные", "Assets:Cash", AccountSection.ASSET, cash = true)
        val equity = acc("equity", "Капитал", "Equity:Opening", AccountSection.EQUITY, cash = false)
        val food = acc("food", "Продукты", "Expenses:Groceries", AccountSection.EXPENSE, cash = false)
        val salary = acc("salary", "Зарплата", "Income:Salary", AccountSection.INCOME, cash = false)
        var book = base(cash, equity, food, salary)
        book = book.plus(entry(book, "open", day, "cash", "equity", "1000"))
        book = book.plus(entry(book, "salary", day, "cash", "salary", "500"))
        book = book.plus(entry(book, "food", day, "food", "cash", "100"))
        val sheet = Ledger.balanceSheet(book, day)
        assertMoney("1400", sheet.assetTotal)
        assertMoney("1000", sheet.equityAccounts)
        assertMoney("400", sheet.result)
        assertMoney(sheet.assetTotal, sheet.rightTotal)
        val pnl = Ledger.incomeStatement(book, day, day)
        assertMoney("500", pnl.incomeTotal)
        assertMoney("100", pnl.expenseTotal)
        assertMoney("400", pnl.result)

        val empty = base(cash, food)
        val over = empty.plus(entry(empty, "od", day, "food", "cash", "50"))
        val overSheet = Ledger.balanceSheet(over, day)
        assertMoney("0", overSheet.assetTotal)
        assertMoney("50", overSheet.liabilityTotal)
        assertMoney("-50", overSheet.result)
        assertMoney(overSheet.assetTotal, overSheet.rightTotal)
    }

    @Test
    fun cashFlowClassifiesAndTies() {
        val cash = acc("cash", "Наличные", "Assets:Cash", AccountSection.ASSET, cash = true)
        val card = acc("card", "Карта", "Assets:Card", AccountSection.ASSET, cash = true)
        val broker = acc("broker", "Брокер", "Assets:Broker", AccountSection.ASSET, cash = false)
        val loan = acc("loan", "Кредит", "Liabilities:Loan", AccountSection.LIABILITY, cash = false)
        val equity = acc("equity", "Капитал", "Equity:Opening", AccountSection.EQUITY, cash = false)
        val food = acc("food", "Продукты", "Expenses:Groceries", AccountSection.EXPENSE, cash = false)
        val salary = acc("salary", "Зарплата", "Income:Salary", AccountSection.INCOME, cash = false)
        var book = base(cash, card, broker, loan, equity, food, salary)
        book = book.plus(entry(book, "open", day, "cash", "equity", "1000"))
        book = book.plus(entry(book, "salary", day.plusDays(1), "cash", "salary", "500"))
        book = book.plus(entry(book, "food", day.plusDays(1), "food", "cash", "80"))
        book = book.plus(entry(book, "buy", day.plusDays(2), "broker", "cash", "200"))
        book = book.plus(entry(book, "loan", day.plusDays(2), "cash", "loan", "300"))
        book = book.plus(entry(book, "move", day.plusDays(3), "card", "cash", "40"))
        val flow = Ledger.cashFlow(book, day, day.plusDays(3))
        assertMoney("0", flow.opening)
        assertMoney("500", flow.operatingIn)
        assertMoney("80", flow.operatingOut)
        assertMoney("200", flow.investingOut)
        assertMoney("1300", flow.financingIn)
        assertMoney("40", flow.transferIn)
        assertMoney("40", flow.transferOut)
        assertMoney(flow.opening.add(flow.net), flow.closing)
        assertMoney("1520", flow.closing)
    }

    @Test
    fun parentAccountCannotBePosted() {
        val parent = acc("cash", "Наличные", "Assets:Cash", AccountSection.ASSET, cash = true)
        val child = parent.copy(
            id = "wallet",
            parentId = "cash",
            name = "Кошелёк",
            beancountName = "Assets:Cash:Wallet",
        )
        val food = acc("food", "Продукты", "Expenses:Groceries", AccountSection.EXPENSE, cash = false)
        val book = base(parent, child, food)
        val bad = entry(book, "bad", day, "food", "cash", "10")
        assertTrue(Ledger.explain(book, bad)!!.contains("субсчета"))
        val good = entry(book, "good", day, "food", "wallet", "10")
        assertNull(Ledger.explain(book, good))
    }

    @Test
    fun currencyGainIsRealized() {
        val rubCash = acc("rub", "Рубли", "Assets:Cash", AccountSection.ASSET, cash = true)
        val usdCash = acc("usd-cash", "Доллары", "Assets:CashUSD", AccountSection.ASSET, cash = true)
        var book = Book(
            title = "fx",
            commodities = listOf(rub, usd),
            quotes = listOf(Quote("usd", day, BigDecimal("90"))),
            accounts = listOf(rubCash, usdCash),
            entries = emptyList(),
        )
        book = book.plus(
            entry(
                book, "buy", day, "usd-cash", "rub", "9000",
                debitCommodity = "usd", debitQty = "100", debitPrice = "90",
            ),
        )
        book = book.plus(
            entry(
                book, "spend", day.plusDays(1), "rub", "usd-cash", "4000",
                creditCommodity = "usd", creditQty = "40", creditPrice = "100",
            ),
        )
        val sheet = Ledger.balanceSheet(book, day.plusDays(1))
        assertMoney(sheet.assetTotal, sheet.rightTotal)
        val pnl = Ledger.incomeStatement(book, day, day.plusDays(1))
        assertMoney("400", pnl.realizedTotal)
    }

    @Test
    fun weekContainsTuesday() {
        val tuesday = LocalDate.of(2026, 11, 24)
        assertTrue(Dates.label(tuesday).contains("(Вт)"))
        val range = Dates.range(tuesday, Span.WEEK)!!
        assertEquals(LocalDate.of(2026, 11, 23), range.first)
        assertEquals(LocalDate.of(2026, 11, 29), range.second)
    }

    private fun base(vararg accounts: Account) = Book("t", listOf(rub), emptyList(), accounts.toList(), emptyList())

    private fun Book.plus(entry: Entry) = copy(entries = entries + entry)

    private fun acc(
        id: String,
        name: String,
        beancount: String,
        section: AccountSection,
        cash: Boolean,
    ) = Account(
        id = id,
        parentId = null,
        name = name,
        beancountName = beancount,
        type = section.defaultType,
        section = section,
        allowsSubaccounts = true,
        cash = cash,
        openedOn = LocalDate.of(2020, 1, 1),
        closedOn = null,
    )

    private fun entry(
        book: Book,
        id: String,
        date: LocalDate,
        debit: String,
        credit: String,
        base: String,
        debitCommodity: String = "rub",
        debitQty: String = base,
        debitPrice: String = "1",
        creditCommodity: String = "rub",
        creditQty: String? = null,
        creditPrice: String = "1",
    ): Entry {
        val debitUnit = unit(debitCommodity)
        val creditUnit = unit(creditCommodity)
        val qty = BigDecimal(debitQty)
        val debitBase = if (debitUnit.operating) BigDecimal(base) else qty.multiply(BigDecimal(debitPrice))
        val creditQuantity = BigDecimal(creditQty ?: if (creditUnit.operating) base else debitQty)
        val creditBase = if (creditUnit.operating) {
            BigDecimal(base)
        } else {
            creditQuantity.multiply(BigDecimal(creditPrice))
        }
        return Entry(
            id = id,
            date = date,
            timeMinutes = null,
            payee = "",
            narration = "",
            debit = Posting(debit, Side.DEBIT, qty, debitUnit.id, debitBase),
            credit = Posting(credit, Side.CREDIT, creditQuantity, creditUnit.id, creditBase),
        )
    }

    private fun unit(id: String) = when (id) {
        aapl.id -> aapl
        usd.id -> usd
        else -> rub
    }

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(Money.s(actual)))
    }

    private fun assertMoney(expected: BigDecimal, actual: BigDecimal) {
        assertEquals(0, Money.s(expected).compareTo(Money.s(actual)))
    }
}
