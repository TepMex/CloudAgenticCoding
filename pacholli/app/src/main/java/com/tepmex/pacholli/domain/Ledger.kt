package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class Figure(
    val label: String,
    val amount: BigDecimal,
    val depth: Int,
    val header: Boolean,
)

data class BalanceSheet(
    val asOf: LocalDate,
    val assets: List<Figure>,
    val assetTotal: BigDecimal,
    val liabilities: List<Figure>,
    val liabilityTotal: BigDecimal,
    val equity: List<Figure>,
    val equityAccounts: BigDecimal,
    val result: BigDecimal,
    val revaluation: BigDecimal,
    val equityTotal: BigDecimal,
    val rightTotal: BigDecimal,
)

data class CashLine(
    val entryId: String,
    val date: LocalDate,
    val label: String,
    val amount: BigDecimal,
    val kind: CashKind,
)

enum class CashKind { OPERATING, INVESTING, FINANCING, TRANSFER }

data class CashFlow(
    val from: LocalDate,
    val to: LocalDate,
    val opening: BigDecimal,
    val operatingIn: BigDecimal,
    val operatingOut: BigDecimal,
    val investingIn: BigDecimal,
    val investingOut: BigDecimal,
    val financingIn: BigDecimal,
    val financingOut: BigDecimal,
    val transferIn: BigDecimal,
    val transferOut: BigDecimal,
    val closing: BigDecimal,
    val lines: List<CashLine>,
) {
    val net: BigDecimal
        get() = Money.s(
            operatingIn.subtract(operatingOut)
                .add(investingIn).subtract(investingOut)
                .add(financingIn).subtract(financingOut)
                .add(transferIn).subtract(transferOut),
        )
}

data class IncomeStatement(
    val from: LocalDate,
    val to: LocalDate,
    val income: List<Figure>,
    val incomeTotal: BigDecimal,
    val expenses: List<Figure>,
    val expenseTotal: BigDecimal,
    val realized: List<Figure>,
    val realizedTotal: BigDecimal,
    val result: BigDecimal,
)

data class Snapshot(
    val lots: Map<Pair<String, String>, Lot>,
    val realized: Map<String, BigDecimal>,
    val posted: Map<String, BigDecimal>,
)

object Ledger {
    fun snapshot(book: Book, inclusive: LocalDate?): Snapshot {
        val lots = HashMap<Pair<String, String>, Lot>()
        val realized = HashMap<String, BigDecimal>()
        val posted = HashMap<String, BigDecimal>()
        for (entry in book.sortedEntries()) {
            if (inclusive != null && entry.date.isAfter(inclusive)) break
            for (posting in listOf(entry.debit, entry.credit)) {
                val account = book.account(posting.accountId) ?: continue
                val signed = if (posting.side == Side.DEBIT) posting.baseAmount else posting.baseAmount.negate()
                posted[account.id] = Money.s((posted[account.id] ?: BigDecimal.ZERO).add(signed))
                if (!account.section.balanceSheet) continue
                val key = account.id to posting.commodityId
                val (gain, lot) = Inventory.apply(
                    lots[key] ?: Lot.EMPTY,
                    posting.side,
                    posting.quantity,
                    posting.baseAmount,
                )
                lots[key] = lot
                if (gain.signum() != 0) {
                    realized[account.id] = Money.s((realized[account.id] ?: BigDecimal.ZERO).add(gain))
                }
            }
        }
        return Snapshot(lots, realized, posted)
    }

    fun quote(book: Book, commodityId: String, on: LocalDate): BigDecimal? {
        val commodity = book.commodity(commodityId) ?: return null
        if (commodity.operating) return BigDecimal.ONE
        return book.quotes
            .filter { it.commodityId == commodityId && !it.date.isAfter(on) }
            .maxByOrNull { it.date }
            ?.price
            ?.let { Money.s(it) }
    }

    fun market(book: Book, lot: Lot, commodityId: String, on: LocalDate): BigDecimal {
        if (lot.qty.signum() == 0) return Money.ZERO
        val price = quote(book, commodityId, on)
            ?: lot.cost.divide(lot.qty, Money.SCALE, RoundingMode.HALF_UP)
        return Money.s(lot.qty.multiply(price))
    }

    fun accountValue(book: Book, shot: Snapshot, account: Account, on: LocalDate): BigDecimal {
        var sum = BigDecimal.ZERO
        for ((key, lot) in shot.lots) {
            if (key.first != account.id) continue
            sum = sum.add(market(book, lot, key.second, on))
        }
        return Money.s(sum)
    }

    fun accountCost(shot: Snapshot, accountId: String): BigDecimal {
        var sum = BigDecimal.ZERO
        for ((key, lot) in shot.lots) {
            if (key.first == accountId) sum = sum.add(lot.cost)
        }
        return Money.s(sum)
    }

