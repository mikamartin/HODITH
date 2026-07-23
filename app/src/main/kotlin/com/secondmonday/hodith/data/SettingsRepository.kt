package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTheme(): Flow<AppTheme>

    suspend fun setTheme(theme: AppTheme)
}
