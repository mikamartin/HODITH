package com.secondmonday.hodith.di

import com.secondmonday.hodith.data.DataStoreSettingsRepository
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.RoomHodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.backup.BackupFileWriter
import com.secondmonday.hodith.data.backup.ContentResolverBackupFileWriter
import com.secondmonday.hodith.notification.Notifier
import com.secondmonday.hodith.notification.SystemNotifier
import com.secondmonday.hodith.widget.GlanceWidgetRefresher
import com.secondmonday.hodith.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindHodithRepository(roomHodithRepository: RoomHodithRepository): HodithRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(dataStoreSettingsRepository: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(glanceWidgetRefresher: GlanceWidgetRefresher): WidgetRefresher

    @Binds
    @Singleton
    abstract fun bindNotifier(systemNotifier: SystemNotifier): Notifier

    @Binds
    @Singleton
    abstract fun bindBackupFileWriter(contentResolverBackupFileWriter: ContentResolverBackupFileWriter): BackupFileWriter
}