    fun balanceSheet(book: Book, asOf: LocalDate): BalanceSheet {
        val shot = snapshot(book, asOf)
        val assetRows = ArrayList<Pair<Account, BigDecimal>>()
        val liabilityRows = ArrayList<Pair<Account, BigDecimal>>()
        val equityRows = ArrayList<Pair<Account, BigDecimal>>()
        var revaluation = BigDecimal.ZERO
        for (account in book.accounts) {
            if (!account.section.balanceSheet) continue
            val value = accountValue(book, shot, account, asOf)
            val cost = accountCost(shot, account.id)
            revaluation = revaluation.add(value.subtract(cost))
            when {
                value.signum() > 0 -> assetRows += account to value
                value.signum() < 0 && account.section == AccountSection.EQUITY ->
                    equityRows += account to value.negate()
                value.signum() < 0 -> liabilityRows += account to value.negate()
            }
        }
        revaluation = Money.s(revaluation)
        val result = financialResult(book, shot)
        val assets = tree(book, assetRows)
        val liabilities = tree(book, liabilityRows)
        val equity = tree(book, equityRows)
        val assetTotal = sumRows(assetRows)
        val liabilityTotal = sumRows(liabilityRows)
        val equityAccounts = sumRows(equityRows)
        val equityTotal = Money.s(equityAccounts.add(result).add(revaluation))
        return BalanceSheet(
            asOf = asOf,
            assets = assets,
            assetTotal = assetTotal,
            liabilities = liabilities,
            liabilityTotal = liabilityTotal,
            equity = equity,
            equityAccounts = equityAccounts,
            result = result,
            revaluation = revaluation,
            equityTotal = equityTotal,
            rightTotal = Money.s(liabilityTotal.add(equityTotal)),
        )
    }

    fun cashFlow(book: Book, from: LocalDate, to: LocalDate): CashFlow {
        val opening = cashPosted(book, snapshot(book, from.minusDays(1)))
        var operatingIn = BigDecimal.ZERO
        var operatingOut = BigDecimal.ZERO
        var investingIn = BigDecimal.ZERO
        var investingOut = BigDecimal.ZERO
        var financingIn = BigDecimal.ZERO
        var financingOut = BigDecimal.ZERO
        var transferIn = BigDecimal.ZERO
        var transferOut = BigDecimal.ZERO
        val lines = ArrayList<CashLine>()
        for (entry in book.sortedEntries()) {
            if (entry.date.isBefore(from) || entry.date.isAfter(to)) continue
            val debitAccount = book.account(entry.debit.accountId) ?: continue
            val creditAccount = book.account(entry.credit.accountId) ?: continue
            val base = entry.debit.baseAmount
            val label = entryLabel(entry, debitAccount, creditAccount)
            when {
                debitAccount.cash && creditAccount.cash -> {
                    transferIn = transferIn.add(base)
                    transferOut = transferOut.add(base)
                    lines += CashLine(entry.id, entry.date, label, Money.ZERO, CashKind.TRANSFER)
                }
                debitAccount.cash -> {
                    val kind = kindOf(creditAccount)
                    when (kind) {
                        CashKind.OPERATING -> operatingIn = operatingIn.add(base)
                        CashKind.INVESTING -> investingIn = investingIn.add(base)
                        CashKind.FINANCING -> financingIn = financingIn.add(base)
                        CashKind.TRANSFER -> Unit
                    }
                    lines += CashLine(entry.id, entry.date, label, base, kind)
                }
                creditAccount.cash -> {
                    val kind = kindOf(debitAccount)
                    when (kind) {
                        CashKind.OPERATING -> operatingOut = operatingOut.add(base)
                        CashKind.INVESTING -> investingOut = investingOut.add(base)
                        CashKind.FINANCING -> financingOut = financingOut.add(base)
                        CashKind.TRANSFER -> Unit
                    }
                    lines += CashLine(entry.id, entry.date, label, base.negate(), kind)
                }
            }
        }
        return CashFlow(
            from = from,
            to = to,
            opening = opening,
            operatingIn = Money.s(operatingIn),
            operatingOut = Money.s(operatingOut),
            investingIn = Money.s(investingIn),
            investingOut = Money.s(investingOut),
            financingIn = Money.s(financingIn),
            financingOut = Money.s(financingOut),
            transferIn = Money.s(transferIn),
            transferOut = Money.s(transferOut),
            closing = cashPosted(book, snapshot(book, to)),
            lines = lines,
        )
    }

