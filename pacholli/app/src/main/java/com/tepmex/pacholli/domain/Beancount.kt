package com.tepmex.pacholli.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

object Beancount {
    fun parse(text: String): Parsed<Book> = parseDocument(text, existing = null)

        fun parseEntry(text: String, book: Book, forceId: String? = null): Parsed<Entry> {
        val operating = book.operatingOrNull()
            ?: return Parsed.fail("В книге нет валюты учёта")
        val document = read(text)
        if (document.errors.isNotEmpty()) return Parsed.fail(document.errors)
        if (document.options.isNotEmpty() || document.opens.isNotEmpty() ||
            document.commodities.isNotEmpty() || document.prices.isNotEmpty() ||
            document.closes.isNotEmpty()
        ) {
            return Parsed.fail("Здесь нужна одна проводка, без option и open")
        }
        if (document.transactions.size != 1) {
            return Parsed.fail("В тексте должна быть ровно одна проводка")
        }
        val accounts = book.accounts.associateBy { it.beancountName }
        val commodities = book.commodities.associateBy { it.code }
        val resolved = resolveTxn(
            txn = document.transactions.single(),
            accounts = accounts,
            commodities = commodities,
            operating = operating,
            priceOf = { code, date ->
                val commodity = commodities[code]
                if (commodity == null) null else Ledger.quote(book, commodity.id, date)
            },
            keepId = forceId,
        )
        if (!resolved.ok || resolved.value == null) return resolved
        val problem = Ledger.explain(book, resolved.value)
        return if (problem == null) resolved else Parsed.fail(problem)
    }

    fun render(book: Book): String = buildString {
        val operating = book.operatingOrNull()
        append("; Pacholli — двойная запись\n")
        append("; https://github.com/beancount/beancount\n")
        append("option \"title\" ${escape(book.title.ifBlank { "Pacholli" })}\n")
        if (operating != null) {
            append("option \"operating_currency\" ${escape(operating.code)}\n")
        }
        append('\n')
        val commodities = book.commodities.sortedWith(compareByDescending<Commodity> { it.operating }.thenBy { it.code })
        for (commodity in commodities) {
            append(commodity.openedOn).append(" commodity ").append(commodity.code).append('\n')
            append("  name: ").append(escape(commodity.name)).append('\n')
            append("  id: ").append(escape(commodity.id)).append('\n')
            if (commodity.operating) append("  operating: \"true\"\n")
            append('\n')
        }
        if (operating != null) {
            val quotes = book.quotes.sortedWith(compareBy<Quote> { it.date }.thenBy { it.commodityId })
            for (quote in quotes) {
                val commodity = book.commodity(quote.commodityId) ?: continue
                if (commodity.operating) continue
                append(quote.date).append(" price ").append(commodity.code).append(' ')
                append(Money.render(quote.price)).append(' ').append(operating.code).append('\n')
            }
            if (quotes.any { book.commodity(it.commodityId)?.operating == false }) append('\n')
        }
        for (account in book.accounts.sortedBy { it.beancountName }) {
            append(account.openedOn).append(" open ").append(account.beancountName)
            if (operating != null) append(' ').append(operating.code)
            append('\n')
            append("  name: ").append(escape(account.name)).append('\n')
            append("  type: ").append(escape(typeToken(account.type))).append('\n')
            append("  allows-subaccounts: ").append(escape(account.allowsSubaccounts.toString())).append('\n')
            append("  cash: ").append(escape(account.cash.toString())).append('\n')
            append("  id: ").append(escape(account.id)).append('\n')
            val closed = account.closedOn
            if (closed != null) {
                append(closed).append(" close ").append(account.beancountName).append('\n')
            }
            append('\n')
        }
        for (entry in book.sortedEntries()) {
            append(renderEntry(entry, book))
            append('\n')
        }
    }.trimEnd().plus("\n")

    fun renderEntry(entry: Entry, book: Book): String {
        val operating = book.operatingOrNull()
        val debit = book.account(entry.debit.accountId)
        val credit = book.account(entry.credit.accountId)
        val debitName = debit?.beancountName ?: "Unknown:Debit"
        val creditName = credit?.beancountName ?: "Unknown:Credit"
        val width = maxOf(debitName.length, creditName.length)
        return buildString {
            append(entry.date).append(" *")
            when {
                entry.payee.isNotEmpty() -> {
                    append(' ').append(escape(entry.payee)).append(' ').append(escape(entry.narration))
                }
                entry.narration.isNotEmpty() -> append(' ').append(escape(entry.narration))
            }
            append('\n')
            val time = entry.timeMinutes
            if (time != null) append("  time: ").append(escape(Dates.timeLabel(time))).append('\n')
            append("  id: ").append(escape(entry.id)).append('\n')
            append(postingLine(debitName, width, entry.debit, book, operating))
            append('\n')
            append(postingLine(creditName, width, entry.credit, book, operating))
            append('\n')
        }
    }

