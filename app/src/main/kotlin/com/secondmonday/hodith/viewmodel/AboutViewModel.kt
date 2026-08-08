package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Taps in each burst of the About screen's version-row unlock gesture. */
private const val DEVELOPER_MODE_BURST_ONE_TAPS = 3
private const val DEVELOPER_MODE_BURST_TWO_TAPS = 4

/** Taps within a burst must land closer together than this, or the attempt resets. */
private const val DEVELOPER_MODE_BURST_MAX_GAP_MS = 600L

/** The gap between the two bursts must fall in this window — too quick or too slow resets the attempt. */
private const val DEVELOPER_MODE_PAUSE_MIN_MS = 600L
private const val DEVELOPER_MODE_PAUSE_MAX_MS = 3000L

sealed interface DeveloperModeUnlockEvent {
    data object Unlocked : DeveloperModeUnlockEvent
}

private sealed interface TapState {
    data object Idle : TapState

    data class BurstOne(
        val count: Int,
        val lastTapAt: Long,
    ) : TapState

    data class Paused(
        val pauseStartedAt: Long,
    ) : TapState

    data class BurstTwo(
        val count: Int,
        val lastTapAt: Long,
    ) : TapState
}

@HiltViewModel
class AboutViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private var tapState: TapState = TapState.Idle

        private val _unlockEvents = Channel<DeveloperModeUnlockEvent>(Channel.BUFFERED)
        val unlockEvents: Flow<DeveloperModeUnlockEvent> = _unlockEvents.receiveAsFlow()

        fun onVersionTapped() {
            viewModelScope.launch {
                if (settingsRepository.observeDeveloperModeUnlocked().first()) return@launch

                val now = clock.nowMillis()
                tapState =
                    when (val state = tapState) {
                        is TapState.Idle -> TapState.BurstOne(count = 1, lastTapAt = now)

                        is TapState.BurstOne ->
                            if (now - state.lastTapAt > DEVELOPER_MODE_BURST_MAX_GAP_MS) {
                                TapState.BurstOne(count = 1, lastTapAt = now)
                            } else if (state.count + 1 == DEVELOPER_MODE_BURST_ONE_TAPS) {
                                TapState.Paused(pauseStartedAt = now)
                            } else {
                                TapState.BurstOne(count = state.count + 1, lastTapAt = now)
                            }

                        is TapState.Paused -> {
                            val pauseLength = now - state.pauseStartedAt
                            if (pauseLength in DEVELOPER_MODE_PAUSE_MIN_MS..DEVELOPER_MODE_PAUSE_MAX_MS) {
                                TapState.BurstTwo(count = 1, lastTapAt = now)
                            } else {
                                TapState.BurstOne(count = 1, lastTapAt = now)
                            }
                        }

                        is TapState.BurstTwo ->
                            if (now - state.lastTapAt > DEVELOPER_MODE_BURST_MAX_GAP_MS) {
                                TapState.BurstOne(count = 1, lastTapAt = now)
                            } else if (state.count + 1 == DEVELOPER_MODE_BURST_TWO_TAPS) {
                                settingsRepository.setDeveloperModeUnlocked()
                                _unlockEvents.send(DeveloperModeUnlockEvent.Unlocked)
                                TapState.Idle
                            } else {
                                TapState.BurstTwo(count = state.count + 1, lastTapAt = now)
                            }
                    }
            }
        }
    }
