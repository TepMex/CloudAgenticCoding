package com.tepmex.ankientertainer.data.hanzi

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HanziEntity::class,
        GreedyCompositionEntity::class,
        VariantEntity::class,
        SimplificationEntity::class,
        MnemonicEntity::class,
        DatasetMetadataEntity::class,
    ],
    version = HanziMetadataDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class HanziMetadataDatabase : RoomDatabase() {
    abstract fun hanziDao(): HanziDao

    companion object {
        const val SCHEMA_VERSION = 2
        const val DB_NAME = "hanzi_metadata.db"
        const val ASSET_PATH = "databases/hanzi_metadata.db"

        fun open(context: Context): HanziMetadataDatabase =
            Room.databaseBuilder(context.applicationContext, HanziMetadataDatabase::class.java, DB_NAME)
                .createFromAsset(ASSET_PATH)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