    private fun postingLine(
        name: String,
        width: Int,
        posting: Posting,
        book: Book,
        operating: Commodity?,
    ): String {
        val commodity = book.commodity(posting.commodityId)
        val code = commodity?.code ?: "???"
        val signed = if (posting.side == Side.DEBIT) posting.baseAmount else posting.baseAmount.negate()
        val number = if (commodity?.operating == false) {
            if (posting.side == Side.DEBIT) posting.quantity else posting.quantity.negate()
        } else {
            signed
        }
        val rendered = signedNumber(number)
        val cost = if (commodity?.operating == false && operating != null) costSuffix(posting, operating) else null
        val tail = if (cost == null) "$rendered $code" else "$rendered $code $cost"
        return "  ${name.padEnd(width)}  $tail"
    }

    private fun costSuffix(posting: Posting, operating: Commodity): String {
        val per = posting.baseAmount.divide(posting.quantity, Money.SCALE, RoundingMode.HALF_UP)
        val back = Money.s(per.multiply(posting.quantity))
        return if (Money.eq(back, posting.baseAmount)) {
            "{${Money.render(per)} ${operating.code}}"
        } else {
            "@@ ${Money.render(posting.baseAmount)} ${operating.code}"
        }
    }

    private fun signedNumber(value: BigDecimal): String {
        val abs = Money.render(value.abs())
        return if (value.signum() < 0) "-$abs" else abs
    }

