package com.tepmex.paircompelo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tepmex.paircompelo.data.dao.ItemComparisonDao
import com.tepmex.paircompelo.data.dao.ListComparisonDao
import com.tepmex.paircompelo.data.dao.PreferenceItemDao
import com.tepmex.paircompelo.data.dao.PreferenceListDao

@Database(
    entities = [
        PreferenceListEntity::class,
        PreferenceItemEntity::class,
        ItemComparisonEntity::class,
        ListComparisonEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PairCompEloDatabase : RoomDatabase() {
    abstract fun preferenceListDao(): PreferenceListDao
    abstract fun preferenceItemDao(): PreferenceItemDao
    abstract fun itemComparisonDao(): ItemComparisonDao
    abstract fun listComparisonDao(): ListComparisonDao

    companion object {
        const val NAME = "pair_comp_elo.db"
    }
}
