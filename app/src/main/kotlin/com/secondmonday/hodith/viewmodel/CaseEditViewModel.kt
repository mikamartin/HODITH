package com.secondmonday.hodith.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.Clock
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

enum class CheckInOption { DEFAULT, CUSTOM, OFF }

data class CaseEditUiState(
    val isEditing: Boolean = false,
    val isLoading: Boolean = true,
    val name: String = "",
    val description: String = "",
    val icon: String? = null,
    val logFlow: LogFlow = LogFlow.DETAIL_SHEET,
    val durationMode: DurationMode = DurationMode.NONE,
    val intensityEnabled: Boolean = false,
    val pinned: Boolean = false,
    val checkInOption: CheckInOption = CheckInOption.DEFAULT,
    val checkInCustomDays: String = "",
    val showNameError: Boolean = false,
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
        private val clock: Clock,
        private val widgetRefresher: WidgetRefresher,
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

        fun onNameChange(value: String) = _uiState.update { it.copy(name = value, showNameError = false) }

        fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

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

        fun onPinnedToggle(pinned: Boolean) = _uiState.update { it.copy(pinned = pinned) }

        fun onCheckInOptionChange(option: CheckInOption) = _uiState.update { it.copy(checkInOption = option) }

        fun onCheckInCustomDaysChange(value: String) = _uiState.update { it.copy(checkInCustomDays = value.filter(Char::isDigit)) }

        fun save() {
            val state = _uiState.value
            val validation = validateCaseEdit(state.name, state.icon)
            if (!validation.isValid) {
                _uiState.update {
                    it.copy(showNameError = !validation.nameValid, showIconError = !validation.iconValid)
                }
                return
            }

            viewModelScope.launch {
                val name = state.name.trim()
                val description = state.description.trim().takeIf { it.isNotEmpty() }
                val icon = requireNotNull(state.icon)
                val checkInDays = checkInDaysFor(state.checkInOption, state.checkInCustomDays)
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
                            pinned = state.pinned,
                            checkInDays = checkInDays,
                        ),
                    )
                } else {
                    val sortOrder = repository.observeActiveCases().first().size
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
                            pinned = state.pinned,
                            checkInDays = checkInDays,
                            lastCheckInAt = null,
                            sortOrder = sortOrder,
                            archived = false,
                        ),
                    )
                }
                // pinned/archived both affect what the List widget shows — refresh it whenever
                // either could have changed, not just from the widget's own configure flow.
                widgetRefresher.refreshListWidget()
                _uiState.update { it.copy(isSaved = true) }
            }
        }

        fun archive() {
            val current = existingCase ?: return
            viewModelScope.launch {
                repository.updateCase(current.copy(archived = true))
                widgetRefresher.refreshListWidget()
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
        pinned = pinned,
        canArchive = true,
        checkInOption =
            when (checkInDays) {
                null -> CheckInOption.DEFAULT
                0 -> CheckInOption.OFF
                else -> CheckInOption.CUSTOM
            },
        checkInCustomDays = checkInDays?.takeIf { it > 0 }?.toString().orEmpty(),
    )

internal fun checkInDaysFor(
    option: CheckInOption,
    customDays: String,
): Int? =
    when (option) {
        CheckInOption.DEFAULT -> null
        CheckInOption.OFF -> 0
        CheckInOption.CUSTOM -> customDays.toIntOrNull()?.takeIf { it > 0 }
    }

/**
 * Pure so the required-field rule is unit-testable on the JVM without a repository or Hilt,
 * same pattern as [homeCaseRows].
 */
internal data class CaseEditValidation(
    val nameValid: Boolean,
    val iconValid: Boolean,
) {
    val isValid: Boolean get() = nameValid && iconValid
}

internal fun validateCaseEdit(
    name: String,
    icon: String?,
): CaseEditValidation = CaseEditValidation(nameValid = name.isNotBlank(), iconValid = icon != null)

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