    private fun parseDocument(text: String, existing: Book?): Parsed<Book> {
        val document = read(text)
        if (document.errors.isNotEmpty()) return Parsed.fail(document.errors)
        if (existing != null) return Parsed.fail("Внутренняя ошибка разбора")
        val errors = ArrayList<String>()
        var title = "Pacholli"
        var operatingCode: String? = null
        for (option in document.options) {
            when (option.key) {
                "title" -> title = option.value
                "operating_currency" -> {
                    if (!option.value.matches(Names.COMMODITY)) {
                        errors += "Строка ${option.line}: валюта учёта «${option.value}» не похожа на код Beancount"
                    } else {
                        operatingCode = option.value
                    }
                }
                "name_assets" -> expectDefault(option, "Assets", errors)
                "name_liabilities" -> expectDefault(option, "Liabilities", errors)
                "name_equity" -> expectDefault(option, "Equity", errors)
                "name_income" -> expectDefault(option, "Income", errors)
                "name_expenses" -> expectDefault(option, "Expenses", errors)
            }
        }
        if (operatingCode == null) {
            errors += "Нужна директива option \"operating_currency\""
        }
        if (errors.isNotEmpty() || operatingCode == null) return Parsed.fail(errors)

        val commodityDrafts = LinkedHashMap<String, Commodity>()
        for (raw in document.commodities) {
            if (!raw.code.matches(Names.COMMODITY)) {
                errors += "Строка ${raw.line}: некорректный код ${raw.code}"
                continue
            }
            val id = metaId(raw.meta, "commodity", raw.code, errors, raw.line)
            val name = raw.meta["name"]?.takeIf { it.isNotBlank() } ?: raw.code
            val operating = raw.code == operatingCode || truth(raw.meta["operating"]) == true
            if (operating && raw.code != operatingCode) {
                errors += "Строка ${raw.line}: операционная единица ${raw.code} не совпадает с $operatingCode"
            }
            val previous = commodityDrafts[raw.code]
            if (previous != null && previous.id != id) {
                errors += "Строка ${raw.line}: единица ${raw.code} объявлена дважды"
                continue
            }
            commodityDrafts[raw.code] = Commodity(id, raw.code, name, raw.code == operatingCode, raw.date)
        }
        if (commodityDrafts[operatingCode] == null) {
            commodityDrafts[operatingCode] = Commodity(
                id = stable("commodity", operatingCode),
                code = operatingCode,
                name = operatingCode,
                operating = true,
                openedOn = LocalDate.of(2020, 1, 1),
            )
        } else if (!commodityDrafts.getValue(operatingCode).operating) {
            val current = commodityDrafts.getValue(operatingCode)
            commodityDrafts[operatingCode] = current.copy(operating = true)
        }
        for (other in commodityDrafts.values) {
            if (other.code != operatingCode && other.operating) {
                errors += "Единица ${other.code} помечена как валюта учёта"
            }
        }

        val accounts = ArrayList<Account>()
        val accountIds = HashSet<String>()
        for (raw in document.opens) {
            val section = Names.sectionOfRoot(raw.name)
            if (section == null || !raw.name.matches(Names.ACCOUNT)) {
                errors += "Строка ${raw.line}: счёт ${raw.name} должен начинаться с Assets, Liabilities, Equity, Income или Expenses"
                continue
            }
            val id = metaId(raw.meta, "account", raw.name, errors, raw.line)
            if (!accountIds.add(id)) {
                errors += "Строка ${raw.line}: повторяющийся id счёта"
                continue
            }
            if (accounts.any { it.beancountName == raw.name }) {
                errors += "Строка ${raw.line}: счёт ${raw.name} открыт дважды"
                continue
            }
            val type = accountType(raw.meta["type"], section, errors, raw.line) ?: continue
            val allows = truth(raw.meta["allows-subaccounts"]) ?: true
            val cash = truth(raw.meta["cash"]) ?: false
            val display = raw.meta["name"]?.takeIf { it.isNotBlank() } ?: Names.leafOf(raw.name)
            accounts += Account(
                id = id,
                parentId = null,
                name = display,
                beancountName = raw.name,
                type = type,
                section = section,
                allowsSubaccounts = allows,
                cash = cash,
                openedOn = raw.date,
                closedOn = null,
            )
        }
        ensureParents(accounts, accountIds)
        linkParents(accounts)
        for (account in accounts) {
            if (!account.allowsSubaccounts && accounts.any { it.parentId == account.id }) {
                errors += "Счёт ${account.beancountName} не допускает субсчета, но они есть"
            }
        }
        for (raw in document.closes) {
            val account = accounts.find { it.beancountName == raw.name }
            if (account == null) {
                errors += "Строка ${raw.line}: закрывается неизвестный счёт ${raw.name}"
                continue
            }
            val index = accounts.indexOf(account)
            accounts[index] = account.copy(closedOn = raw.date)
        }

        val byCode = HashMap(commodityDrafts)
        fun touchCommodity(code: String, on: LocalDate) {
            if (byCode.containsKey(code)) return
            if (!code.matches(Names.COMMODITY)) return
            byCode[code] = Commodity(stable("commodity", code), code, code, code == operatingCode, on)
        }
        for (raw in document.prices) touchCommodity(raw.code, raw.date)
        for (txn in document.transactions) {
            for (leg in txn.legs) {
                leg.code?.let { touchCommodity(it, txn.date) }
                when (val cost = leg.cost) {
                    is Cost.Per -> touchCommodity(cost.code, txn.date)
                    is Cost.Total -> touchCommodity(cost.code, txn.date)
                    null -> Unit
                }
            }
        }

        val quotes = ArrayList<Quote>()
        val quoteKeys = HashSet<String>()
        val operating = byCode.getValue(operatingCode)
        for (raw in document.prices) {
            if (raw.code == operatingCode) {
                errors += "Строка ${raw.line}: цена валюты учёта не задаётся"
                continue
            }
            if (raw.quote != operatingCode) {
                errors += "Строка ${raw.line}: цена ${raw.code} должна быть в $operatingCode"
                continue
            }
            if (raw.amount.signum() <= 0) {
                errors += "Строка ${raw.line}: цена должна быть больше нуля"
                continue
            }
            val commodity = byCode[raw.code]
            if (commodity == null) {
                errors += "Строка ${raw.line}: неизвестная единица ${raw.code}"
                continue
            }
            val key = commodity.id + "@" + raw.date
            if (!quoteKeys.add(key)) {
                val index = quotes.indexOfFirst { it.commodityId == commodity.id && it.date == raw.date }
                if (index >= 0) quotes[index] = Quote(commodity.id, raw.date, Money.s(raw.amount))
            } else {
                quotes += Quote(commodity.id, raw.date, Money.s(raw.amount))
            }
        }

        val bookSoFar = Book(title, byCode.values.toList(), quotes, accounts.toList(), emptyList())
        val accountsByName = accounts.associateBy { it.beancountName }
        val entries = ArrayList<Entry>()
        val entryIds = HashSet<String>()
        for (txn in document.transactions) {
            val resolved = resolveTxn(
                txn = txn,
                accounts = accountsByName,
                commodities = byCode,
                operating = operating,
                priceOf = { code, date ->
                    val commodity = byCode[code]
                    if (commodity == null) null else Ledger.quote(bookSoFar, commodity.id, date)
                },
                keepId = null,
            )
            if (!resolved.ok || resolved.value == null) {
                errors += resolved.errors
            } else {
                val problem = Ledger.explain(bookSoFar, resolved.value)
                if (problem != null) {
                    errors += "Строка ${txn.line}: $problem"
                } else if (!entryIds.add(resolved.value.id)) {
                    errors += "Строка ${txn.line}: повторяющийся id проводки"
                } else {
                    entries += resolved.value
                }
            }
        }
        if (errors.isNotEmpty()) return Parsed.fail(errors.distinct())
        return Parsed.ok(
            Book(
                title = title,
                commodities = byCode.values.sortedBy { it.code },
                quotes = quotes,
                accounts = accounts.toList(),
                entries = entries,
            ),
        )
    }

