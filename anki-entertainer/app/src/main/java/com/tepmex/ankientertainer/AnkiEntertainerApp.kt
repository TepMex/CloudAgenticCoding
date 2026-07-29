package com.tepmex.ankientertainer

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.tepmex.ankientertainer.data.AppPreferences
import com.tepmex.ankientertainer.data.LikedChunksRepository
import com.tepmex.ankientertainer.data.hanzi.DefaultPromptTemplateEngine
import com.tepmex.ankientertainer.data.hanzi.HanziMetadataDatabase
import com.tepmex.ankientertainer.data.hanzi.HanziMetadataRepository
import com.tepmex.ankientertainer.data.hanzi.PromptTemplateEngine
import com.tepmex.ankientertainer.data.hanzi.RoomHanziMetadataRepository

class AnkiEntertainerApp : Application() {
    lateinit var preferences: AppPreferences
        private set

    lateinit var likedChunks: LikedChunksRepository
        private set

    lateinit var hanziMetadataRepository: HanziMetadataRepository
        private set

    lateinit var promptTemplateEngine: PromptTemplateEngine
        private set

    private var hanziDatabase: HanziMetadataDatabase? = null

    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        preferences = AppPreferences(this)
        likedChunks = LikedChunksRepository(this)
        hanziMetadataRepository = RoomHanziMetadataRepository(
            databaseProvider = {
                val existing = hanziDatabase
                if (existing != null) return@RoomHanziMetadataRepository existing
                val opened = HanziMetadataDatabase.open(this)
                hanziDatabase = opened
                opened
            },
        )
        promptTemplateEngine = DefaultPromptTemplateEngine(hanziMetadataRepository)
    }
}
