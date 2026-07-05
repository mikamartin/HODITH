package com.secondmonday.hodit.di

import android.content.Context
import androidx.room.Room
import com.secondmonday.hodit.data.CaseDao
import com.secondmonday.hodit.data.EventDao
import com.secondmonday.hodit.data.HoditDatabase
import com.secondmonday.hodit.data.HunchDao
import com.secondmonday.hodit.data.TagDao
import com.secondmonday.hodit.data.TriggerDao
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
    fun provideHoditDatabase(
        @ApplicationContext context: Context,
    ): HoditDatabase = Room.databaseBuilder(context, HoditDatabase::class.java, "hodit.db").build()

    @Provides
    fun provideCaseDao(database: HoditDatabase): CaseDao = database.caseDao()

    @Provides
    fun provideEventDao(database: HoditDatabase): EventDao = database.eventDao()

    @Provides
    fun provideTagDao(database: HoditDatabase): TagDao = database.tagDao()

    @Provides
    fun provideHunchDao(database: HoditDatabase): HunchDao = database.hunchDao()

    @Provides
    fun provideTriggerDao(database: HoditDatabase): TriggerDao = database.triggerDao()
}
