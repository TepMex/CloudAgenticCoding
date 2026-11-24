package com.tepmex.pacholli.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tepmex.pacholli.data.Repository
import com.tepmex.pacholli.domain.Account
import com.tepmex.pacholli.domain.AccountDraft
import com.tepmex.pacholli.domain.AccountSection
import com.tepmex.pacholli.domain.AccountType
import com.tepmex.pacholli.domain.Beancount
import com.tepmex.pacholli.domain.Book
import com.tepmex.pacholli.domain.Chart
import com.tepmex.pacholli.domain.CommodityDraft
import com.tepmex.pacholli.domain.Dates
import com.tepmex.pacholli.domain.Entry
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.domain.Money
import com.tepmex.pacholli.domain.Pad
import com.tepmex.pacholli.domain.PadKey
import com.tepmex.pacholli.domain.Parsed
import com.tepmex.pacholli.domain.Side
import com.tepmex.pacholli.domain.Span
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Draft(
    val id: String,
    val date: LocalDate,
    val minutes: Int?,
    val payee: String,
    val narration: String,
    val debitAccountId: String?,
    val creditAccountId: String?,
    val debitQty: BigDecimal,
    val debitCommodityId: String,
    val creditQty: BigDecimal,
    val creditCommodityId: String,
    val prices: Map<String, BigDecimal>,
    val priceText: Map<String, String>,
    val linked: Boolean,
    val textMode: Boolean,
    val text: String,
    val expression: String,
    val focus: Side,
    val error: String?,
    val isNew: Boolean,
)

data class AccountForm(
    val id: String,
    val isNew: Boolean,
    val name: String,
    val leaf: String,
    val leafTouched: Boolean,
    val parentId: String?,
    val type: AccountType,
    val section: AccountSection,
    val allowsSubaccounts: Boolean,
    val cash: Boolean,
    val openedOn: LocalDate,
    val closedOn: LocalDate?,
    val error: String?,
)

data class UnitForm(
    val id: String,
    val isNew: Boolean,
    val code: String,
    val name: String,
    val operating: Boolean,
    val openedOn: LocalDate,
    val priceText: String,
    val priceDate: LocalDate,
    val error: String?,
)

class PacholliViewModel(private val repository: Repository) : ViewModel() {
    val book: StateFlow<Book> = repository.book
    val ready: StateFlow<Boolean> = repository.ready

    private val anchorState = MutableStateFlow(LocalDate.now())
    val anchor: StateFlow<LocalDate> = anchorState.asStateFlow()
    private val spanState = MutableStateFlow(Span.DAY)
    val span: StateFlow<Span> = spanState.asStateFlow()

    private val draftState = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = draftState.asStateFlow()
    private val accountState = MutableStateFlow<AccountForm?>(null)
    val accountForm: StateFlow<AccountForm?> = accountState.asStateFlow()
    private val unitState = MutableStateFlow<UnitForm?>(null)
    val unitForm: StateFlow<UnitForm?> = unitState.asStateFlow()

    private val messagesState = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = messagesState.asSharedFlow()
    private val closedState = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val closed: SharedFlow<Unit> = closedState.asSharedFlow()

    init {
        viewModelScope.launch { repository.load() }
    }

    fun setSpan(span: Span) {
        spanState.value = span
    }

    fun shift(direction: Int) {
        anchorState.value = Dates.shift(anchorState.value, spanState.value, direction)
    }

    fun today() {
        anchorState.value = LocalDate.now()
    }

    fun beginEntry(existing: Entry?) {
        val current = book.value
        val operating = current.operatingOrNull() ?: return
        if (existing == null) {
            val now = LocalTime.now()
            val fresh = Draft(
                id = UUID.randomUUID().toString(),
                date = anchorState.value,
                minutes = now.hour * 60 + now.minute,
                payee = "",
                narration = "",
                debitAccountId = null,
                creditAccountId = null,
                debitQty = BigDecimal.ZERO,
                debitCommodityId = operating.id,
                creditQty = BigDecimal.ZERO,
                creditCommodityId = operating.id,
                prices = emptyMap(),
                priceText = emptyMap(),
                linked = true,
                textMode = false,
                text = "",
                expression = "0",
                focus = Side.DEBIT,
                error = null,
                isNew = true,
            )
            draftState.value = fresh.copy(text = rendered(fresh, current))
        } else {
            draftState.value = fromEntry(existing, current).copy(isNew = false, textMode = false)
        }
    }

