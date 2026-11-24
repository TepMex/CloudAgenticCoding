package com.tepmex.pacholli.data

import androidx.room.withTransaction
import com.tepmex.pacholli.domain.Account
import com.tepmex.pacholli.domain.AccountDraft
import com.tepmex.pacholli.domain.AccountSection
import com.tepmex.pacholli.domain.AccountType
import com.tepmex.pacholli.domain.Book
import com.tepmex.pacholli.domain.Chart
import com.tepmex.pacholli.domain.Commodity
import com.tepmex.pacholli.domain.CommodityDraft
import com.tepmex.pacholli.domain.Entry
import com.tepmex.pacholli.domain.Ledger
import com.tepmex.pacholli.domain.Posting
import com.tepmex.pacholli.domain.Quote
import com.tepmex.pacholli.domain.Seed
import com.tepmex.pacholli.domain.Side
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class Repository(private val database: PacholliDatabase) {
    private val dao = database.dao()
    private val state = MutableStateFlow(Book.empty())
    private val loaded = MutableStateFlow(false)
    val book: StateFlow<Book> = state.asStateFlow()
    val ready: StateFlow<Boolean> = loaded.asStateFlow()

    suspend fun load() {
        val next = withContext(Dispatchers.IO) {
            if (dao.meta() == null) persist(Seed.book())
            restore()
        }
        state.value = next
        loaded.value = true
    }

    suspend fun saveEntry(entry: Entry): String? {
        val error = Ledger.explain(state.value, entry)
        if (error != null) return error
        val next = withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.deletePostings(entry.id)
                dao.insertEntries(listOf(entry.toEntity()))
                dao.insertPostings(listOf(entry.debit, entry.credit).map { it.toEntity(entry.id) })
            }
            restore()
        }
        state.value = next
        return null
    }

    suspend fun deleteEntry(id: String) {
        val next = withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.deletePostings(id)
                dao.deleteEntry(id)
            }
            restore()
        }
        state.value = next
    }

    suspend fun saveAccount(draft: AccountDraft): String? {
        val error = Chart.checkAccount(state.value, draft)
        if (error != null) return error
        val updated = Chart.commitAccount(state.value, draft)
        val next = withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.clearAccounts()
                dao.insertAccounts(updated.accounts.map { it.toEntity() })
            }
            restore()
        }
        state.value = next
        return null
    }

    suspend fun deleteAccount(id: String): String? {
        val error = Chart.deleteAccount(state.value, id)
        if (error != null) return error
        val next = withContext(Dispatchers.IO) {
            dao.deleteAccount(id)
            restore()
        }
        state.value = next
        return null
    }

    suspend fun saveCommodity(draft: CommodityDraft, price: BigDecimal?, priceDate: LocalDate): String? {
        val error = Chart.checkCommodity(state.value, draft)
        if (error != null) return error
        val existing = state.value.commodity(draft.id)
        val saved = Commodity(
            id = draft.id,
            code = draft.code.trim(),
            name = draft.name.trim(),
            operating = existing?.operating == true,
            openedOn = draft.openedOn,
        )
        val next = withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.insertCommodities(listOf(saved.toEntity()))
                if (!saved.operating && price != null && price.signum() > 0) {
                    dao.insertQuotes(listOf(QuoteEntity(saved.id, priceDate.toEpochDay(), price.toPlainString())))
                }
            }
            restore()
        }
        state.value = next
        return null
    }

    suspend fun deleteCommodity(id: String): String? {
        val error = Chart.deleteCommodity(state.value, id)
        if (error != null) return error
        val next = withContext(Dispatchers.IO) {
            database.withTransaction {
                dao.deleteQuotes(id)
                dao.deleteCommodity(id)
            }
            restore()
        }
        state.value = next
        return null
    }

    suspend fun deleteQuote(commodityId: String, date: LocalDate) {
        val next = withContext(Dispatchers.IO) {
            dao.deleteQuote(commodityId, date.toEpochDay())
            restore()
        }
        state.value = next
    }

    suspend fun setTitle(title: String) {
        val next = withContext(Dispatchers.IO) {
            dao.insertMeta(MetaEntity(title = title.trim().ifEmpty { "Pacholli" }))
            restore()
        }
        state.value = next
    }

    suspend fun replace(book: Book) {
        val next = withContext(Dispatchers.IO) {
            database.withTransaction { persist(book) }
            restore()
        }
        state.value = next
    }

    private suspend fun persist(book: Book) {
        dao.clearPostings()
        dao.clearEntries()
        dao.clearQuotes()
        dao.clearAccounts()
        dao.clearCommodities()
        dao.clearMeta()
        dao.insertMeta(MetaEntity(title = book.title))
        dao.insertCommodities(book.commodities.map { it.toEntity() })
        dao.insertQuotes(book.quotes.map { QuoteEntity(it.commodityId, it.date.toEpochDay(), it.price.toPlainString()) })
        dao.insertAccounts(book.accounts.map { it.toEntity() })
        dao.insertEntries(book.entries.map { it.toEntity() })
        dao.insertPostings(book.entries.flatMap { entry ->
            listOf(entry.debit, entry.credit).map { it.toEntity(entry.id) }
        })
    }

    private suspend fun restore(): Book {
        val meta = dao.meta()
        val postings = dao.postings().groupBy { it.entryId }
        val entries = dao.entries().mapNotNull { entity ->
            val legs = postings[entity.id].orEmpty()
            val debit = legs.find { it.side == Side.DEBIT.name } ?: return@mapNotNull null
            val credit = legs.find { it.side == Side.CREDIT.name } ?: return@mapNotNull null
            Entry(
                id = entity.id,
                date = LocalDate.ofEpochDay(entity.epochDay),
                timeMinutes = entity.timeMinutes,
                payee = entity.payee,
                narration = entity.narration,
                debit = debit.toPosting(),
                credit = credit.toPosting(),
            )
        }
        return Book(
            title = meta?.title ?: "Pacholli",
            commodities = dao.commodities().map { it.toCommodity() },
            quotes = dao.quotes().map {
                Quote(it.commodityId, LocalDate.ofEpochDay(it.epochDay), BigDecimal(it.price))
            },
            accounts = dao.accounts().map { it.toAccount() },
            entries = entries,
        )
    }
}