    private fun resolveTxn(
        txn: RawTxn,
        accounts: Map<String, Account>,
        commodities: Map<String, Commodity>,
        operating: Commodity,
        priceOf: (String, LocalDate) -> BigDecimal?,
        keepId: String?,
    ): Parsed<Entry> {
        val errors = ArrayList<String>()
        if (txn.legs.size != 2) {
            errors += "Строка ${txn.line}: в проводке два счёта, а не ${txn.legs.size}"
        }
        val elided = txn.legs.count { it.signedQty == null }
        if (elided > 1) errors += "Строка ${txn.line}: пустая сумма может быть только у одного счёта"
        data class Weighted(
            val account: Account,
            val commodity: Commodity,
            val quantity: BigDecimal,
            val weight: BigDecimal,
        )
        val weighted = ArrayList<Weighted>()
        for (leg in txn.legs) {
            val account = accounts[leg.account]
            if (account == null) {
                errors += "Строка ${leg.line}: неизвестный счёт ${leg.account}"
                continue
            }
            if (leg.signedQty == null) continue
            val code = leg.code
            val commodity = if (code == null) null else commodities[code]
            if (commodity == null) {
                errors += "Строка ${leg.line}: неизвестная единица ${code ?: ""}"
                continue
            }
            val weight = weightOf(leg, commodity, operating, txn.date, priceOf, errors) ?: continue
            weighted += Weighted(account, commodity, leg.signedQty.abs(), weight)
        }
        if (elided == 1 && weighted.size == 1 && txn.legs.size == 2) {
            val known = weighted.single()
            val blank = txn.legs.first { it.signedQty == null }
            val account = accounts[blank.account]
            if (account == null) {
                errors += "Строка ${blank.line}: неизвестный счёт ${blank.account}"
            } else {
                val opposite = known.weight.negate()
                weighted += Weighted(account, operating, opposite.abs(), opposite)
            }
        }
        if (errors.isNotEmpty() || weighted.size != 2) {
            if (errors.isEmpty()) errors += "Строка ${txn.line}: проводка не разобрана"
            return Parsed.fail(errors)
        }
        val sum = weighted[0].weight.add(weighted[1].weight)
        if (sum.signum() != 0 && !Money.eq(sum, BigDecimal.ZERO)) {
            return Parsed.fail("Строка ${txn.line}: дебет и кредит не сходятся")
        }
        val debit = weighted.firstOrNull { it.weight.signum() > 0 }
        val credit = weighted.firstOrNull { it.weight.signum() < 0 }
        if (debit == null || credit == null) {
            return Parsed.fail("Строка ${txn.line}: нужны дебет и кредит")
        }
        if (debit.commodity.id == credit.commodity.id && !Money.eq(debit.quantity, credit.quantity)) {
            return Parsed.fail("Строка ${txn.line}: для ${debit.commodity.code} количества должны совпадать")
        }
        val minutes = txn.meta["time"]?.let { raw ->
            parseTime(raw) ?: return Parsed.fail("Строка ${txn.line}: время «$raw»")
        }
        val id = keepId ?: txn.meta["id"]?.takeIf { it.isNotBlank() }
            ?: stable("entry", "${txn.date}|${txn.line}|${txn.payee}|${txn.narration}")
        val entry = Entry(
            id = id,
            date = txn.date,
            timeMinutes = minutes,
            payee = txn.payee,
            narration = txn.narration,
            debit = Posting(
                debit.account.id,
                Side.DEBIT,
                Money.s(debit.quantity),
                debit.commodity.id,
                Money.s(debit.weight.abs()),
            ),
            credit = Posting(
                credit.account.id,
                Side.CREDIT,
                Money.s(credit.quantity),
                credit.commodity.id,
                Money.s(credit.weight.abs()),
            ),
        )
        return Parsed.ok(entry)
    }