    fun confirmAmount() {
        val current = draftState.value ?: return
        val ledger = book.value
        val value = Pad.eval(current.expression)
        if (value == null || value.signum() <= 0) {
            draftState.value = current.copy(error = "Сумма должна быть больше нуля")
            return
        }
        draftState.value = applyQty(current, value, ledger)
        saveEntry()
    }

    fun pad(key: PadKey) {
        val current = draftState.value ?: return
        val ledger = book.value
        val expression = Pad.apply(current.expression, key)
        val value = Pad.eval(expression)
        var next = current.copy(expression = expression, error = null)
        if (value != null) next = applyQty(next, value, ledger)
        draftState.value = next.copy(text = rendered(next, ledger))
    }

    fun focus(side: Side) {
        val current = draftState.value ?: return
        val qty = if (side == Side.DEBIT) current.debitQty else current.creditQty
        draftState.value = current.copy(focus = side, expression = qtyExpression(qty), error = null)
    }

    fun setAccount(side: Side, accountId: String) {
        val current = draftState.value ?: return
        val next = if (side == Side.DEBIT) {
            current.copy(debitAccountId = accountId, error = null)
        } else {
            current.copy(creditAccountId = accountId, error = null)
        }
        draftState.value = next.copy(text = rendered(next, book.value))
    }

    fun setCommodity(side: Side, commodityId: String) {
        val current = draftState.value ?: return
        val ledger = book.value
        val operating = ledger.operatingOrNull() ?: return
        var next = if (side == Side.DEBIT) {
            current.copy(debitCommodityId = commodityId)
        } else {
            current.copy(creditCommodityId = commodityId)
        }
        next = rememberPrice(next, commodityId, ledger)
        if (side == Side.CREDIT) {
            val link = commodityId == operating.id || commodityId == next.debitCommodityId
            next = next.copy(linked = link)
        } else if (next.linked && commodityId != next.creditCommodityId) {
            next = next.copy(creditCommodityId = operating.id)
        }
        next = relink(next, ledger)
        if (next.focus == side) {
            val qty = if (side == Side.DEBIT) next.debitQty else next.creditQty
            next = next.copy(expression = qtyExpression(qty))
        }
        draftState.value = next.copy(text = rendered(next, ledger), error = null)
    }

    fun setPriceText(commodityId: String, text: String) {
        val current = draftState.value ?: return
        val ledger = book.value
        var next = current.copy(priceText = current.priceText + (commodityId to text), error = null)
        val parsed = Money.parseUser(text)
        if (parsed != null && parsed.signum() > 0) {
            next = relink(next.copy(prices = next.prices + (commodityId to parsed)), ledger)
        }
        draftState.value = next.copy(text = rendered(next, ledger))
    }

    fun toggleLink() {
        val current = draftState.value ?: return
        val ledger = book.value
        val next = if (current.linked) {
            current.copy(linked = false)
        } else {
            relink(current.copy(linked = true, creditCommodityId = ledger.operatingOrNull()?.id ?: current.creditCommodityId), ledger)
        }
        draftState.value = next.copy(text = rendered(next, ledger))
    }

    fun setDate(date: LocalDate) {
        update { it.copy(date = date) }
    }

    fun shiftDate(days: Int) {
        update { it.copy(date = it.date.plusDays(days.toLong())) }
    }

    fun setMinutes(minutes: Int) {
        update { it.copy(minutes = minutes) }
    }

    fun setPayee(payee: String) {
        update { it.copy(payee = payee) }
    }

    fun setNarration(narration: String) {
        update { it.copy(narration = narration) }
    }

