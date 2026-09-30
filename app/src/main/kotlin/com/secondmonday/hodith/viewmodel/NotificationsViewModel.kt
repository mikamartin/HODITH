package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.NotificationEntity
import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.Expectation
import com.secondmonday.hodith.domain.VerdictResult
import com.secondmonday.hodith.domain.computeVerdict
import com.secondmonday.hodith.domain.daysBetween
import com.secondmonday.hodith.domain.expectationInputsFor
import com.secondmonday.hodith.domain.silenceAnchorForEvents
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Everything the bell tab (Case Detail's third tab) needs to render: the Case's Notifications
 * (raw entities — [notificationCardState] turns each into display data), the Case's own
 * check-ins toggle and duration/intensity gating for the editor, and the Case's full event
 * history for computing each card's Now line / comparison line.
 */
data class NotificationsUiState(
    val notifications: List<NotificationEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val durationMode: DurationMode = DurationMode.NONE,
    val intensityEnabled: Boolean = false,
    val checkInsEnabled: Boolean = true,
    val caseCreatedAt: Long = 0L,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** A Notification's threshold range — shared by the bell-tab editor and [validateBackup]'s field-range check. */
internal val THRESHOLD_RANGE = 1..999

/**
 * Case Detail's bell tab, backed by its own [androidx.hilt.navigation.compose.hiltViewModel]
 * instance rather than sharing [CaseDetailViewModel]'s state — see
 * [TrendsListViewModel][com.secondmonday.hodith.viewmodel.TrendsListViewModel]'s own doc comment:
 * no destination in this app shares another screen's ViewModel; each full-screen/tab destination
 * re-queries the repository on its own.
 */
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
            combine(
                repository.observeCase(caseId),
                repository.observeNotificationsForCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
            ) { case, notifications, eventsWithTags ->
                NotificationsUiState(
                    notifications = notifications,
                    events = eventsWithTags.map { it.event },
                    durationMode = case?.durationMode ?: DurationMode.NONE,
                    intensityEnabled = case?.intensityEnabled ?: false,
                    checkInsEnabled = case?.checkInsEnabled ?: true,
                    caseCreatedAt = case?.createdAt ?: 0L,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = NotificationsUiState(),
            )

        fun createNotification(
            kind: NotificationKind,
            threshold: Int,
            windowDays: Int?,
            expectedPer: ExpectedPer,
            metric: VerdictMetric,
            minIntensity: Int?,
        ) {
            if (kind == NotificationKind.OFTEN && (windowDays == null || windowDays <= 0)) return
            viewModelScope.launch {
                repository.insertNotification(
                    NotificationEntity(
                        caseId = caseId,
                        kind = kind,
                        threshold = threshold,
                        windowDays = if (kind == NotificationKind.OFTEN) windowDays else null,
                        expectedPer = expectedPer,
                        metric = if (kind == NotificationKind.OFTEN) metric else VerdictMetric.OCCURRENCE_COUNT,
                        minIntensity = if (kind == NotificationKind.OFTEN) minIntensity else null,
                        enabled = true,
                        lastFiredAt = null,
                    ),
                )
                requestNotificationPermissionIfNeeded()
            }
        }

        /** Edit for an existing Notification — leaves [NotificationEntity.armed]/[NotificationEntity.lastFiredAt] history untouched. */
        fun updateNotification(
            notificationId: Long,
            kind: NotificationKind,
            threshold: Int,
            windowDays: Int?,
            expectedPer: ExpectedPer,
            metric: VerdictMetric,
            minIntensity: Int?,
        ) {
            if (kind == NotificationKind.OFTEN && (windowDays == null || windowDays <= 0)) return
            viewModelScope.launch {
                val existing = repository.getNotification(notificationId) ?: return@launch
                repository.updateNotification(
                    existing.copy(
                        kind = kind,
                        threshold = threshold,
                        windowDays = if (kind == NotificationKind.OFTEN) windowDays else null,
                        expectedPer = expectedPer,
                        metric = if (kind == NotificationKind.OFTEN) metric else VerdictMetric.OCCURRENCE_COUNT,
                        minIntensity = if (kind == NotificationKind.OFTEN) minIntensity else null,
                    ),
                )
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

        /**
         * Writes immediately, unlike [com.secondmonday.hodith.viewmodel.CaseEditViewModel]'s
         * buffered-until-save `onCheckInToggle` — the bell tab is a live settings page with no Save
         * button, so the switch must persist the moment it's flipped, same immediacy as [setEnabled].
         */
        fun setCheckInsEnabled(enabled: Boolean) {
            viewModelScope.launch {
                val case = repository.observeCase(caseId).first() ?: return@launch
                repository.updateCase(case.copy(checkInsEnabled = enabled))
            }
        }

        private suspend fun requestNotificationPermissionIfNeeded() {
            if (!settingsRepository.hasRequestedNotificationPermission()) {
                settingsRepository.setNotificationPermissionRequested()
                notificationPermissionRequestSignal.request()
            }
        }
    }

/**
 * Everything a bell-tab Notification card needs to render — settings line, Now line, and (OFTEN
 * only) the [Expectation]/[VerdictResult] pair [com.secondmonday.hodith.ui.common.ExpectationEarlyCard]/
 * [com.secondmonday.hodith.ui.common.ExpectationVerdictCard] render as the tier-gated comparison
 * line — computed once as a pure function of the Notification, the Case's full event history, its
 * duration mode, its creation time, and now. Mirrors `insightsTabState`'s
 * pure-function-called-via-`remember` pattern (ui/casedetail/CaseDetailScreen.kt) rather than living
 * inside [NotificationsViewModel]. Voice formatting itself stays in the Composable, the same split
 * `ExpectationVerdictCard`/`ExpectationEarlyCard` already draw between `VerdictResult` and their own
 * Voice calls.
 */
data class NotificationCardState(
    val id: Long,
    val kind: NotificationKind,
    val enabled: Boolean,
    val threshold: Int,
    val expectedPer: ExpectedPer,
    val lookbackDays: Int?,
    val metric: VerdictMetric,
    val minIntensity: Int?,
    val tracksDuration: Boolean,
    val firedDaysAgo: Long?,
    val observedRate: Double?,
    val silentDays: Long?,
    val expectation: Expectation?,
    val verdictResult: VerdictResult?,
)

/** See [NotificationCardState]'s own doc comment. */
internal fun notificationCardState(
    notification: NotificationEntity,
    events: List<EventEntity>,
    durationMode: DurationMode,
    caseCreatedAt: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): NotificationCardState {
    val base =
        NotificationCardState(
            id = notification.id,
            kind = notification.kind,
            enabled = notification.enabled,
            threshold = notification.threshold,
            expectedPer = notification.expectedPer,
            lookbackDays = notification.windowDays,
            metric = notification.metric,
            minIntensity = notification.minIntensity,
            tracksDuration = durationMode.tracksDuration,
            firedDaysAgo = notification.lastFiredAt?.let { daysBetween(it, now, zone) },
            observedRate = null,
            silentDays = null,
            expectation = null,
            verdictResult = null,
        )
    return when (notification.kind) {
        NotificationKind.OFTEN -> {
            val (expectation, filteredEvents) = expectationInputsFor(notification, events, now)
            val result = computeVerdict(expectation, filteredEvents, now, durationMode, zone)
            base.copy(observedRate = result.observedRate, expectation = expectation, verdictResult = result)
        }
        NotificationKind.QUIET -> {
            val anchor = silenceAnchorForEvents(events, durationMode, now) ?: caseCreatedAt
            base.copy(silentDays = daysBetween(anchor, now, zone))
        }
    }
}
