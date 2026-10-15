package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import com.secondmonday.hodith.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NO_CASE_ID = -1L
internal const val CASE_NAME_MAX_LENGTH = 60
internal const val CASE_DESCRIPTION_MAX_LENGTH = 90

/** One-shot signal to show a transient notice when `intensityEnabled` is flipped on a Case that already has logged events (spec §6). */
data class IntensityToggleNotice(
    val enabled: Boolean,
    val eventCount: Int,
)

data class CaseEditUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val logFlow: LogFlow = LogFlow.DETAIL_SHEET,
    val tracksDuration: Boolean = false,
    val intensityEnabled: Boolean = false,
    val showNameError: Boolean = false,
    val showDuplicateNameError: Boolean = false,
    val showDuplicateArchivedNameError: Boolean = false,
    val showIconError: Boolean = false,
    val isSaved: Boolean = false,
    val canArchive: Boolean = false,
    val isArchived: Boolean = false,
    /**
     * Events on this Case with no end time — open-ended running events or quick logs, whichever
     * the Case's `tracksDuration` flag was when they were logged. Gates both turn-on/turn-off
     * duration-tracking confirms (spec §6 Story U2).
     */
    val runningEventCount: Int = 0,
    /** Total events already logged on this Case, regardless of end-time state — gates the intensity-toggle notice (spec §6). */
    val loggedEventCount: Int = 0,
    val showTurnOffDurationConfirm: Boolean = false,
    val showTurnOnDurationConfirm: Boolean = false,
)