    fun toggleText() {
        val current = draftState.value ?: return
        val ledger = book.value
        if (current.textMode) {
            val parsed = Beancount.parseEntry(current.text, ledger, current.id)
            val next = if (parsed.value != null) {
                fromEntry(parsed.value, ledger).copy(isNew = current.isNew, textMode = false, error = null)
            } else {
                current.copy(textMode = false, text = rendered(current, ledger), error = null)
            }
            draftState.value = next
        } else {
            val text = rendered(current, ledger)
            draftState.value = current.copy(textMode = true, text = text, error = null)
        }
    }

    fun setText(text: String) {
        val current = draftState.value ?: return
        val parsed = Beancount.parseEntry(text, book.value, current.id)
        val error = parsed.errors.firstOrNull()
        val filled = if (parsed.value != null) {
            fromEntry(parsed.value, book.value).copy(
                isNew = current.isNew,
                textMode = true,
                text = text,
                error = null,
                linked = false,
            )
        } else {
            current.copy(text = text, textMode = true, error = error)
        }
        draftState.value = filled
    }

    fun saveEntry() {
        val current = draftState.value ?: return
        val ledger = book.value
        val parsed = if (current.textMode) {
            Beancount.parseEntry(current.text, ledger, current.id)
        } else {
            val value = Pad.eval(current.expression)
            val withQty = if (value != null && value.signum() > 0) applyQty(current, value, ledger) else current
            compile(withQty, ledger)
        }
        if (!parsed.ok || parsed.value == null) {
            draftState.value = current.copy(error = parsed.errors.firstOrNull() ?: "Проводку нельзя сохранить")
            return
        }
        viewModelScope.launch {
            val error = repository.saveEntry(parsed.value)
            if (error == null) {
                closedState.emit(Unit)
            } else {
                draftState.value = draftState.value?.copy(error = error)
            }
        }
    }

    fun deleteEntry() {
        val id = draftState.value?.id ?: return
        viewModelScope.launch {
            repository.deleteEntry(id)
            closedState.emit(Unit)
        }
    }

    fun beginAccount(existingId: String?, parentId: String?) {
        val ledger = book.value
        val existing = existingId?.let { ledger.account(it) }
        if (existing == null) {
            val parent = parentId?.let { ledger.account(it) }
            val section = parent?.section ?: AccountSection.ASSET
            accountState.value = AccountForm(
                id = UUID.randomUUID().toString(),
                isNew = true,
                name = "",
                leaf = "Account",
                leafTouched = false,
                parentId = parent?.id,
                type = section.defaultType,
                section = section,
                allowsSubaccounts = true,
                cash = false,
                openedOn = LocalDate.now(),
                closedOn = null,
                error = null,
            )
        } else {
            val draft = Chart.draftOf(existing)
            accountState.value = AccountForm(
                id = existing.id,
                isNew = false,
                name = draft.name,
                leaf = draft.leaf,
                leafTouched = true,
                parentId = draft.parentId,
                type = draft.type,
                section = draft.section,
                allowsSubaccounts = draft.allowsSubaccounts,
                cash = draft.cash,
                openedOn = draft.openedOn,
                closedOn = draft.closedOn,
                error = null,
            )
        }
    }

    fun updateAccount(transform: (AccountForm) -> AccountForm) {
        val current = accountState.value ?: return
        accountState.value = transform(current).copy(error = null)
    }

    fun saveAccount() {
        val form = accountState.value ?: return
        val leaf = if (form.isNew && !form.leafTouched) NamesLeaf(form.name) else form.leaf
        val draft = AccountDraft(
            id = form.id,
            parentId = form.parentId,
            name = form.name,
            leaf = leaf,
            type = form.type,
            section = form.section,
            allowsSubaccounts = form.allowsSubaccounts,
            cash = form.cash,
            openedOn = form.openedOn,
            closedOn = form.closedOn,
        )
        viewModelScope.launch {
            val error = repository.saveAccount(draft)
            if (error == null) closedState.emit(Unit) else accountState.value = form.copy(error = error)
        }
    }