    private fun weightOf(
        leg: RawLeg,
        commodity: Commodity,
        operating: Commodity,
        date: LocalDate,
        priceOf: (String, LocalDate) -> BigDecimal?,
        errors: MutableList<String>,
    ): BigDecimal? {
        val signed = leg.signedQty ?: return null
        if (signed.signum() == 0) {
            errors += "Строка ${leg.line}: сумма равна нулю"
            return null
        }
        if (commodity.operating) {
            if (leg.cost != null) {
                errors += "Строка ${leg.line}: у валюты учёта не указывают стоимость в фигурных скобках"
                return null
            }
            return Money.s(signed)
        }
        val total = when (val cost = leg.cost) {
            is Cost.Per -> {
                if (cost.code != operating.code || cost.price.signum() <= 0) {
                    errors += "Строка ${leg.line}: стоимость должна быть положительной и в ${operating.code}"
                    return null
                }
                signed.multiply(cost.price)
            }
            is Cost.Total -> {
                if (cost.code != operating.code || cost.total.signum() <= 0) {
                    errors += "Строка ${leg.line}: стоимость должна быть положительной и в ${operating.code}"
                    return null
                }
                BigDecimal.valueOf(signed.signum().toLong()).multiply(cost.total)
            }
            null -> {
                val price = priceOf(commodity.code, date)
                if (price == null) {
                    errors += "Строка ${leg.line}: нет стоимости ${commodity.code} на $date"
                    return null
                }
                signed.multiply(price)
            }
        }
        return Money.s(total)
    }

    private fun ensureParents(accounts: MutableList<Account>, ids: MutableSet<String>) {
        val byName = accounts.associateBy { it.beancountName }.toMutableMap()
        val pending = ArrayList(accounts)
        for (account in pending) {
            val parts = account.beancountName.split(':')
            for (length in 2 until parts.size) {
                val prefix = parts.subList(0, length).joinToString(":")
                if (byName.containsKey(prefix)) continue
                val section = Names.sectionOfRoot(prefix) ?: continue
                val id = stable("account", prefix)
                if (!ids.add(id)) continue
                val parent = Account(
                    id = id,
                    parentId = null,
                    name = Names.leafOf(prefix),
                    beancountName = prefix,
                    type = section.defaultType,
                    section = section,
                    allowsSubaccounts = true,
                    cash = false,
                    openedOn = account.openedOn,
                    closedOn = null,
                )
                byName[prefix] = parent
                accounts += parent
            }
        }
    }

    private fun linkParents(accounts: MutableList<Account>) {
        val byName = accounts.associateBy { it.beancountName }
        for (index in accounts.indices) {
            val account = accounts[index]
            val parentName = account.beancountName.substringBeforeLast(':')
            val parent = if (parentName.contains(':')) byName[parentName] else null
            accounts[index] = account.copy(parentId = parent?.id)
        }
    }

    private fun read(text: String): RawDocument {
        val errors = ArrayList<String>()
        val lines = normalize(text).split('\n')
        val options = ArrayList<RawOption>()
        val opens = ArrayList<RawOpen>()
        val closes = ArrayList<RawClose>()
        val commodities = ArrayList<RawCommodity>()
        val prices = ArrayList<RawPrice>()
        val transactions = ArrayList<RawTxn>()
        var index = 0
        while (index < lines.size) {
            if (errors.size > 40) break
            val stripped = stripComment(lines[index]).trimEnd()
            val lineNo = index + 1
            if (stripped.isBlank()) {
                index++
                continue
            }
            if (stripped.first().isWhitespace()) {
                errors += "Строка $lineNo: лишний отступ"
                index++
                continue
            }
            val trimmed = stripped.trim()
            if (trimmed.matches(Regex("""\*+($|\s.*)"""))) {
                index++
                continue
            }
            if (trimmed.startsWith("option")) {
                val parsed = parseOption(trimmed, lineNo, errors)
                if (parsed != null) options += parsed
                index++
                continue
            }
            if (UNSUPPORTED.any { trimmed == it || trimmed.startsWith("$it ") }) {
                errors += "Строка $lineNo: директива не поддерживается"
                index++
                continue
            }
            val dated = DATED.matchEntire(trimmed)
            if (dated == null) {
                errors += "Строка $lineNo: не разобрал «${trimmed.take(80)}»"
                index++
                continue
            }
            val date = try {
                LocalDate.parse(dated.groupValues[1])
            } catch (_: DateTimeParseException) {
                errors += "Строка $lineNo: некорректная дата"
                index++
                continue
            }
            val verb = dated.groupValues[2]
            val rest = dated.groupValues[3].trim()
            when (verb) {
                "open" -> {
                    val name = rest.substringBefore(' ').trim().trimEnd(',')
                    val ok = name.matches(Names.ACCOUNT)
                    if (!ok) errors += "Строка $lineNo: счёт «$name»"
                    index++
                    val meta = readMeta(lines, index, errors)
                    index = meta.second
                    if (ok) opens += RawOpen(date, name, meta.first, lineNo)
                }
                "close" -> {
                    val name = rest.trim()
                    if (!name.matches(Names.ACCOUNT)) errors += "Строка $lineNo: счёт «$name»"
                    index++
                    val meta = readMeta(lines, index, errors)
                    index = meta.second
                    closes += RawClose(date, name, lineNo)
                }
                "commodity" -> {
                    val code = rest.trim().substringBefore(' ').trim()
                    index++
                    val meta = readMeta(lines, index, errors)
                    index = meta.second
                    commodities += RawCommodity(date, code, meta.first, lineNo)
                }
                "price" -> {
                    val price = parsePrice(rest, lineNo, errors)
                    if (price != null) prices += price.copy(date = date, line = lineNo)
                    index++
                    val skipped = readMeta(lines, index, errors)
                    index = skipped.second
                }
                "*", "!", "txn", "TXN" -> {
                    val header = parseNarration(rest, lineNo, errors)
                    index++
                    val body = readTxnBody(lines, index, errors)
                    index = body.next
                    transactions += RawTxn(date, header.first, header.second, body.meta, body.legs, lineNo)
                }
                in BALANCE_DIRECTIVES -> {
                    errors += "Строка $lineNo: директива $verb не поддерживается"
                    index++
                }
                else -> {
                    errors += "Строка $lineNo: неизвестная директива $verb"
                    index++
                }
            }
        }
        return RawDocument(options, opens, closes, commodities, prices, transactions, errors)
    }