    fun incomeStatement(book: Book, from: LocalDate, to: LocalDate): IncomeStatement {
        val before = snapshot(book, from.minusDays(1))
        val after = snapshot(book, to)
        val incomeRows = ArrayList<Pair<Account, BigDecimal>>()
        val expenseRows = ArrayList<Pair<Account, BigDecimal>>()
        for (account in book.accounts) {
            val delta = Money.s(
                (after.posted[account.id] ?: BigDecimal.ZERO)
                    .subtract(before.posted[account.id] ?: BigDecimal.ZERO),
            )
            when (account.section) {
                AccountSection.INCOME -> {
                    val amount = Money.s(delta.negate())
                    if (amount.signum() != 0) incomeRows += account to amount
                }
                AccountSection.EXPENSE -> if (delta.signum() != 0) expenseRows += account to delta
                else -> Unit
            }
        }
        val realizedRows = ArrayList<Pair<Account, BigDecimal>>()
        val accountIds = (before.realized.keys + after.realized.keys).toSet()
        for (id in accountIds) {
            val account = book.account(id) ?: continue
            val amount = Money.s(
                (after.realized[id] ?: BigDecimal.ZERO).subtract(before.realized[id] ?: BigDecimal.ZERO),
            )
            if (amount.signum() != 0) realizedRows += account to amount
        }
        val incomeTotal = sumRows(incomeRows)
        val expenseTotal = sumRows(expenseRows)
        val realizedTotal = sumRows(realizedRows)
        return IncomeStatement(
            from = from,
            to = to,
            income = tree(book, incomeRows),
            incomeTotal = incomeTotal,
            expenses = tree(book, expenseRows),
            expenseTotal = expenseTotal,
            realized = tree(book, realizedRows),
            realizedTotal = realizedTotal,
            result = Money.s(incomeTotal.subtract(expenseTotal).add(realizedTotal)),
        )
    }

    fun explain(book: Book, entry: Entry): String? {
        if (entry.timeMinutes != null && entry.timeMinutes !in 0..1439) return "Некорректное время"
        if (entry.debit.accountId == entry.credit.accountId) return "Дебет и кредит — разные счета"
        explainPosting(book, entry, entry.debit)?.let { return it }
        explainPosting(book, entry, entry.credit)?.let { return it }
        if (entry.debit.side != Side.DEBIT || entry.credit.side != Side.CREDIT) return "Нужны дебет и кредит"
        if (!Money.eq(entry.debit.baseAmount, entry.credit.baseAmount)) {
            return "Дебет и кредит не сходятся в валюте учёта"
        }
        if (entry.debit.commodityId == entry.credit.commodityId &&
            !Money.eq(entry.debit.quantity, entry.credit.quantity)
        ) {
            return "Для одной единицы количества дебета и кредита должны совпадать"
        }
        return null
    }

    fun buildEntry(
        book: Book,
        id: String,
        date: LocalDate,
        minutes: Int?,
        payee: String,
        narration: String,
        debitAccountId: String?,
        creditAccountId: String?,
        debitQty: BigDecimal,
        debitCommodityId: String,
        creditQty: BigDecimal,
        creditCommodityId: String,
        debitPrice: BigDecimal,
        creditPrice: BigDecimal,
    ): Parsed<Entry> {
        if (debitAccountId == null || creditAccountId == null) {
            return Parsed.fail("Выберите счёт дебета и счёт кредита")
        }
        val debitCommodity = book.commodity(debitCommodityId)
            ?: return Parsed.fail("Неизвестная единица дебета")
        val creditCommodity = book.commodity(creditCommodityId)
            ?: return Parsed.fail("Неизвестная единица кредита")
        if (debitQty.signum() <= 0 || creditQty.signum() <= 0) {
            return Parsed.fail("Сумма должна быть больше нуля")
        }
        val debitBase = booked(debitQty, debitCommodity, debitPrice)
            ?: return Parsed.fail("Укажите стоимость ${debitCommodity.code}")
        val creditBase = booked(creditQty, creditCommodity, creditPrice)
            ?: return Parsed.fail("Укажите стоимость ${creditCommodity.code}")
        if (!Money.eq(debitBase, creditBase)) {
            val code = book.operatingOrNull()?.code ?: ""
            return Parsed.fail(
                "Суммы не сходятся: дебет ${Money.render(debitBase)} $code, кредит ${Money.render(creditBase)} $code",
            )
        }
        val entry = Entry(
            id = id,
            date = date,
            timeMinutes = minutes,
            payee = oneLine(payee),
            narration = oneLine(narration),
            debit = Posting(debitAccountId, Side.DEBIT, Money.s(debitQty), debitCommodityId, debitBase),
            credit = Posting(creditAccountId, Side.CREDIT, Money.s(creditQty), creditCommodityId, creditBase),
        )
        val error = explain(book, entry)
        return if (error == null) Parsed.ok(entry) else Parsed.fail(error)
    }

