package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
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
 * Everything the bell tab (Case Detail's third tab) needs to render: the Case's Watches
 * (raw entities — [watchCardState] turns each into display data), the Case's own
 * check-ins toggle and duration/intensity gating for the editor, and the Case's full event
 * history for computing each card's Now line / comparison line.
 */
data class WatchesUiState(
    val watches: List<WatchEntity> = emptyList(),
    val events: List<EventEntity> = emptyList(),
    val durationMode: DurationMode = DurationMode.NONE,
    val intensityEnabled: Boolean = false,
    val checkInsEnabled: Boolean = true,
    val caseCreatedAt: Long = 0L,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** A Watch's threshold range — shared by the bell-tab editor and [validateBackup]'s field-range check. */
internal val THRESHOLD_RANGE = 1..999

/**
 * Case Detail's bell tab, backed by its own [androidx.hilt.navigation.compose.hiltViewModel]
 * instance rather than sharing [CaseDetailViewModel]'s state — see
 * [TrendsListViewModel][com.secondmonday.hodith.viewmodel.TrendsListViewModel]'s own doc comment:
 * no destination in this app shares another screen's ViewModel; each full-screen/tab destination
 * re-queries the repository on its own.
 */
@HiltViewModel
class WatchesViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
        private val notificationPermissionRequestSignal: NotificationPermissionRequestSignal,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        val uiState: StateFlow<WatchesUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeWatchesForCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
            ) { case, watches, eventsWithTags ->
                WatchesUiState(
                    watches = watches,
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
                initialValue = WatchesUiState(),
            )

        fun createWatch(
            kind: WatchKind,
            threshold: Int,
            windowDays: Int?,
            expectedPer: ExpectedPer,
            metric: VerdictMetric,
            minIntensity: Int?,
        ) {
            if (kind == WatchKind.OFTEN && (windowDays == null || windowDays <= 0)) return
            viewModelScope.launch {
                repository.insertWatch(
                    WatchEntity(
                        caseId = caseId,
                        kind = kind,
                        threshold = threshold,
                        windowDays = if (kind == WatchKind.OFTEN) windowDays else null,
                        expectedPer = expectedPer,
                        metric = if (kind == WatchKind.OFTEN) metric else VerdictMetric.OCCURRENCE_COUNT,
                        minIntensity = if (kind == WatchKind.OFTEN) minIntensity else null,
                        enabled = true,
                        lastFiredAt = null,
                    ),
                )
                requestNotificationPermissionIfNeeded()
            }
        }

        /** Edit for an existing Watch — leaves [WatchEntity.armed]/[WatchEntity.lastFiredAt] history untouched. */
        fun updateWatch(
            watchId: Long,
            kind: WatchKind,
            threshold: Int,
            windowDays: Int?,
            expectedPer: ExpectedPer,
            metric: VerdictMetric,
            minIntensity: Int?,
        ) {
            if (kind == WatchKind.OFTEN && (windowDays == null || windowDays <= 0)) return
            viewModelScope.launch {
                val existing = repository.getWatch(watchId) ?: return@launch
                repository.updateWatch(
                    existing.copy(
                        kind = kind,
                        threshold = threshold,
                        windowDays = if (kind == WatchKind.OFTEN) windowDays else null,
                        expectedPer = expectedPer,
                        metric = if (kind == WatchKind.OFTEN) metric else VerdictMetric.OCCURRENCE_COUNT,
                        minIntensity = if (kind == WatchKind.OFTEN) minIntensity else null,
                    ),
                )
            }
        }

        fun setEnabled(
            watchId: Long,
            enabled: Boolean,
        ) {
            viewModelScope.launch {
                val watch = repository.getWatch(watchId) ?: return@launch
                repository.updateWatch(watch.copy(enabled = enabled))
            }
        }

        fun deleteWatch(watchId: Long) {
            viewModelScope.launch {
                val watch = repository.getWatch(watchId) ?: return@launch
                repository.deleteWatch(watch)
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
 * Everything a bell-tab Watch card needs to render — settings line, Now line, and (OFTEN
 * only) the [Expectation]/[VerdictResult] pair the card renders as the tier-gated comparison
 * line — computed once as a pure function of the Watch, the Case's full event history, its
 * duration mode, its creation time, and now. Mirrors `insightsTabState`'s
 * pure-function-called-via-`remember` pattern (ui/casedetail/CaseDetailScreen.kt) rather than living
 * inside [WatchesViewModel]. Voice formatting itself stays in the Composable.
 */
data class WatchCardState(
    val id: Long,
    val kind: WatchKind,
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

/** See [WatchCardState]'s own doc comment. */
internal fun watchCardState(
    watch: WatchEntity,
    events: List<EventEntity>,
    durationMode: DurationMode,
    caseCreatedAt: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): WatchCardState {
    val base =
        WatchCardState(
            id = watch.id,
            kind = watch.kind,
            enabled = watch.enabled,
            threshold = watch.threshold,
            expectedPer = watch.expectedPer,
            lookbackDays = watch.windowDays,
            metric = watch.metric,
            minIntensity = watch.minIntensity,
            tracksDuration = durationMode.tracksDuration,
            firedDaysAgo = watch.lastFiredAt?.let { daysBetween(it, now, zone) },
            observedRate = null,
            silentDays = null,
            expectation = null,
            verdictResult = null,
        )
    return when (watch.kind) {
        WatchKind.OFTEN -> {
            val (expectation, filteredEvents) = expectationInputsFor(watch, events, now)
            val result = computeVerdict(expectation, filteredEvents, now, durationMode, zone)
            base.copy(observedRate = result.observedRate, expectation = expectation, verdictResult = result)
        }
        WatchKind.QUIET -> {
            val anchor = silenceAnchorForEvents(events, durationMode, now) ?: caseCreatedAt
            base.copy(silentDays = daysBetween(anchor, now, zone))
        }
    }
}
