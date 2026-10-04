package com.secondmonday.hodith.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.data.orderedShareSections
import com.secondmonday.hodith.data.reorderVisibleSections
import com.secondmonday.hodith.data.share.ShareImageExporter
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The share screen's user-editable choices — everything [shareCardState] needs beyond the Case's own data. */
data class ShareSelection(
    val format: ShareCardFormat = ShareCardFormat.SQUARE,
    val displayNameOverride: String? = null,
    val selectedSections: Set<ShareInsightsSection> = ShareInsightsSection.entries.toSet(),
)

data class ShareUiState(
    val case: CaseEntity? = null,
    val events: List<EventWithTags> = emptyList(),
    val selection: ShareSelection = ShareSelection(),
    /** The device-wide Story section order, every section once — see [orderedShareSections]. */
    val sectionOrder: List<ShareInsightsSection> = ShareInsightsSection.entries,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val SHARE_FILE_NAME_PREFIX = "hodith-share-card"

@HiltViewModel
class ShareViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        private val shareImageExporter: ShareImageExporter,
        private val settingsRepository: SettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))

        private val selection = MutableStateFlow(ShareSelection())

        val uiState: StateFlow<ShareUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
                selection,
                settingsRepository.observeShareSectionOrder(),
            ) { case, events, selection, sectionOrder ->
                ShareUiState(
                    case = case,
                    events = events,
                    selection = selection,
                    sectionOrder = sectionOrder,
                    isLoading = false,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = ShareUiState(),
            )

        fun nowMillis(): Long = clock.nowMillis()

        fun setFormat(format: ShareCardFormat) {
            selection.update { it.copy(format = format) }
        }

        /** `null` (or blank) means fall back to the Case's own name — see [ShareSelection.displayNameOverride]. */
        fun setDisplayNameOverride(name: String?) {
            selection.update {
                it.copy(displayNameOverride = name?.take(CASE_NAME_MAX_LENGTH)?.takeIf { override -> override.isNotBlank() })
            }
        }

        fun setSectionSelected(
            section: ShareInsightsSection,
            selected: Boolean,
        ) {
            selection.update {
                it.copy(selectedSections = if (selected) it.selectedSections + section else it.selectedSections - section)
            }
        }

        /**
         * Moves the picker row at [from] to [to], where [available] is the Case's visible sections in
         * their current order. The new order is saved device-wide, so every Case and every later session
         * opens with it.
         */
        fun moveSection(
            available: List<ShareInsightsSection>,
            from: Int,
            to: Int,
        ) {
            viewModelScope.launch {
                val order = orderedShareSections(settingsRepository.observeShareSectionOrder().first())
                settingsRepository.setShareSectionOrder(reorderVisibleSections(order, available, from, to))
            }
        }

        private val _shareRequests = Channel<Uri>(Channel.BUFFERED)

        /** One-shot: each captured card image produces exactly one emission for the UI to hand to `ACTION_SEND`. */
        val shareRequests: Flow<Uri> = _shareRequests.receiveAsFlow()

        fun share(bitmap: Bitmap) {
            viewModelScope.launch {
                val uri = shareImageExporter.exportToShareUri(bitmap, SHARE_FILE_NAME_PREFIX)
                _shareRequests.send(uri)
            }
        }
    }
