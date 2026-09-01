package com.secondmonday.hodith.data

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val THEME_KEY = stringPreferencesKey("theme")
private val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
private val CHECK_IN_DEFAULT_INTERVAL_KEY = stringPreferencesKey("check_in_default_interval")
private val NOTIFICATION_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
private val DEVELOPER_MODE_UNLOCKED_KEY = booleanPreferencesKey("developer_mode_unlocked")
private val CLOUD_BACKUP_ENABLED_KEY = booleanPreferencesKey("cloud_backup_enabled")

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @ApplicationContext private val context: Context,
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

        override fun observeTimeFormat(): Flow<TimeFormat> =
            dataStore.data.map { preferences ->
                preferences[TIME_FORMAT_KEY]?.let { name ->
                    runCatching { TimeFormat.valueOf(name) }.getOrNull()
                } ?: deviceTimeFormat()
            }

        override suspend fun setTimeFormat(format: TimeFormat) {
            dataStore.edit { preferences -> preferences[TIME_FORMAT_KEY] = format.name }
        }

        private fun deviceTimeFormat(): TimeFormat =
            if (DateFormat.is24HourFormat(context)) TimeFormat.TWENTY_FOUR_HOUR else TimeFormat.TWELVE_HOUR

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

        override fun observeDeveloperModeUnlocked(): Flow<Boolean> =
            dataStore.data.map { preferences -> preferences[DEVELOPER_MODE_UNLOCKED_KEY] ?: false }

        override suspend fun setDeveloperModeUnlocked() {
            dataStore.edit { preferences -> preferences[DEVELOPER_MODE_UNLOCKED_KEY] = true }
        }

        override fun observeCloudBackupEnabled(): Flow<Boolean> =
            dataStore.data.map { preferences -> preferences[CLOUD_BACKUP_ENABLED_KEY] ?: true }

        override suspend fun setCloudBackupEnabled(enabled: Boolean) {
            dataStore.edit { preferences -> preferences[CLOUD_BACKUP_ENABLED_KEY] = enabled }
        }
    }
