package com.tepmex.zuotasks.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class NodeTypeConverter {
    @TypeConverter
    fun fromNodeType(value: NodeType): String = value.name

    @TypeConverter
    fun toNodeType(value: String): NodeType = NodeType.valueOf(value)
}

@Database(
    entities = [TreeNodeEntity::class, RegularTaskEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(NodeTypeConverter::class)
abstract class ZuoTasksDatabase : RoomDatabase() {
    abstract fun treeNodeDao(): TreeNodeDao
    abstract fun regularTaskDao(): RegularTaskDao

    companion object {
        @Volatile
        private var instance: ZuoTasksDatabase? = null

        fun get(context: Context): ZuoTasksDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ZuoTasksDatabase::class.java,
                    "zuotasks.db",
                )
                    .build()
                    .also { instance = it }
            }
    }
}
