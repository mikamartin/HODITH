package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.VerdictMetric
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

data class NotificationRow(
    val id: Long,
    val kind: NotificationKind,
    val threshold: Int,
    val windowDays: Int?,
    val enabled: Boolean,
    val firedDaysAgo: Long?,
)

data class NotificationsUiState(
    val notifications: List<NotificationRow> = emptyList(),
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Default lookback unit for a newly-created `OFTEN` Notification — later phases surface a picker for this. */
private val DEFAULT_EXPECTED_PER = ExpectedPer.WEEK

@HiltViewModel
class NotificationsViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
        private val notificationPermissionRequestSignal: NotificationPermissionRequestSignal,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        val uiState: StateFlow<NotificationsUiState> =
            repository
                .observeNotificationsForCase(caseId)
                .map { notifications ->
                    NotificationsUiState(notifications = notificationRows(notifications, clock.nowMillis()), isLoading = false)
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = NotificationsUiState(),
                )

        fun createNotification(
            kind: NotificationKind,
            threshold: Int,
            windowDays: Int?,
        ) {
            if (kind == NotificationKind.OFTEN && (windowDays == null || windowDays <= 0)) return
            viewModelScope.launch {
                repository.insertNotification(
                    NotificationEntity(
                        caseId = caseId,
                        kind = kind,
                        threshold = threshold,
                        windowDays = if (kind == NotificationKind.OFTEN) windowDays else null,
                        expectedPer = DEFAULT_EXPECTED_PER,
                        metric = VerdictMetric.OCCURRENCE_COUNT,
                        minIntensity = null,
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
            notificationId: Long,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val notification = repository.getNotification(notificationId) ?: return@launch
                repository.updateNotification(notification.copy(enabled = enabled))
            }
        }

        fun deleteNotification(notificationId: Long) {
            viewModelScope.launch {
                val notification = repository.getNotification(notificationId) ?: return@launch
                repository.deleteNotification(notification)
            }
        }
    }

/**
 * Pure mapping so the list-row shape is unit-testable on the JVM without a repository or Hilt,
 * same pattern as [archivedCaseRows]. [NotificationRow.firedDaysAgo] is calendar-day-aware (via
 * [ChronoUnit.DAYS]) rather than a fixed-millis division.
 */
internal fun notificationRows(
    notifications: List<NotificationEntity>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<NotificationRow> =
    notifications.map { notification ->
        NotificationRow(
            id = notification.id,
            kind = notification.kind,
            threshold = notification.threshold,
            windowDays = notification.windowDays,
            enabled = notification.enabled,
            firedDaysAgo = notification.lastFiredAt?.let { daysAgo(it, nowMillis, zone) },
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
