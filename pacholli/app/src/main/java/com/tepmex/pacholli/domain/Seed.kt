package com.tepmex.pacholli.domain

import java.time.LocalDate

object Seed {
    val opened: LocalDate = LocalDate.of(2020, 1, 1)
    const val RUB = "commodity-rub"

    fun book(): Book {
        val rub = Commodity(RUB, "RUB", "Российский рубль", operating = true, openedOn = opened)
        return Book(
            title = "Личные финансы",
            commodities = listOf(rub),
            quotes = emptyList(),
            accounts = listOf(
                account("acc-cash", "Наличные", "Assets:Cash", AccountSection.ASSET, cash = true),
                account("acc-card", "Карта", "Assets:Card", AccountSection.ASSET, cash = true),
                account("acc-property", "Имущество", "Assets:Property", AccountSection.ASSET, cash = false),
                account(
                    "acc-credit",
                    "Кредитная карта",
                    "Liabilities:CreditCard",
                    AccountSection.LIABILITY,
                    cash = false,
                ),
                account("acc-equity", "Начальное сальдо", "Equity:Opening", AccountSection.EQUITY, cash = false),
                account("acc-salary", "Зарплата", "Income:Salary", AccountSection.INCOME, cash = false),
                account("acc-other-income", "Прочие доходы", "Income:Other", AccountSection.INCOME, cash = false),
                account("acc-food", "Продукты", "Expenses:Groceries", AccountSection.EXPENSE, cash = false),
                account("acc-transport", "Транспорт", "Expenses:Transport", AccountSection.EXPENSE, cash = false),
                account("acc-home", "Жильё", "Expenses:Housing", AccountSection.EXPENSE, cash = false),
                account("acc-other", "Прочие расходы", "Expenses:Other", AccountSection.EXPENSE, cash = false),
            ),
            entries = emptyList(),
        )
    }

    private fun account(
        id: String,
        name: String,
        beancountName: String,
        section: AccountSection,
        cash: Boolean,
    ): Account = Account(
        id = id,
        parentId = null,
        name = name,
        beancountName = beancountName,
        type = section.defaultType,
        section = section,
        allowsSubaccounts = true,
        cash = cash,
        openedOn = opened,
        closedOn = null,
    )
}
