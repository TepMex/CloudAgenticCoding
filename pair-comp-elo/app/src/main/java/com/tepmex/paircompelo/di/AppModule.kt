package com.tepmex.paircompelo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.tepmex.paircompelo.core.AppClock
import com.tepmex.paircompelo.core.SystemAppClock
import com.tepmex.paircompelo.data.dao.ItemComparisonDao
import com.tepmex.paircompelo.data.dao.ListComparisonDao
import com.tepmex.paircompelo.data.dao.PreferenceItemDao
import com.tepmex.paircompelo.data.dao.PreferenceListDao
import com.tepmex.paircompelo.data.db.PairCompEloDatabase
import com.tepmex.paircompelo.domain.pairing.PairSelector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "ranking_settings")

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemAppClock): AppClock
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PairCompEloDatabase =
        Room.databaseBuilder(
            context,
            PairCompEloDatabase::class.java,
            PairCompEloDatabase.NAME,
        )
            .build()

    @Provides
    fun provideListDao(db: PairCompEloDatabase): PreferenceListDao = db.preferenceListDao()

    @Provides
    fun provideItemDao(db: PairCompEloDatabase): PreferenceItemDao = db.preferenceItemDao()

    @Provides
    fun provideItemComparisonDao(db: PairCompEloDatabase): ItemComparisonDao = db.itemComparisonDao()

    @Provides
    fun provideListComparisonDao(db: PairCompEloDatabase): ListComparisonDao = db.listComparisonDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun providePairSelector(): PairSelector = PairSelector()
}
