package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class TriggerRow(
    val id: Long,
    val kind: TriggerKind,
    val threshold: Int,
    val windowDays: Int?,
    val enabled: Boolean,
    val firedDaysAgo: Long?,
)

data class TriggersUiState(
    val triggers: List<TriggerRow> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

@HiltViewModel
class TriggersViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
        private val notificationPermissionRequestSignal: NotificationPermissionRequestSignal,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        val uiState: StateFlow<TriggersUiState> =
            repository
                .observeTriggersForCase(caseId)
                .map { triggers ->
                    TriggersUiState(triggers = triggerRows(triggers, clock.nowMillis()), isLoading = false)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = TriggersUiState(),
                )

        fun createTrigger(
            kind: TriggerKind,
            threshold: Int,
            windowDays: Int?,
        ) {
            viewModelScope.launch {
                repository.insertTrigger(
                    TriggerEntity(
                        caseId = caseId,
                        kind = kind,
                        threshold = threshold,
                        windowDays = if (kind == TriggerKind.AT_LEAST) windowDays else null,
                        enabled = true,
                        lastFiredAt = null,
                    ),
                )
                if (!settingsRepository.hasRequestedNotificationPermission()) {
                    settingsRepository.setNotificationPermissionRequested()
                    notificationPermissionRequestSignal.request()
                }
            }
        }

        fun setEnabled(
            triggerId: Long,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val trigger = repository.getTrigger(triggerId) ?: return@launch
                repository.updateTrigger(trigger.copy(enabled = enabled))
            }
        }

        fun deleteTrigger(triggerId: Long) {
            viewModelScope.launch {
                val trigger = repository.getTrigger(triggerId) ?: return@launch
                repository.deleteTrigger(trigger)
            }
        }
    }

/**
 * Pure mapping so the list-row shape is unit-testable on the JVM without a repository or Hilt,
 * same pattern as [archivedCaseRows]. [TriggerRow.firedDaysAgo] is calendar-day-aware (via
 * [ChronoUnit.DAYS]), matching [monthsAgo]'s precedent rather than a fixed-millis division.
 */
internal fun triggerRows(
    triggers: List<TriggerEntity>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<TriggerRow> =
    triggers.map { trigger ->
        TriggerRow(
            id = trigger.id,
            kind = trigger.kind,
            threshold = trigger.threshold,
            windowDays = trigger.windowDays,
            enabled = trigger.enabled,
            firedDaysAgo = trigger.lastFiredAt?.let { daysAgo(it, nowMillis, zone) },
        )
    }

private fun daysAgo(
    pastMillis: Long,
    nowMillis: Long,
    zone: ZoneId,
): Long {
    val past = Instant.ofEpochMilli(pastMillis).atZone(zone)
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    return ChronoUnit.DAYS.between(past, now)
}
