package com.secondmonday.hodith.di

import com.secondmonday.hodith.AppInitializer
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@Module
@InstallIn(SingletonComponent::class)
interface AppInitializerModule {
    @Multibinds
    fun appInitializers(): Set<AppInitializer>
}
