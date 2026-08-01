package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTheme(): Flow<AppTheme>

    suspend fun setTheme(theme: AppTheme)

    fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval>

    suspend fun getCheckInDefaultInterval(): CheckInDefaultInterval

    suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval)

    /** Spec §11: POST_NOTIFICATIONS is requested once, on first trigger created or first check-in enabled — never again after. */
    suspend fun hasRequestedNotificationPermission(): Boolean

    fun observeHasRequestedNotificationPermission(): Flow<Boolean>

    suspend fun setNotificationPermissionRequested()
}
