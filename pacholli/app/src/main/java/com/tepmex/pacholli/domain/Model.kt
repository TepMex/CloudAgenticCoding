package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class AccountType {
    ACTIVE,
    PASSIVE,
    ACTIVE_PASSIVE,
}

enum class AccountSection {
    ASSET,
    LIABILITY,
    EQUITY,
    INCOME,
    EXPENSE,
    ;

    val root: String
        get() = when (this) {
            ASSET -> "Assets"
            LIABILITY -> "Liabilities"
            EQUITY -> "Equity"
            INCOME -> "Income"
            EXPENSE -> "Expenses"
        }

    val title: String
        get() = when (this) {
            ASSET -> "Активы"
            LIABILITY -> "Обязательства"
            EQUITY -> "Капитал"
            INCOME -> "Доходы"
            EXPENSE -> "Расходы"
        }

    val defaultType: AccountType
        get() = when (this) {
            ASSET, EXPENSE -> AccountType.ACTIVE
            LIABILITY, EQUITY, INCOME -> AccountType.PASSIVE
        }

    val balanceSheet: Boolean
        get() = this == ASSET || this == LIABILITY || this == EQUITY
}

enum class Side {
    DEBIT,
    CREDIT,
}

enum class Span {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ALL,
    ;

    val title: String
        get() = when (this) {
            DAY -> "День"
            WEEK -> "Неделя"
            MONTH -> "Месяц"
            YEAR -> "Год"
            ALL -> "Всё"
        }
}

data class Commodity(
    val id: String,
    val code: String,
    val name: String,
    val operating: Boolean,
    val openedOn: LocalDate,
)

data class Quote(
    val commodityId: String,
    val date: LocalDate,
    val price: BigDecimal,
)

data class Account(
    val id: String,
    val parentId: String?,
    val name: String,
    val beancountName: String,
    val type: AccountType,
    val section: AccountSection,
    val allowsSubaccounts: Boolean,
    val cash: Boolean,
    val openedOn: LocalDate,
    val closedOn: LocalDate?,
)

data class Posting(
    val accountId: String,
    val side: Side,
    val quantity: BigDecimal,
    val commodityId: String,
    val baseAmount: BigDecimal,
)

data class Entry(
    val id: String,
    val date: LocalDate,
    val timeMinutes: Int?,
    val payee: String,
    val narration: String,
    val debit: Posting,
    val credit: Posting,
)

data class Book(
    val title: String,
    val commodities: List<Commodity>,
    val quotes: List<Quote>,
    val accounts: List<Account>,
    val entries: List<Entry>,
) {
    fun operatingOrNull(): Commodity? = commodities.firstOrNull { it.operating }

    fun account(id: String): Account? = accounts.find { it.id == id }

    fun commodity(id: String): Commodity? = commodities.find { it.id == id }

    fun commodityByCode(code: String): Commodity? = commodities.find { it.code == code }

    fun accountByName(name: String): Account? = accounts.find { it.beancountName == name }

    fun childrenOf(id: String): List<Account> = accounts.filter { it.parentId == id }

    fun hasChildren(id: String): Boolean = accounts.any { it.parentId == id }

    fun sortedEntries(): List<Entry> = entries.sortedWith(
        compareBy<Entry> { it.date }.thenBy { it.timeMinutes ?: -1 }.thenBy { it.id },
    )

    fun path(account: Account): String {
        val names = ArrayList<String>()
        var current: Account? = account
        val guard = HashSet<String>()
        while (current != null && guard.add(current.id)) {
            names += current.name
            current = current.parentId?.let { account(it) }
        }
        return names.asReversed().joinToString(" · ")
    }

    companion object {
        fun empty(): Book = Book("", emptyList(), emptyList(), emptyList(), emptyList())
    }
}

data class Parsed<T>(val value: T?, val errors: List<String>) {
    val ok: Boolean get() = value != null && errors.isEmpty()

    companion object {
        fun <T> ok(value: T): Parsed<T> = Parsed(value, emptyList())

        fun <T> fail(vararg messages: String): Parsed<T> = Parsed(null, messages.toList())

        fun <T> fail(messages: List<String>): Parsed<T> = Parsed(null, messages)
    }
}
