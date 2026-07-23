package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.demo.DemoDataSeeder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SERIOUS,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val hodithRepository: HodithRepository,
        private val demoDataSeeder: DemoDataSeeder,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            settingsRepository
                .observeTheme()
                .map { theme -> SettingsUiState(theme = theme, isLoading = false) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = SettingsUiState(),
                )

        private val _demoDataLoaded = Channel<Unit>(Channel.BUFFERED)
        val demoDataLoaded: Flow<Unit> = _demoDataLoaded.receiveAsFlow()

        fun onThemeSelect(theme: AppTheme) {
            viewModelScope.launch { settingsRepository.setTheme(theme) }
        }

        fun loadDemoData() {
            viewModelScope.launch {
                demoDataSeeder.seed()
                _demoDataLoaded.send(Unit)
            }
        }

        fun deleteAllData() {
            viewModelScope.launch { hodithRepository.deleteAllData() }
        }
    }
