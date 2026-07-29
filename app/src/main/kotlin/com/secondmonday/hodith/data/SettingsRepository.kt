package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTheme(): Flow<AppTheme>

    suspend fun setTheme(theme: AppTheme)

    fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval>

    suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval)
}
