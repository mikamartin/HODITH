package com.secondmonday.hodith.debug

import com.secondmonday.hodith.AppInitializer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class SeedDataModule {
    @Binds
    @IntoSet
    abstract fun bindSeedDataInitializer(initializer: SeedDataInitializer): AppInitializer
}
