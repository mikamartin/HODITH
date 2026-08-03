package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Taps on the About screen's version row to unlock Developer Mode — same convention as Android's own Developer Options. */
private const val DEVELOPER_MODE_UNLOCK_TAP_COUNT = 7

/** Countdown feedback only starts this many taps before unlock, so early taps stay silent. */
private const val DEVELOPER_MODE_UNLOCK_COUNTDOWN_START = 3

sealed interface DeveloperModeUnlockEvent {
    data class Countdown(
        val remainingTaps: Int,
    ) : DeveloperModeUnlockEvent

    data object Unlocked : DeveloperModeUnlockEvent
}

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        private var tapCount = 0

        private val _unlockEvents = Channel<DeveloperModeUnlockEvent>(Channel.BUFFERED)
        val unlockEvents: Flow<DeveloperModeUnlockEvent> = _unlockEvents.receiveAsFlow()

        fun onVersionTapped() {
            viewModelScope.launch {
                if (settingsRepository.observeDeveloperModeUnlocked().first()) return@launch

                tapCount++
                val remainingTaps = DEVELOPER_MODE_UNLOCK_TAP_COUNT - tapCount
                when {
                    remainingTaps <= 0 -> {
                        settingsRepository.setDeveloperModeUnlocked()
                        _unlockEvents.send(DeveloperModeUnlockEvent.Unlocked)
                    }
                    remainingTaps <= DEVELOPER_MODE_UNLOCK_COUNTDOWN_START -> {
                        _unlockEvents.send(DeveloperModeUnlockEvent.Countdown(remainingTaps))
                    }
                }
            }
        }
    }
