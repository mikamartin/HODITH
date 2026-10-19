package com.secondmonday.hodith.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SerializationModule {
    // encodeDefaults = true: a defaulted field (schemaVersion, WatchEntity.armed/metric) must
    // always be written, not omitted when it equals its default - BackupSerializer.peekSchemaVersion
    // and any future upgrade step depend on schemaVersion actually being present in the JSON.
    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
}
