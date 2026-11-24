package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BeancountTest {
    @Test
    fun roundTripKeepsChartPricesAndEntries() {
        val text = """
            option "title" "Дом"
            option "operating_currency" "RUB"

            2020-01-01 commodity RUB
              name: "Российский рубль"
              id: "rub"
              operating: "true"

            2020-01-01 commodity AAPL
              name: "Apple"
              id: "aapl"

            2026-11-01 price AAPL 100.00 RUB

            2020-01-01 open Assets:Cash RUB
              name: "Наличные"
              type: "active"
              allows-subaccounts: "true"
              cash: "true"
              id: "cash"

            2020-01-01 open Assets:Broker RUB
              name: "Брокер"
              type: "active-passive"
              allows-subaccounts: "false"
              cash: "false"
              id: "broker"

            2020-01-01 open Expenses:Groceries RUB
              name: "Продукты"
              type: "active"
              allows-subaccounts: "false"
              cash: "false"
              id: "food"

            2020-01-01 open Income:Salary RUB
              name: "Зарплата"
              type: "passive"
              allows-subaccounts: "false"
              cash: "false"
              id: "salary"

            2026-11-24 * "Работа" "Аванс"
              time: "09:05"
              id: "pay"
              Assets:Cash     1,000.00 RUB
              Income:Salary  -1,000.00 RUB

            2026-11-24 * "Брокер" "Покупка"
              id: "buy"
              Assets:Broker   2 AAPL {100.00 RUB}
              Assets:Cash    -200.00 RUB
        """.trimIndent()
        val parsed = Beancount.parse(text)
        assertTrue(parsed.errors.joinToString(), parsed.ok)
        val book = parsed.value!!
        assertEquals("Дом", book.title)
        assertEquals("RUB", book.operatingOrNull()?.code)
        assertEquals(AccountType.ACTIVE_PASSIVE, book.account("broker")?.type)
        assertTrue(book.account("cash")?.cash == true)
        assertFalse(book.account("broker")?.allowsSubaccounts == true)
        assertEquals(2, book.entries.size)
        val buy = book.entries.first { it.id == "buy" }
        assertEquals(Side.DEBIT, buy.debit.side)
        assertEquals("broker", buy.debit.accountId)
        assertEquals(0, BigDecimal("2").compareTo(buy.debit.quantity))
        assertEquals(0, BigDecimal("200").compareTo(Money.s(buy.debit.baseAmount)))
        assertEquals(9 * 60 + 5, book.entries.first { it.id == "pay" }.timeMinutes)

        val exported = Beancount.render(book)
        val again = Beancount.parse(exported)
        assertTrue(again.errors.joinToString(), again.ok)
        assertEquals(exported, Beancount.render(again.value!!))
        val sheet = Ledger.balanceSheet(again.value!!, LocalDate.of(2026, 11, 24))
        assertEquals(0, sheet.assetTotal.compareTo(sheet.rightTotal))
    }

    @Test
    fun rejectsUnbalancedThreeLegsAndBalanceDirective() {
        val head = """
            option "title" "Дом"
            option "operating_currency" "RUB"
            2020-01-01 open Assets:Cash
              id: "cash"
              name: "Наличные"
            2020-01-01 open Expenses:Groceries
              id: "food"
              name: "Продукты"
        """.trimIndent()
        fun book(body: String) = Beancount.parse(head + "\n" + body.trimIndent())
        val three = book(
            """
            2026-01-02 * "Магазин"
              Expenses:Groceries  10.00 RUB
              Expenses:Groceries   5.00 RUB
              Assets:Cash        -15.00 RUB
            """,
        )
        assertFalse(three.ok)
        assertTrue(three.errors.joinToString().contains("два счёта"))

        val gap = book(
            """
            2026-01-02 * "Магазин"
              Expenses:Groceries  10.00 RUB
              Assets:Cash         -9.00 RUB
            """,
        )
        assertFalse(gap.ok)

        val balance = book(
            """
            2026-01-02 balance Assets:Cash 0.00 RUB
            """,
        )
        assertFalse(balance.ok)
        assertTrue(balance.errors.joinToString().contains("не поддерживается"))
    }

    @Test
    fun editorRejectsInvalidTextAndParent() {
        val book = Seed.book()
        val bad = Beancount.parseEntry("это не проводка", book)
        assertFalse(bad.ok)
        val parent = book.account("acc-cash")!!
        val withChild = Chart.commitAccount(
            book,
            AccountDraft(
                id = "wallet",
                parentId = parent.id,
                name = "Кошелёк",
                leaf = "Wallet",
                type = AccountType.ACTIVE,
                section = AccountSection.ASSET,
                allowsSubaccounts = false,
                cash = true,
                openedOn = Seed.opened,
                closedOn = null,
            ),
        )
        val text = """
            2026-11-24 * "Магазин" "Хлеб"
              Expenses:Groceries   12.50 RUB
              Assets:Cash         -12.50 RUB
        """.trimIndent()
        val refused = Beancount.parseEntry(text, withChild)
        assertFalse(refused.ok)
        assertTrue(refused.errors.joinToString().contains("субсчета"))
        val accepted = Beancount.parseEntry(
            """
            2026-11-24 * "Магазин" "Хлеб"
              Expenses:Groceries      12.50 RUB
              Assets:Cash:Wallet     -12.50 RUB
            """.trimIndent(),
            withChild,
            forceId = "keep",
        )
        assertTrue(accepted.errors.joinToString(), accepted.ok)
        assertEquals("keep", accepted.value?.id)
        assertNull(Ledger.explain(withChild, accepted.value!!))
    }

    @Test
    fun elidedAmountAndPriceDirective() {
        val parsed = Beancount.parse(
            """
            option "title" "Дом"
            option "operating_currency" "RUB"
            2020-01-01 commodity USD
              name: "Доллар"
              id: "usd"
            2026-01-01 price USD 90.00 RUB
            2020-01-01 open Assets:Cash
              id: "rub"
              name: "Рубли"
              cash: "true"
            2020-01-01 open Assets:CashUSD
              id: "usd-cash"
              name: "Доллары"
              cash: "true"
            2026-01-02 * "Обмен"
              Assets:CashUSD  10 USD
              Assets:Cash
            """.trimIndent(),
        )
        assertTrue(parsed.errors.joinToString(), parsed.ok)
        val entry = parsed.value!!.entries.single()
        assertEquals("usd-cash", entry.debit.accountId)
        assertEquals("rub", entry.credit.accountId)
        assertEquals(0, BigDecimal("900").compareTo(Money.s(entry.debit.baseAmount)))
    }

    @Test
    fun subaccountFlagAndRename() {
        val book = Seed.book()
        val cash = book.account("acc-cash")!!
        val blocked = cash.copy(allowsSubaccounts = false)
        val frozen = book.copy(accounts = book.accounts.map { if (it.id == cash.id) blocked else it })
        val child = AccountDraft(
            id = "wallet",
            parentId = cash.id,
            name = "Кошелёк",
            leaf = "Wallet",
            type = AccountType.ACTIVE,
            section = AccountSection.ASSET,
            allowsSubaccounts = true,
            cash = true,
            openedOn = Seed.opened,
            closedOn = null,
        )
        assertTrue(Chart.checkAccount(frozen, child)!!.contains("не допускает"))
        assertNull(Chart.checkAccount(book, child))
        val withChild = Chart.commitAccount(book, child)
        val renamed = Chart.commitAccount(
            withChild,
            Chart.draftOf(withChild.account("acc-cash")!!).copy(leaf = "Wallet", name = "Кошельки"),
        )
        assertEquals("Assets:Wallet", renamed.account("acc-cash")?.beancountName)
        assertEquals("Assets:Wallet:Wallet", renamed.account("wallet")?.beancountName)
        assertTrue(Chart.checkAccount(renamed, Chart.draftOf(renamed.account("acc-cash")!!).copy(allowsSubaccounts = false))!!.contains("субсчета"))
    }
}
