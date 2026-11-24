package com.tepmex.pacholli.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Entity(tableName = "meta")
data class MetaEntity(
    @PrimaryKey val id: Int = 1,
    val title: String,
)

@Entity(tableName = "commodities")
data class CommodityEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val operating: Boolean,
    val openedEpoch: Long,
)

@Entity(tableName = "quotes", primaryKeys = ["commodityId", "epochDay"])
data class QuoteEntity(
    val commodityId: String,
    val epochDay: Long,
    val price: String,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val beancountName: String,
    val accountType: String,
    val section: String,
    val allowsSubaccounts: Boolean,
    val cash: Boolean,
    val openedEpoch: Long,
    val closedEpoch: Long?,
)

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val epochDay: Long,
    val timeMinutes: Int?,
    val payee: String,
    val narration: String,
)

@Entity(tableName = "postings", primaryKeys = ["entryId", "side"])
data class PostingEntity(
    val entryId: String,
    val side: String,
    val accountId: String,
    val quantity: String,
    val commodityId: String,
    val baseAmount: String,
)

@Dao
interface PacholliDao {
    @Query("SELECT * FROM meta WHERE id = 1")
    suspend fun meta(): MetaEntity?

    @Query("SELECT * FROM commodities")
    suspend fun commodities(): List<CommodityEntity>

    @Query("SELECT * FROM quotes")
    suspend fun quotes(): List<QuoteEntity>

    @Query("SELECT * FROM accounts")
    suspend fun accounts(): List<AccountEntity>

    @Query("SELECT * FROM entries")
    suspend fun entries(): List<EntryEntity>

    @Query("SELECT * FROM postings")
    suspend fun postings(): List<PostingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(entity: MetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommodities(entities: List<CommodityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuotes(entities: List<QuoteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(entities: List<AccountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entities: List<EntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostings(entities: List<PostingEntity>)

    @Query("DELETE FROM meta")
    suspend fun clearMeta()

    @Query("DELETE FROM commodities")
    suspend fun clearCommodities()

    @Query("DELETE FROM quotes")
    suspend fun clearQuotes()

    @Query("DELETE FROM accounts")
    suspend fun clearAccounts()

    @Query("DELETE FROM entries")
    suspend fun clearEntries()

    @Query("DELETE FROM postings")
    suspend fun clearPostings()

    @Query("DELETE FROM postings WHERE entryId = :entryId")
    suspend fun deletePostings(entryId: String)

    @Query("DELETE FROM entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: String)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccount(id: String)

    @Query("DELETE FROM quotes WHERE commodityId = :commodityId")
    suspend fun deleteQuotes(commodityId: String)

    @Query("DELETE FROM quotes WHERE commodityId = :commodityId AND epochDay = :epochDay")
    suspend fun deleteQuote(commodityId: String, epochDay: Long)

    @Query("DELETE FROM commodities WHERE id = :id")
    suspend fun deleteCommodity(id: String)
}

@Database(
    entities = [
        MetaEntity::class,
        CommodityEntity::class,
        QuoteEntity::class,
        AccountEntity::class,
        EntryEntity::class,
        PostingEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PacholliDatabase : RoomDatabase() {
    abstract fun dao(): PacholliDao

    companion object {
        fun create(context: Context): PacholliDatabase =
            Room.databaseBuilder(context, PacholliDatabase::class.java, "pacholli.db")
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
