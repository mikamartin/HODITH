package com.secondmonday.hodith.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val THEME_KEY = stringPreferencesKey("theme")
private val CHECK_IN_DEFAULT_INTERVAL_KEY = stringPreferencesKey("check_in_default_interval")
private val NOTIFICATION_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")

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

        override fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval> =
            dataStore.data.map { preferences ->
                preferences[CHECK_IN_DEFAULT_INTERVAL_KEY]?.let { name ->
                    runCatching { CheckInDefaultInterval.valueOf(name) }.getOrNull()
                } ?: CheckInDefaultInterval.SEVEN
            }

        override suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval) {
            dataStore.edit { preferences -> preferences[CHECK_IN_DEFAULT_INTERVAL_KEY] = interval.name }
        }

        override suspend fun getCheckInDefaultInterval(): CheckInDefaultInterval = observeCheckInDefaultInterval().first()

        override suspend fun hasRequestedNotificationPermission(): Boolean = observeHasRequestedNotificationPermission().first()

        override fun observeHasRequestedNotificationPermission(): Flow<Boolean> =
            dataStore.data.map { preferences -> preferences[NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false }

        override suspend fun setNotificationPermissionRequested() {
            dataStore.edit { preferences -> preferences[NOTIFICATION_PERMISSION_REQUESTED_KEY] = true }
        }
    }
