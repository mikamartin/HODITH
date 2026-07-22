package com.secondmonday.hodith.di

import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.RoomHodithRepository
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
}