@HiltViewModel
class CaseEditViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
        private val widgetRefresher: WidgetRefresher,
        private val notificationPermissionRequestSignal: NotificationPermissionRequestSignal,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long? = savedStateHandle.get<Long>("caseId")?.takeIf { it != NO_CASE_ID }
        private var existingCase: CaseEntity? = null
        private var pendingTracksDuration: Boolean? = null
        private var stopRunningOnSave = false
        private var convertOpenEndedOnSave = false

        private val _uiState = MutableStateFlow(CaseEditUiState(isEditing = caseId != null))
        val uiState: StateFlow<CaseEditUiState> = _uiState.asStateFlow()

        private val _intensityToggleNotice = Channel<IntensityToggleNotice>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val intensityToggleNotice: Flow<IntensityToggleNotice> = _intensityToggleNotice.receiveAsFlow()

        init {
            val id = caseId
            if (id == null) {
                _uiState.update { it.copy(isLoading = false) }
            } else {
                viewModelScope.launch {
                    val case = repository.getCase(id)
                    existingCase = case
                    _uiState.value = case?.toUiState() ?: CaseEditUiState(isEditing = true, isLoading = false)
                }
                viewModelScope.launch {
                    repository.observeEventsWithTagsForCase(id).collect { events ->
                        val running = events.count { it.event.endedAt == null }
                        _uiState.update { it.copy(runningEventCount = running, loggedEventCount = events.size) }
                    }
                }
            }
        }

        fun onNameChange(value: String) =
            _uiState.update {
                it.copy(
                    name = value.take(CASE_NAME_MAX_LENGTH),
                    showNameError = false,
                    showDuplicateNameError = false,
                    showDuplicateArchivedNameError = false,
                )
            }

        fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value.take(CASE_DESCRIPTION_MAX_LENGTH)) }

        fun onIconSelect(icon: String) = _uiState.update { it.copy(icon = icon, showIconError = false) }

        fun onLogFlowChange(logFlow: LogFlow) = _uiState.update { it.copy(logFlow = logFlow) }

        fun onTracksDurationChange(tracksDuration: Boolean) {
            // A fresh decision supersedes any stamp a held-then-abandoned earlier toggle armed —
            // otherwise off→on→off (both confirmed) before a single save() would leave both flags
            // set and stamp `endedAt = now` on a days-old point. The transitions below are keyed
            // off the Case's *persisted* flag, not the unsaved in-memory one, so an intermediate
            // never-saved flip can't be "left".
            stopRunningOnSave = false
            convertOpenEndedOnSave = false
            pendingTracksDuration = null

            val persistedTracksDuration = existingCase?.tracksDuration
            val runningCount = _uiState.value.runningEventCount

            val turningOff = persistedTracksDuration == true && !tracksDuration
            if (turningOff && runningCount > 0) {
                pendingTracksDuration = tracksDuration
                _uiState.update { it.copy(showTurnOffDurationConfirm = true) }
                return
            }
            // Turning tracking on reinterprets every open-ended event as a live ongoing span
            // (spec §6) — hold the switch until the user confirms collapsing them to instant events.
            val turningOn = persistedTracksDuration == false && tracksDuration
            if (turningOn && runningCount > 0) {
                pendingTracksDuration = tracksDuration
                _uiState.update { it.copy(showTurnOnDurationConfirm = true) }
                return
            }
            applyTracksDuration(tracksDuration)
        }

        /** Proceed with a change that was held back by [showTurnOffDurationConfirm]. */
        fun confirmTurnOffDuration() {
            val next = pendingTracksDuration ?: return
            stopRunningOnSave = true
            _uiState.update { it.copy(showTurnOffDurationConfirm = false) }
            applyTracksDuration(next)
        }

        fun dismissTurnOffDuration() {
            pendingTracksDuration = null
            _uiState.update { it.copy(showTurnOffDurationConfirm = false) }
        }

        /** Proceed with a change that was held back by [showTurnOnDurationConfirm]. */
        fun confirmTurnOnDuration() {
            val next = pendingTracksDuration ?: return
            convertOpenEndedOnSave = true
            _uiState.update { it.copy(showTurnOnDurationConfirm = false) }
            applyTracksDuration(next)
        }

        fun dismissTurnOnDuration() {
            pendingTracksDuration = null
            _uiState.update { it.copy(showTurnOnDurationConfirm = false) }
        }

        private fun applyTracksDuration(tracksDuration: Boolean) {
            pendingTracksDuration = null
            _uiState.update {
                it.copy(tracksDuration = tracksDuration, logFlow = coerceLogFlow(it.logFlow, it.intensityEnabled))
            }
        }

        /**
         * Stamp `endedAt` on every currently open-ended event of [caseId] — used by both
         * tracks-duration transitions (spec §6 Story U2): [endedAt] returns `now` when turning
         * tracking off, the event's own `occurredAt` when turning it on.
         */
        private suspend fun stampOpenEndedEvents(
            caseId: Long,
            endedAt: (EventEntity) -> Long,
        ) {
            repository
                .observeEventsWithTagsForCase(caseId)
                .first()
                .filter { it.event.endedAt == null }
                .forEach { repository.updateEvent(it.event.copy(endedAt = endedAt(it.event))) }
        }

        fun onIntensityToggle(enabled: Boolean) {
            _uiState.update {
                it.copy(intensityEnabled = enabled, logFlow = coerceLogFlow(it.logFlow, enabled))
            }
            val loggedEventCount = _uiState.value.loggedEventCount
            if (loggedEventCount > 0) {
                _intensityToggleNotice.trySend(IntensityToggleNotice(enabled, loggedEventCount))
            }
        }

        fun save() {
            viewModelScope.launch {
                val state = _uiState.value
                // A blank name can't be a duplicate of anything meaningful, so the query is
                // skipped only then -- not also when the icon is unset, since a non-blank name
                // can still be a real duplicate that needs its own warning even while the icon
                // error is what's failing isValid.
                val allCases = if (state.name.isBlank()) emptyList() else repository.getAllCases()
                val validation = validateCaseEdit(state.name, state.icon, existingCase?.id, allCases)
                if (!validation.isValid) {
                    _uiState.update {
                        it.copy(
                            showNameError = !validation.nameValid,
                            showDuplicateNameError = validation.nameDuplicate,
                            showDuplicateArchivedNameError = validation.nameDuplicatesArchived,
                            showIconError = !validation.iconValid,
                        )
                    }
                    return@launch
                }

                val name = state.name.trim()
                val description = state.description.trim().takeIf { it.isNotEmpty() }
                val icon = requireNotNull(state.icon)
                val current = existingCase

                if (current != null) {
                    // checkInsEnabled is deliberately not copied here -- the form no longer asks
                    // for it (it's a live setting on the bell tab now, WatchesViewModel.
                    // setCheckInsEnabled), so .copy() leaves the Case's persisted value untouched.
                    repository.updateCase(
                        current.copy(
                            name = name,
                            description = description,
                            icon = icon,
                            logFlow = state.logFlow,
                            tracksDuration = state.tracksDuration,
                            intensityEnabled = state.intensityEnabled,
                        ),
                    )
                    // Turning tracking off: stop every still-running event at the moment of the switch.
                    if (stopRunningOnSave && !state.tracksDuration) {
                        val stoppedAt = clock.nowMillis()
                        stampOpenEndedEvents(current.id) { stoppedAt }
                    }
                    // Turning tracking on: collapse open-ended events to instant events at their own
                    // start (not "now"), so a point logged days ago doesn't become a live ongoing span.
                    if (convertOpenEndedOnSave && state.tracksDuration) {
                        stampOpenEndedEvents(current.id) { it.occurredAt }
                    }
                } else {
                    // A newly-created Case still needs a checkInsEnabled value at insert time even
                    // though the form no longer asks -- default true, matching this field's default
                    // everywhere else (CaseEditUiState/testCase/TestFixtures).
                    repository.insertCase(
                        CaseEntity(
                            name = name,
                            description = description,
                            icon = icon,
                            createdAt = clock.nowMillis(),
                            logFlow = state.logFlow,
                            tracksDuration = state.tracksDuration,
                            intensityEnabled = state.intensityEnabled,
                            checkInsEnabled = true,
                            lastCheckInAt = null,
                            sortOrder = allCases.count { !it.archived },
                            archived = false,
                        ),
                    )
                }
                // A widget currently showing this Case can be affected by name/icon/archived
                // changes made here — refresh in case one is.
                widgetRefresher.refreshWidgets()
                // A new Case starts with check-ins on by default (above); an edit no longer touches
                // checkInsEnabled at all, so this nudge -- originally keyed to the check-in toggle --
                // now triggers on Case creation itself, the only path here that can turn check-ins on.
                if (current == null && !settingsRepository.hasRequestedNotificationPermission()) {
                    settingsRepository.setNotificationPermissionRequested()
                    notificationPermissionRequestSignal.request()
                }
                _uiState.update { it.copy(isSaved = true) }
            }
        }

        fun archive() {
            val current = existingCase ?: return
            viewModelScope.launch {
                repository.updateCase(current.copy(archived = true))
                widgetRefresher.refreshWidgets()
                _uiState.update { it.copy(isArchived = true) }
            }
        }
    }

