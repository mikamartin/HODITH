package com.secondmonday.hodith.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val THEME_KEY = stringPreferencesKey("theme")

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        override fun observeTheme(): Flow<AppTheme> =
            dataStore.data.map { preferences ->
                preferences[THEME_KEY]?.let { name ->
                    runCatching { AppTheme.valueOf(name) }.getOrNull()
                } ?: AppTheme.PLAIN
            }

        override suspend fun setTheme(theme: AppTheme) {
            dataStore.edit { preferences -> preferences[THEME_KEY] = theme.name }
        }
    }
