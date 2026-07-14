package com.tepmex.ankientertainer

import android.app.Application
import com.tepmex.ankientertainer.data.AppPreferences
import com.tepmex.ankientertainer.data.LikedChunksRepository

class AnkiEntertainerApp : Application() {
    lateinit var preferences: AppPreferences
        private set

    lateinit var likedChunks: LikedChunksRepository
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        likedChunks = LikedChunksRepository(this)
    }
}