    private fun readMeta(
        lines: List<String>,
        start: Int,
        errors: MutableList<String>,
    ): Pair<Map<String, String>, Int> {
        val meta = LinkedHashMap<String, String>()
        var index = start
        while (index < lines.size) {
            val stripped = stripComment(lines[index]).trimEnd()
            if (stripped.isBlank()) break
            if (!stripped.first().isWhitespace()) break
            val body = stripped.trim()
            val match = META.matchEntire(body)
            if (match == null) {
                errors += "Строка ${index + 1}: не метаданные и не проводка"
                index++
                continue
            }
            meta[match.groupValues[1]] = metaValue(match.groupValues[2])
            index++
        }
        return meta to index
    }

    private fun readTxnBody(lines: List<String>, start: Int, errors: MutableList<String>): TxnBody {
        val meta = LinkedHashMap<String, String>()
        val legs = ArrayList<RawLeg>()
        var index = start
        while (index < lines.size) {
            val stripped = stripComment(lines[index]).trimEnd()
            if (stripped.isBlank()) break
            if (!stripped.first().isWhitespace()) break
            val body = stripped.trim()
            val match = META.matchEntire(body)
            if (match != null) {
                meta[match.groupValues[1]] = metaValue(match.groupValues[2])
            } else {
                parseLeg(body, index + 1, errors)?.let { legs += it }
            }
            index++
        }
        return TxnBody(meta, legs, index)
    }

    private fun parseLeg(body: String, line: Int, errors: MutableList<String>): RawLeg? {
        val split = ACCOUNT_HEAD.matchEntire(body)
        if (split == null) {
            errors += "Строка $line: проводка «${body.take(80)}»"
            return null
        }
        val account = split.groupValues[1]
        if (!account.matches(Names.ACCOUNT)) {
            errors += "Строка $line: счёт «$account»"
            return null
        }
        val rest = split.groupValues[2].trim()
        if (rest.isEmpty()) return RawLeg(account, null, null, null, line)
        val amount = AMOUNT_HEAD.matchEntire(rest)
        if (amount == null) {
            errors += "Строка $line: сумма «${rest.take(80)}»"
            return null
        }
        val signed = Money.parse(amount.groupValues[1])
        if (signed == null) {
            errors += "Строка $line: сумма «${amount.groupValues[1]}»"
            return null
        }
        val code = amount.groupValues[2]
        if (!code.matches(Names.COMMODITY)) {
            errors += "Строка $line: единица «$code»"
            return null
        }
        val tail = amount.groupValues[3].trim()
        val cost = when {
            tail.isEmpty() -> null
            tail.startsWith("@@") -> parseTotal(tail.removePrefix("@@").trim(), line, errors)
            tail.startsWith("@") -> parsePer(tail.removePrefix("@").trim(), line, errors)
            tail.startsWith("{") && tail.endsWith("}") -> {
                if (tail.contains(',')) {
                    errors += "Строка $line: дата внутри стоимости не поддерживается"
                    null
                } else {
                    parsePer(tail.removePrefix("{").removeSuffix("}").trim(), line, errors)
                }
            }
            else -> {
                errors += "Строка $line: стоимость «${tail.take(40)}»"
                null
            }
        }
        if (tail.isNotEmpty() && cost == null) return null
        return RawLeg(account, signed, code, cost, line)
    }

