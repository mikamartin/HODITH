package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTheme(): Flow<AppTheme>

    suspend fun setTheme(theme: AppTheme)

    /** Spec §14: 12h/24h clock display. No stored value means "follow the device" — see [TimeFormat]. */
    fun observeTimeFormat(): Flow<TimeFormat>

    suspend fun setTimeFormat(format: TimeFormat)

    fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval>

    suspend fun getCheckInDefaultInterval(): CheckInDefaultInterval

    suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval)

    /** Spec §11: POST_NOTIFICATIONS is requested once, on first trigger created or first check-in enabled — never again after. */
    suspend fun hasRequestedNotificationPermission(): Boolean

    fun observeHasRequestedNotificationPermission(): Flow<Boolean>

    suspend fun setNotificationPermissionRequested()

    /** Hidden developer-mode unlock (About screen's version-tap gesture) — one-way, like [setNotificationPermissionRequested]. */
    fun observeDeveloperModeUnlocked(): Flow<Boolean>

    suspend fun setDeveloperModeUnlocked()

    /** Spec §16: opts out of Android's OS-level device backup (cloud backup and device-transfer alike). Default on. */
    fun observeCloudBackupEnabled(): Flow<Boolean>

    suspend fun setCloudBackupEnabled(enabled: Boolean)
}