internal fun CaseEntity.toUiState() =
    CaseEditUiState(
        isEditing = true,
        isLoading = false,
        name = name,
        description = description.orEmpty(),
        icon = icon,
        logFlow = coerceLogFlow(logFlow, intensityEnabled),
        tracksDuration = tracksDuration,
        intensityEnabled = intensityEnabled,
        canArchive = true,
    )

/**
 * Pure so the required-field rule is unit-testable on the JVM without a repository or Hilt,
 * same pattern as [homeCaseRows].
 */
internal data class CaseEditValidation(
    val nameValid: Boolean,
    val nameDuplicate: Boolean,
    val nameDuplicatesArchived: Boolean,
    val iconValid: Boolean,
) {
    val isValid: Boolean get() = nameValid && !nameDuplicate && !nameDuplicatesArchived && iconValid
}

/**
 * [otherCases] is every other Case regardless of archived state: a name can't match an active
 * Case (blocked outright) or an archived one (surfaced as a separate warning, since the fix is
 * different — rename, restore the archived Case, or delete it).
 */
internal fun validateCaseEdit(
    name: String,
    icon: String?,
    editingCaseId: Long?,
    otherCases: List<CaseEntity>,
): CaseEditValidation {
    val trimmedName = name.trim()
    val nameValid = trimmedName.isNotBlank()
    val matchingOtherCases =
        if (nameValid) {
            otherCases.filter { it.id != editingCaseId && it.name.equals(trimmedName, ignoreCase = true) }
        } else {
            emptyList()
        }
    val nameDuplicate = matchingOtherCases.any { !it.archived }
    val nameDuplicatesArchived = !nameDuplicate && matchingOtherCases.any { it.archived }
    return CaseEditValidation(
        nameValid = nameValid,
        nameDuplicate = nameDuplicate,
        nameDuplicatesArchived = nameDuplicatesArchived,
        iconValid = icon != null,
    )
}

/**
 * One-tap is an instant insert with no fields, so it can never capture an intensity rating.
 * Duration tracking doesn't gate it (spec §6 Story U2): a tracked event starts open-ended by
 * default, exactly like a non-tracked one, so a one-tap quick-log is always a legitimate way to
 * start it — any duration gets stated later, from the Case's own log rows.
 */
internal fun isOneTapAllowed(intensityEnabled: Boolean): Boolean = !intensityEnabled

/** Auto-corrects away from [LogFlow.ONE_TAP] the moment it becomes invalid; never auto-restores it. */
internal fun coerceLogFlow(
    logFlow: LogFlow,
    intensityEnabled: Boolean,
): LogFlow =
    if (logFlow == LogFlow.ONE_TAP && !isOneTapAllowed(intensityEnabled)) {
        LogFlow.DETAIL_SHEET
    } else {
        logFlow
    }