    private fun parsePer(body: String, line: Int, errors: MutableList<String>): Cost.Per? {
        val match = PRICE_BODY.matchEntire(body) ?: run {
            errors += "Строка $line: стоимость «$body»"
            return null
        }
        val amount = Money.parse(match.groupValues[1]) ?: return null
        return Cost.Per(amount, match.groupValues[2])
    }

    private fun parseTotal(body: String, line: Int, errors: MutableList<String>): Cost.Total? {
        val match = PRICE_BODY.matchEntire(body) ?: run {
            errors += "Строка $line: стоимость «$body»"
            return null
        }
        val amount = Money.parse(match.groupValues[1]) ?: return null
        return Cost.Total(amount.abs(), match.groupValues[2])
    }

    private fun parsePrice(rest: String, line: Int, errors: MutableList<String>): RawPrice? {
        val match = PRICE_LINE.matchEntire(rest) ?: run {
            errors += "Строка $line: цена «$rest»"
            return null
        }
        val amount = Money.parse(match.groupValues[2])
        if (amount == null) {
            errors += "Строка $line: цена"
            return null
        }
        return RawPrice(LocalDate.EPOCH, match.groupValues[1], amount, match.groupValues[3], line)
    }

    private fun parseOption(text: String, line: Int, errors: MutableList<String>): RawOption? {
        val strings = readStrings(text.removePrefix("option").trim(), line, errors) ?: return null
        if (strings.size != 2) {
            errors += "Строка $line: option ждёт два значения в кавычках"
            return null
        }
        return RawOption(strings[0], strings[1], line)
    }

    private fun parseNarration(rest: String, line: Int, errors: MutableList<String>): Pair<String, String> {
        if (rest.isBlank()) return "" to ""
        val strings = ArrayList<String>()
        var index = 0
        while (index < rest.length) {
            while (index < rest.length && rest[index].isWhitespace()) index++
            if (index >= rest.length) break
            when (rest[index]) {
                '"' -> {
                    val parsed = scanString(rest, index) ?: run {
                        errors += "Строка $line: незакрытая кавычка"
                        return "" to ""
                    }
                    strings += parsed.first
                    index = parsed.second
                }
                '#', '^' -> {
                    while (index < rest.length && !rest[index].isWhitespace()) index++
                }
                else -> {
                    errors += "Строка $line: лишний текст «${rest.substring(index).take(40)}»"
                    return "" to ""
                }
            }
        }
        return when (strings.size) {
            0 -> "" to ""
            1 -> "" to strings[0]
            2 -> strings[0] to strings[1]
            else -> {
                errors += "Строка $line: у проводки не больше двух строк — получатель и описание"
                "" to ""
            }
        }
    }

    private fun readStrings(text: String, line: Int, errors: MutableList<String>): List<String>? {
        val strings = ArrayList<String>()
        var index = 0
        while (index < text.length) {
            while (index < text.length && text[index].isWhitespace()) index++
            if (index >= text.length) break
            if (text[index] != '"') {
                errors += "Строка $line: ожидалась кавычка"
                return null
            }
            val parsed = scanString(text, index) ?: run {
                errors += "Строка $line: незакрытая кавычка"
                return null
            }
            strings += parsed.first
            index = parsed.second
        }
        return strings
    }

    private fun scanString(text: String, start: Int): Pair<String, Int>? {
        val out = StringBuilder()
        var index = start + 1
        while (index < text.length) {
            val ch = text[index]
            if (ch == '\\') {
                if (index + 1 >= text.length) return null
                when (val escaped = text[index + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(escaped)
                }
                index += 2
                continue
            }
            if (ch == '"') return out.toString() to index + 1
            out.append(ch)
            index++
        }
        return null
    }

    private fun metaValue(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("\"")) {
            return scanString(trimmed, 0)?.first ?: trimmed.trim('"')
        }
        return trimmed
    }

    private fun metaId(
        meta: Map<String, String>,
        kind: String,
        key: String,
        errors: MutableList<String>,
        line: Int,
    ): String {
        val id = meta["id"]?.trim().orEmpty()
        if (id.isEmpty()) return stable(kind, key)
        if (id.any { it.isWhitespace() }) {
            errors += "Строка $line: id не должен содержать пробелы"
            return stable(kind, key)
        }
        return id
    }

