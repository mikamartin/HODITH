package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-rolled in-memory test double for [SettingsRepository], same style as [FakeHodithRepository]. */
class FakeSettingsRepository : SettingsRepository {
    val theme = MutableStateFlow(AppTheme.PLAIN)

    override fun observeTheme(): Flow<AppTheme> = theme

    override suspend fun setTheme(theme: AppTheme) {
        this.theme.value = theme
    }
}