private fun Commodity.toEntity() = CommodityEntity(id, code, name, operating, openedOn.toEpochDay())

private fun CommodityEntity.toCommodity() = Commodity(
    id = id,
    code = code,
    name = name,
    operating = operating,
    openedOn = LocalDate.ofEpochDay(openedEpoch),
)

private fun Account.toEntity() = AccountEntity(
    id = id,
    parentId = parentId,
    name = name,
    beancountName = beancountName,
    accountType = type.name,
    section = section.name,
    allowsSubaccounts = allowsSubaccounts,
    cash = cash,
    openedEpoch = openedOn.toEpochDay(),
    closedEpoch = closedOn?.toEpochDay(),
)

private fun AccountEntity.toAccount() = Account(
    id = id,
    parentId = parentId,
    name = name,
    beancountName = beancountName,
    type = AccountType.valueOf(accountType),
    section = AccountSection.valueOf(section),
    allowsSubaccounts = allowsSubaccounts,
    cash = cash,
    openedOn = LocalDate.ofEpochDay(openedEpoch),
    closedOn = closedEpoch?.let { LocalDate.ofEpochDay(it) },
)

private fun Entry.toEntity() = EntryEntity(id, date.toEpochDay(), timeMinutes, payee, narration)

private fun Posting.toEntity(entryId: String) = PostingEntity(
    entryId = entryId,
    side = side.name,
    accountId = accountId,
    quantity = quantity.toPlainString(),
    commodityId = commodityId,
    baseAmount = baseAmount.toPlainString(),
)

private fun PostingEntity.toPosting() = Posting(
    accountId = accountId,
    side = Side.valueOf(side),
    quantity = BigDecimal(quantity),
    commodityId = commodityId,
    baseAmount = BigDecimal(baseAmount),
)