    fun search(book: Book, query: String): List<Entry> {
        val needle = query.trim().lowercase()
        val sorted = book.sortedEntries().asReversed()
        if (needle.isEmpty()) return sorted
        return sorted.filter { entry ->
            val debit = book.account(entry.debit.accountId)
            val credit = book.account(entry.credit.accountId)
            val commodity = book.commodity(entry.debit.commodityId)
            listOf(
                entry.payee,
                entry.narration,
                debit?.name,
                credit?.name,
                debit?.beancountName,
                credit?.beancountName,
                commodity?.code,
                Money.render(entry.debit.baseAmount),
                Money.display(entry.debit.baseAmount),
            ).any { it?.lowercase()?.contains(needle) == true }
        }
    }

    fun journal(book: Book, anchor: LocalDate, span: Span): List<Entry> =
        book.sortedEntries().asReversed().filter { Dates.contains(it.date, anchor, span) }

    private fun booked(qty: BigDecimal, commodity: Commodity, price: BigDecimal): BigDecimal? {
        val used = if (commodity.operating) BigDecimal.ONE else price
        if (!commodity.operating && used.signum() <= 0) return null
        return Money.s(qty.multiply(used))
    }

    private fun explainPosting(book: Book, entry: Entry, posting: Posting): String? {
        val account = book.account(posting.accountId) ?: return "Счёт не найден"
        if (book.hasChildren(account.id)) {
            return "У счёта «${account.name}» есть субсчета — проводка только по ним"
        }
        if (entry.date.isBefore(account.openedOn)) {
            return "Счёт «${account.name}» открыт ${account.openedOn}"
        }
        val closed = account.closedOn
        if (closed != null && !entry.date.isBefore(closed)) {
            return "Счёт «${account.name}» закрыт $closed"
        }
        val commodity = book.commodity(posting.commodityId) ?: return "Неизвестная единица"
        if (posting.quantity.signum() <= 0 || posting.baseAmount.signum() <= 0) {
            return "Сумма должна быть больше нуля"
        }
        if (commodity.operating && !Money.eq(posting.quantity, posting.baseAmount)) {
            return "Для ${commodity.code} сумма равна количеству"
        }
        return null
    }

    private fun financialResult(book: Book, shot: Snapshot): BigDecimal {
        var income = BigDecimal.ZERO
        var expense = BigDecimal.ZERO
        for ((id, amount) in shot.posted) {
            when (book.account(id)?.section) {
                AccountSection.INCOME -> income = income.add(amount.negate())
                AccountSection.EXPENSE -> expense = expense.add(amount)
                else -> Unit
            }
        }
        val realized = shot.realized.values.fold(BigDecimal.ZERO, BigDecimal::add)
        return Money.s(income.subtract(expense).add(realized))
    }

    private fun cashPosted(book: Book, shot: Snapshot): BigDecimal {
        var sum = BigDecimal.ZERO
        for (account in book.accounts) {
            if (account.cash) sum = sum.add(shot.posted[account.id] ?: BigDecimal.ZERO)
        }
        return Money.s(sum)
    }

    private fun kindOf(account: Account): CashKind = when (account.section) {
        AccountSection.INCOME, AccountSection.EXPENSE -> CashKind.OPERATING
        AccountSection.ASSET -> CashKind.INVESTING
        AccountSection.LIABILITY, AccountSection.EQUITY -> CashKind.FINANCING
    }

    private fun entryLabel(entry: Entry, debit: Account, credit: Account): String {
        val who = entry.payee.ifBlank { entry.narration }
        val accounts = "${debit.name} ← ${credit.name}"
        return if (who.isBlank()) accounts else "$who · $accounts"
    }

    private fun sumRows(rows: List<Pair<Account, BigDecimal>>): BigDecimal =
        Money.s(rows.fold(BigDecimal.ZERO) { acc, row -> acc.add(row.second) })

    private fun tree(book: Book, rows: List<Pair<Account, BigDecimal>>): List<Figure> {
        if (rows.isEmpty()) return emptyList()
        val amounts = rows.associate { it.first.id to it.second }
        val shown = HashSet<String>()
        for ((account, _) in rows) {
            shown += account.id
            var parent = account.parentId?.let { book.account(it) }
            val guard = HashSet<String>()
            while (parent != null && guard.add(parent.id)) {
                shown += parent.id
                parent = parent.parentId?.let { book.account(it) }
            }
        }
        return book.accounts
            .filter { it.id in shown }
            .sortedBy { it.beancountName }
            .map { account ->
                val amount = amounts[account.id]
                Figure(
                    label = account.name,
                    amount = amount ?: Money.ZERO,
                    depth = account.beancountName.count { it == ':' } - 1,
                    header = amount == null,
                )
            }
    }

    private fun oneLine(text: String): String = text.replace('\n', ' ').replace('\r', ' ').trim()
}
