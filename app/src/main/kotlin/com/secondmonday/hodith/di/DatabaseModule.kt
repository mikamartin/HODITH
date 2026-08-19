package com.secondmonday.hodith.di

import android.content.Context
import androidx.room.Room
import com.secondmonday.hodith.data.CaseDao
import com.secondmonday.hodith.data.EventDao
import com.secondmonday.hodith.data.HodithDatabase
import com.secondmonday.hodith.data.HunchDao
import com.secondmonday.hodith.data.TagDao
import com.secondmonday.hodith.data.TriggerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideHodithDatabase(
        @ApplicationContext context: Context,
    ): HodithDatabase =
        Room
            .databaseBuilder(context, HodithDatabase::class.java, "hodith.db")
            .addMigrations(*HodithDatabase.MIGRATIONS)
            .build()

    @Provides
    fun provideCaseDao(database: HodithDatabase): CaseDao = database.caseDao()

    @Provides
    fun provideEventDao(database: HodithDatabase): EventDao = database.eventDao()

    @Provides
    fun provideTagDao(database: HodithDatabase): TagDao = database.tagDao()

    @Provides
    fun provideHunchDao(database: HodithDatabase): HunchDao = database.hunchDao()

    @Provides
    fun provideTriggerDao(database: HodithDatabase): TriggerDao = database.triggerDao()
}