    private fun accountType(
        raw: String?,
        section: AccountSection,
        errors: MutableList<String>,
        line: Int,
    ): AccountType? {
        if (raw.isNullOrBlank()) return section.defaultType
        return when (raw.trim().lowercase()) {
            "active", "активный" -> AccountType.ACTIVE
            "passive", "пассивный" -> AccountType.PASSIVE
            "active-passive", "active_passive", "активно-пассивный" -> AccountType.ACTIVE_PASSIVE
            else -> {
                errors += "Строка $line: тип счёта «$raw»"
                null
            }
        }
    }

    private fun truth(raw: String?): Boolean? = when (raw?.trim()?.lowercase()) {
        null, "" -> null
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> null
    }

    private fun expectDefault(option: RawOption, expected: String, errors: MutableList<String>) {
        if (option.value != expected) {
            errors += "Строка ${option.line}: ${option.key} можно оставить только $expected"
        }
    }

    private fun parseTime(raw: String): Int? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").matchEntire(raw.trim()) ?: return null
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun typeToken(type: AccountType): String = when (type) {
        AccountType.ACTIVE -> "active"
        AccountType.PASSIVE -> "passive"
        AccountType.ACTIVE_PASSIVE -> "active-passive"
    }

    private fun escape(text: String): String = buildString {
        append('"')
        for (ch in text) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                else -> append(ch)
            }
        }
        append('"')
    }

    private fun normalize(text: String): String = text
        .trimStart('\uFEFF')
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    private fun stripComment(line: String): String {
        var inString = false
        var escaped = false
        for (index in line.indices) {
            val ch = line[index]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
            } else if (ch == '"') {
                inString = true
            } else if (ch == ';') {
                return line.substring(0, index)
            }
        }
        return line
    }

    private fun stable(kind: String, key: String): String =
        UUID.nameUUIDFromBytes("$kind:$key".toByteArray(StandardCharsets.UTF_8)).toString()

    private val DATED = Regex("""^(\d{4}-\d{2}-\d{2})\s+(\S+)\s*(.*)$""")
    private val META = Regex("""^([a-z][A-Za-z0-9_-]*):\s*(.*)$""")
    private val ACCOUNT_HEAD = Regex("""^([A-Z][A-Za-z0-9-]*(?::[A-Z][A-Za-z0-9-]*)+)\s*(.*)$""")
    private val AMOUNT_HEAD = Regex("""^(-?\d[\d,]*(?:\.\d+)?)\s+([A-Z][A-Z0-9'._-]*)\s*(.*)$""")
    private val PRICE_BODY = Regex("""^(-?\d[\d,]*(?:\.\d+)?)\s+([A-Z][A-Z0-9'._-]*)$""")
    private val PRICE_LINE = Regex("""^([A-Z][A-Z0-9'._-]*)\s+(-?\d[\d,]*(?:\.\d+)?)\s+([A-Z][A-Z0-9'._-]*)$""")
    private val UNSUPPORTED = listOf("include", "plugin", "pushtag", "poptag")
    private val BALANCE_DIRECTIVES = setOf("balance", "pad", "note", "document", "event", "query", "custom")

    private data class RawOption(val key: String, val value: String, val line: Int)
    private data class RawOpen(val date: LocalDate, val name: String, val meta: Map<String, String>, val line: Int)
    private data class RawClose(val date: LocalDate, val name: String, val line: Int)
    private data class RawCommodity(val date: LocalDate, val code: String, val meta: Map<String, String>, val line: Int)
    private data class RawPrice(
        val date: LocalDate,
        val code: String,
        val amount: BigDecimal,
        val quote: String,
        val line: Int,
    )
    private data class RawLeg(
        val account: String,
        val signedQty: BigDecimal?,
        val code: String?,
        val cost: Cost?,
        val line: Int,
    )
    private data class RawTxn(
        val date: LocalDate,
        val payee: String,
        val narration: String,
        val meta: Map<String, String>,
        val legs: List<RawLeg>,
        val line: Int,
    )
    private data class RawDocument(
        val options: List<RawOption>,
        val opens: List<RawOpen>,
        val closes: List<RawClose>,
        val commodities: List<RawCommodity>,
        val prices: List<RawPrice>,
        val transactions: List<RawTxn>,
        val errors: List<String>,
    )
    private data class TxnBody(val meta: Map<String, String>, val legs: List<RawLeg>, val next: Int)
    private sealed class Cost {
        data class Per(val price: BigDecimal, val code: String) : Cost()
        data class Total(val total: BigDecimal, val code: String) : Cost()
    }
}
