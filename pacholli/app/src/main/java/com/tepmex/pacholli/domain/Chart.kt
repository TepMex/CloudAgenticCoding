package com.tepmex.pacholli.domain

import java.time.LocalDate

data class AccountDraft(
    val id: String,
    val parentId: String?,
    val name: String,
    val leaf: String,
    val type: AccountType,
    val section: AccountSection,
    val allowsSubaccounts: Boolean,
    val cash: Boolean,
    val openedOn: LocalDate,
    val closedOn: LocalDate?,
)

data class CommodityDraft(
    val id: String,
    val code: String,
    val name: String,
    val openedOn: LocalDate,
)

object Chart {
    fun draftOf(account: Account): AccountDraft = AccountDraft(
        id = account.id,
        parentId = account.parentId,
        name = account.name,
        leaf = Names.leafOf(account.beancountName),
        type = account.type,
        section = account.section,
        allowsSubaccounts = account.allowsSubaccounts,
        cash = account.cash,
        openedOn = account.openedOn,
        closedOn = account.closedOn,
    )

    fun checkAccount(book: Book, draft: AccountDraft): String? {
        val name = draft.name.trim()
        if (name.isEmpty()) return "Введите название"
        if (!draft.leaf.matches(Names.LEAF)) {
            return "Имя Beancount: с заглавной буквы, латиница, цифры и дефис"
        }
        val parent = draft.parentId?.let { book.account(it) }
        if (draft.parentId != null && parent == null) return "Родительский счёт не найден"
        if (parent != null && parent.section != draft.section) {
            return "Субсчёт остаётся в разделе «${parent.section.title}»"
        }
        if (parent != null && !parent.allowsSubaccounts && book.account(draft.id)?.parentId != parent.id) {
            return "Счёт «${parent.name}» не допускает субсчета"
        }
        if (draft.parentId == draft.id) return "Счёт не может быть родителем самого себя"
        if (draft.parentId != null && isUnder(book, draft.parentId, draft.id)) {
            return "Нельзя перенести счёт внутрь собственного субсчёта"
        }
        val full = Names.fullName(draft.section, parent, draft.leaf)
        if (!full.matches(Names.ACCOUNT)) return "Некорректное имя Beancount"
        val clash = book.accounts.find { it.beancountName == full && it.id != draft.id }
        if (clash != null) return "Имя $full уже занято счётом «${clash.name}»"
        if (!draft.allowsSubaccounts && book.hasChildren(draft.id)) {
            return "Сначала удалите субсчета"
        }
        val closed = draft.closedOn
        if (closed != null && closed.isBefore(draft.openedOn)) return "Дата закрытия раньше даты открытия"
        val existing = book.account(draft.id)
        if (existing != null && existing.section != draft.section && hasHistory(book, draft.id)) {
            return "Раздел нельзя сменить: по счёту уже есть проводки"
        }
        val dates = postingDates(book, draft.id)
        if (dates.any { it.isBefore(draft.openedOn) }) {
            return "Есть проводка раньше даты открытия"
        }
        if (closed != null && dates.any { !it.isBefore(closed) }) {
            return "Есть проводка в день закрытия или позже"
        }
        return null
    }

    fun commitAccount(book: Book, draft: AccountDraft): Book {
        val parent = draft.parentId?.let { book.account(it) }
        val full = Names.fullName(draft.section, parent, draft.leaf)
        val previous = book.account(draft.id)
        val saved = Account(
            id = draft.id,
            parentId = draft.parentId,
            name = draft.name.trim(),
            beancountName = full,
            type = draft.type,
            section = draft.section,
            allowsSubaccounts = draft.allowsSubaccounts,
            cash = draft.cash,
            openedOn = draft.openedOn,
            closedOn = draft.closedOn,
        )
        var accounts = if (previous == null) {
            book.accounts + saved
        } else {
            book.accounts.map { if (it.id == saved.id) saved else it }
        }
        if (previous != null && previous.beancountName != full) {
            val prefix = previous.beancountName
            accounts = accounts.map { account ->
                if (account.id == saved.id) account
                else if (account.beancountName == prefix || account.beancountName.startsWith("$prefix:")) {
                    account.copy(
                        beancountName = full + account.beancountName.removePrefix(prefix),
                        section = draft.section,
                    )
                } else {
                    account
                }
            }
        }
        return book.copy(accounts = accounts)
    }

    fun deleteAccount(book: Book, id: String): String? {
        val account = book.account(id) ?: return "Счёт не найден"
        if (book.hasChildren(id)) return "Сначала удалите субсчета «${account.name}»"
        if (hasHistory(book, id)) return "По счёту «${account.name}» есть проводки"
        return null
    }

    fun checkCommodity(book: Book, draft: CommodityDraft): String? {
        val code = draft.code.trim()
        if (!code.matches(Names.COMMODITY)) {
            return "Код единицы: с заглавной латинской буквы, до 23 символов"
        }
        if (draft.name.trim().isEmpty()) return "Введите название"
        val clash = book.commodities.find { it.code == code && it.id != draft.id }
        if (clash != null) return "Код $code уже используется"
        return null
    }

    fun deleteCommodity(book: Book, id: String): String? {
        val commodity = book.commodity(id) ?: return "Единица не найдена"
        if (commodity.operating) return "Валюту учёта удалить нельзя"
        val used = book.entries.any { it.debit.commodityId == id || it.credit.commodityId == id }
        if (used) return "Единица ${commodity.code} есть в проводках"
        return null
    }

    private fun hasHistory(book: Book, id: String): Boolean {
        if (postingDates(book, id).isNotEmpty()) return true
        return book.accounts.filter { it.parentId == id }.any { hasHistory(book, it.id) }
    }

    private fun postingDates(book: Book, id: String): List<LocalDate> = book.entries
        .filter { it.debit.accountId == id || it.credit.accountId == id }
        .map { it.date }

    /** True when [node] is [root] or lies under it. */
    private fun isUnder(book: Book, node: String, root: String): Boolean {
        var current = book.account(node)
        val guard = HashSet<String>()
        while (current != null && guard.add(current.id)) {
            if (current.id == root) return true
            current = current.parentId?.let { book.account(it) }
        }
        return false
    }
}