    fun deleteAccount() {
        val id = accountState.value?.id ?: return
        viewModelScope.launch {
            val error = repository.deleteAccount(id)
            if (error == null) closedState.emit(Unit) else messagesState.emit(error)
        }
    }

    fun beginUnit(existingId: String?) {
        val ledger = book.value
        val existing = existingId?.let { ledger.commodity(it) }
        if (existing == null) {
            unitState.value = UnitForm(
                id = UUID.randomUUID().toString(),
                isNew = true,
                code = "",
                name = "",
                operating = false,
                openedOn = LocalDate.now(),
                priceText = "",
                priceDate = LocalDate.now(),
                error = null,
            )
        } else {
            val latest = ledger.quotes.filter { it.commodityId == existing.id }.maxByOrNull { it.date }
            unitState.value = UnitForm(
                id = existing.id,
                isNew = false,
                code = existing.code,
                name = existing.name,
                operating = existing.operating,
                openedOn = existing.openedOn,
                priceText = latest?.let { Money.render(it.price) } ?: "",
                priceDate = latest?.date ?: LocalDate.now(),
                error = null,
            )
        }
    }

    fun updateUnit(transform: (UnitForm) -> UnitForm) {
        val current = unitState.value ?: return
        unitState.value = transform(current).copy(error = null)
    }

    fun saveUnit() {
        val form = unitState.value ?: return
        val price = Money.parseUser(form.priceText)
        if (!form.operating && form.priceText.isNotBlank() && (price == null || price.signum() <= 0)) {
            unitState.value = form.copy(error = "Стоимость должна быть больше нуля")
            return
        }
        val draft = CommodityDraft(
            id = form.id,
            code = form.code.trim().uppercase(),
            name = form.name,
            openedOn = form.openedOn,
        )
        viewModelScope.launch {
            val error = repository.saveCommodity(draft, price, form.priceDate)
            if (error == null) closedState.emit(Unit) else unitState.value = form.copy(error = error)
        }
    }

    fun deleteUnit() {
        val id = unitState.value?.id ?: return
        viewModelScope.launch {
            val error = repository.deleteCommodity(id)
            if (error == null) closedState.emit(Unit) else messagesState.emit(error)
        }
    }

    fun deleteQuote(commodityId: String, date: LocalDate) {
        viewModelScope.launch { repository.deleteQuote(commodityId, date) }
    }

    fun setTitle(title: String) {
        viewModelScope.launch { repository.setTitle(title) }
    }

    fun importBook(text: String): Parsed<Book> = Beancount.parse(text)

    fun replaceBook(imported: Book) {
        viewModelScope.launch {
            repository.replace(imported)
            messagesState.emit("Книга загружена")
            closedState.emit(Unit)
        }
    }

    fun exportText(): String = Beancount.render(book.value)

    private fun update(transform: (Draft) -> Draft) {
        val current = draftState.value ?: return
        val next = transform(current).copy(error = null)
        draftState.value = next.copy(text = if (next.textMode) next.text else rendered(next, book.value))
    }

    private fun applyQty(draft: Draft, qty: BigDecimal, ledger: Book): Draft {
        val next = if (draft.focus == Side.DEBIT) {
            relink(draft.copy(debitQty = qty), ledger)
        } else {
            draft.copy(creditQty = qty, linked = false)
        }
        return next
    }

    private fun relink(draft: Draft, ledger: Book): Draft {
        if (!draft.linked) return draft
        val operating = ledger.operatingOrNull() ?: return draft
        val commodity = ledger.commodity(draft.debitCommodityId) ?: return draft
        val price = priceFor(draft, commodity.id, ledger)
        val base = Money.s(draft.debitQty.multiply(if (commodity.operating) BigDecimal.ONE else price))
        return when {
            draft.creditCommodityId == draft.debitCommodityId -> draft.copy(creditQty = draft.debitQty)
            draft.creditCommodityId == operating.id -> draft.copy(creditQty = if (base.signum() > 0) base else draft.creditQty)
            else -> draft
        }
    }

