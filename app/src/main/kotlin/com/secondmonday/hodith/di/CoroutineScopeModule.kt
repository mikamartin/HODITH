package com.secondmonday.hodith.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CoroutineScopeModule {
    /**
     * Outlives any single screen — used by [com.secondmonday.hodith.data.RoomHodithRepository] to
     * fire immediate Trigger/check-in evaluation after an event mutation without making callers
     * (quick-log, start/stop) wait on it. [SupervisorJob] so one failed evaluation can't cancel
     * others sharing the scope.
     */
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
