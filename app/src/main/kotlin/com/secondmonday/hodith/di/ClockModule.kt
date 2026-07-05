package com.secondmonday.hodith.di

import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {
    @Binds
    @Singleton
    abstract fun bindClock(systemClock: SystemClock): Clock
}