    private fun rememberPrice(draft: Draft, commodityId: String, ledger: Book): Draft {
        if (draft.prices.containsKey(commodityId)) return draft
        val quote = Ledger.quote(ledger, commodityId, draft.date) ?: return draft
        if (ledger.commodity(commodityId)?.operating == true) return draft
        return draft.copy(
            prices = draft.prices + (commodityId to quote),
            priceText = draft.priceText + (commodityId to Money.render(quote)),
        )
    }

    private fun compile(draft: Draft, ledger: Book): Parsed<Entry> {
        val operating = ledger.operatingOrNull() ?: return Parsed.fail("Нет валюты учёта")
        return Ledger.buildEntry(
            book = ledger,
            id = draft.id,
            date = draft.date,
            minutes = draft.minutes,
            payee = draft.payee,
            narration = draft.narration,
            debitAccountId = draft.debitAccountId,
            creditAccountId = draft.creditAccountId,
            debitQty = draft.debitQty,
            debitCommodityId = draft.debitCommodityId,
            creditQty = draft.creditQty,
            creditCommodityId = draft.creditCommodityId,
            debitPrice = priceFor(draft, draft.debitCommodityId, ledger),
            creditPrice = priceFor(draft, draft.creditCommodityId, ledger).let { price ->
                if (draft.creditCommodityId == operating.id) BigDecimal.ONE else price
            },
        )
    }

    private fun rendered(draft: Draft, ledger: Book): String {
        val parsed = compile(draft, ledger)
        return if (parsed.value != null) Beancount.renderEntry(parsed.value, ledger).trimEnd() else ""
    }

    private fun fromEntry(entry: Entry, ledger: Book): Draft {
        val prices = HashMap<String, BigDecimal>()
        val texts = HashMap<String, String>()
        for (posting in listOf(entry.debit, entry.credit)) {
            val commodity = ledger.commodity(posting.commodityId) ?: continue
            if (commodity.operating || posting.quantity.signum() == 0) continue
            val price = Money.s(posting.baseAmount.divide(posting.quantity, Money.SCALE, java.math.RoundingMode.HALF_UP))
            prices[commodity.id] = price
            texts[commodity.id] = Money.render(price)
        }
        return Draft(
            id = entry.id,
            date = entry.date,
            minutes = entry.timeMinutes,
            payee = entry.payee,
            narration = entry.narration,
            debitAccountId = entry.debit.accountId,
            creditAccountId = entry.credit.accountId,
            debitQty = entry.debit.quantity,
            debitCommodityId = entry.debit.commodityId,
            creditQty = entry.credit.quantity,
            creditCommodityId = entry.credit.commodityId,
            prices = prices,
            priceText = texts,
            linked = false,
            textMode = false,
            text = Beancount.renderEntry(entry, ledger).trimEnd(),
            expression = qtyExpression(entry.debit.quantity),
            focus = Side.DEBIT,
            error = null,
            isNew = false,
        )
    }

    private fun priceFor(draft: Draft, commodityId: String, ledger: Book): BigDecimal {
        val commodity = ledger.commodity(commodityId)
        if (commodity == null || commodity.operating) return BigDecimal.ONE
        return draft.prices[commodityId] ?: Ledger.quote(ledger, commodityId, draft.date) ?: BigDecimal.ZERO
    }

    private fun qtyExpression(qty: BigDecimal): String {
        if (qty.signum() == 0) return "0"
        val plain = Money.s(qty).stripTrailingZeros()
        val fixed = if (plain.scale() < 0) plain.setScale(0) else plain
        return fixed.toPlainString()
    }

    private fun NamesLeaf(name: String): String = com.tepmex.pacholli.domain.Names.leafFromDisplay(name)

    companion object {
        fun factory(repository: Repository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PacholliViewModel(repository) as T
            }
    }
}

fun Account.shortType(): String = when (type) {
    AccountType.ACTIVE -> "активный"
    AccountType.PASSIVE -> "пассивный"
    AccountType.ACTIVE_PASSIVE -> "активно-пассивный"
}
