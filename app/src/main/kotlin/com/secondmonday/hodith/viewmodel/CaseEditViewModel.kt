package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.notification.NotificationPermissionRequestSignal
import com.secondmonday.hodith.widget.WidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val NO_CASE_ID = -1L
internal const val CASE_NAME_MAX_LENGTH = 60
internal const val CASE_DESCRIPTION_MAX_LENGTH = 280

data class CaseEditUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val logFlow: LogFlow = LogFlow.DETAIL_SHEET,
    val durationMode: DurationMode = DurationMode.NONE,
    val intensityEnabled: Boolean = false,
    val checkInsEnabled: Boolean = true,
    val showNameError: Boolean = false,
    val showDuplicateNameError: Boolean = false,
    val showIconError: Boolean = false,
    val isSaved: Boolean = false,
    val canArchive: Boolean = false,
    val isArchived: Boolean = false,
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

        private val _uiState = MutableStateFlow(CaseEditUiState(isEditing = caseId != null))
        val uiState: StateFlow<CaseEditUiState> = _uiState.asStateFlow()

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
            }
        }

        fun onNameChange(value: String) =
            _uiState.update {
                it.copy(name = value.take(CASE_NAME_MAX_LENGTH), showNameError = false, showDuplicateNameError = false)
            }

        fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value.take(CASE_DESCRIPTION_MAX_LENGTH)) }

        fun onIconSelect(icon: String) = _uiState.update { it.copy(icon = icon, showIconError = false) }

        fun onLogFlowChange(logFlow: LogFlow) = _uiState.update { it.copy(logFlow = logFlow) }

        fun onDurationModeChange(durationMode: DurationMode) =
            _uiState.update {
                it.copy(durationMode = durationMode, logFlow = coerceLogFlow(it.logFlow, durationMode, it.intensityEnabled))
            }

        fun onIntensityToggle(enabled: Boolean) =
            _uiState.update {
                it.copy(intensityEnabled = enabled, logFlow = coerceLogFlow(it.logFlow, it.durationMode, enabled))
            }

        fun onCheckInToggle(enabled: Boolean) = _uiState.update { it.copy(checkInsEnabled = enabled) }

        fun save() {
            viewModelScope.launch {
                val state = _uiState.value
                // Skip the query entirely when name/icon are already invalid on their own —
                // no duplicate check or sort-order lookup can change that outcome.
                val activeCases =
                    if (state.name.isBlank() || state.icon == null) emptyList() else repository.observeActiveCases().first()
                val validation = validateCaseEdit(state.name, state.icon, existingCase?.id, activeCases)
                if (!validation.isValid) {
                    _uiState.update {
                        it.copy(
                            showNameError = !validation.nameValid,
                            showDuplicateNameError = validation.nameDuplicate,
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
                    repository.updateCase(
                        current.copy(
                            name = name,
                            description = description,
                            icon = icon,
                            logFlow = state.logFlow,
                            durationMode = state.durationMode,
                            intensityEnabled = state.intensityEnabled,
                            checkInsEnabled = state.checkInsEnabled,
                        ),
                    )
                } else {
                    repository.insertCase(
                        CaseEntity(
                            name = name,
                            description = description,
                            icon = icon,
                            createdAt = clock.nowMillis(),
                            logFlow = state.logFlow,
                            durationMode = state.durationMode,
                            intensityEnabled = state.intensityEnabled,
                            hunchNudgeDismissed = false,
                            checkInsEnabled = state.checkInsEnabled,
                            lastCheckInAt = null,
                            sortOrder = activeCases.size,
                            archived = false,
                        ),
                    )
                }
                // A widget currently showing this Case can be affected by name/icon/archived
                // changes made here — refresh in case one is.
                widgetRefresher.refreshWidgets()
                if (state.checkInsEnabled && !settingsRepository.hasRequestedNotificationPermission()) {
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
        logFlow = coerceLogFlow(logFlow, durationMode, intensityEnabled),
        durationMode = durationMode,
        intensityEnabled = intensityEnabled,
        canArchive = true,
        checkInsEnabled = checkInsEnabled,
    )

/**
 * Pure so the required-field rule is unit-testable on the JVM without a repository or Hilt,
 * same pattern as [homeCaseRows].
 */
internal data class CaseEditValidation(
    val nameValid: Boolean,
    val nameDuplicate: Boolean,
    val iconValid: Boolean,
) {
    val isValid: Boolean get() = nameValid && !nameDuplicate && iconValid
}

/**
 * [otherActiveCases] should already be scoped to active (non-archived) Cases — a name only needs
 * to be unique among Cases currently visible on Home, not ones tucked away in the archive.
 */
internal fun validateCaseEdit(
    name: String,
    icon: String?,
    editingCaseId: Long?,
    otherActiveCases: List<CaseEntity>,
): CaseEditValidation {
    val trimmedName = name.trim()
    val nameValid = trimmedName.isNotBlank()
    val nameDuplicate =
        nameValid &&
            otherActiveCases.any { it.id != editingCaseId && it.name.equals(trimmedName, ignoreCase = true) }
    return CaseEditValidation(nameValid = nameValid, nameDuplicate = nameDuplicate, iconValid = icon != null)
}

/**
 * One-tap is an instant insert with no fields, so it can never capture a typed [DurationMode.MANUAL]
 * duration or an intensity rating. [DurationMode.START_STOP] is unaffected — a one-tap Start/Stop is
 * a legitimate flow.
 */
internal fun isOneTapAllowed(
    durationMode: DurationMode,
    intensityEnabled: Boolean,
): Boolean = durationMode != DurationMode.MANUAL && !intensityEnabled

/** Auto-corrects away from [LogFlow.ONE_TAP] the moment it becomes invalid; never auto-restores it. */
internal fun coerceLogFlow(
    logFlow: LogFlow,
    durationMode: DurationMode,
    intensityEnabled: Boolean,
): LogFlow =
    if (logFlow == LogFlow.ONE_TAP && !isOneTapAllowed(durationMode, intensityEnabled)) {
        LogFlow.DETAIL_SHEET
    } else {
        logFlow
    }
