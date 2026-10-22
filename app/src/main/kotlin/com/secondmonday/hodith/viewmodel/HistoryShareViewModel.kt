package com.secondmonday.hodith.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.pdf.HistoryPdfContent
import com.secondmonday.hodith.data.pdf.PdfExporter
import com.secondmonday.hodith.data.share.ShareImageExporter
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.domain.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

/**
 * History Share's user-editable choices, mirroring [ShareSelection]'s shape. [dateTo] and [dateFrom]
 * are both local-day-boundary millis (end-of-day and start-of-day respectively, see
 * [endOfDayMillis]/[startOfDayMillis]) rather than arbitrary instants, since the picker only ever
 * lets a user choose a calendar date. [dateFrom] `null` means "since the beginning"; [dateTo] is
 * never null (defaults to today, per spec §13, and can't move into the future).
 */
data class HistoryShareSelection(
    val sortOrder: ChronologicalOrder = ChronologicalOrder.NEWEST_FIRST,
    val dateFrom: Long? = null,
    val dateTo: Long,
    val fields: Set<HistoryRowField> = HistoryRowField.entries.toSet(),
)

data class HistoryShareUiState(
    val case: CaseEntity? = null,
    val events: List<EventWithTags> = emptyList(),
    val selection: HistoryShareSelection,
    val isLoading: Boolean = true,
)

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val HISTORY_SHARE_FILE_NAME_PREFIX = "hodith-history-share-card"
private const val HISTORY_PDF_FILE_NAME_PREFIX = "hodith-history-record"

/** Local start-of-day millis for [date] in this zone. */
internal fun TimeZone.startOfDayMillis(date: LocalDate): Long = date.atStartOfDayIn(this).toEpochMilliseconds()

/** Local end-of-day millis (23:59:59.999) for [date] in this zone — the inclusive upper bound [filterAndSortEvents] expects. */
internal fun TimeZone.endOfDayMillis(date: LocalDate): Long = date.plus(1, DateTimeUnit.DAY).atStartOfDayIn(this).toEpochMilliseconds() - 1

internal fun Long.toLocalDateIn(zone: TimeZone): LocalDate = Instant.fromEpochMilliseconds(this).toLocalDateTime(zone).date

@HiltViewModel
class HistoryShareViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        private val shareImageExporter: ShareImageExporter,
        private val pdfExporter: PdfExporter,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))
        private val zone = TimeZone.currentSystemDefault()

        private val selection =
            MutableStateFlow(
                HistoryShareSelection(dateTo = zone.endOfDayMillis(clock.nowMillis().toLocalDateIn(zone))),
            )

        val uiState: StateFlow<HistoryShareUiState> =
            combine(
                repository.observeCase(caseId),
                repository.observeEventsWithTagsForCase(caseId),
                selection,
            ) { case, events, selection ->
                HistoryShareUiState(case = case, events = events, selection = selection, isLoading = false)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = HistoryShareUiState(selection = selection.value),
            )

        fun nowMillis(): Long = clock.nowMillis()

        fun setSortOrder(order: ChronologicalOrder) {
            selection.update { it.copy(sortOrder = order) }
        }

        /** [pickedDate] is floor-capped by the caller's [androidx.compose.material3.SelectableDates] at the current [HistoryShareSelection.dateTo]. */
        fun setDateFrom(pickedDate: LocalDate?) {
            selection.update { it.copy(dateFrom = pickedDate?.let { zone.startOfDayMillis(it) }) }
        }

        /** [pickedDate] is ceiling-capped by the caller at today — see [nowMillis]. */
        fun setDateTo(pickedDate: LocalDate) {
            selection.update { it.copy(dateTo = zone.endOfDayMillis(pickedDate)) }
        }

        fun setFieldSelected(
            field: HistoryRowField,
            selected: Boolean,
        ) {
            selection.update { it.copy(fields = if (selected) it.fields + field else it.fields - field) }
        }

        private val _shareRequests = Channel<Uri>(Channel.BUFFERED)
        val shareRequests: Flow<Uri> = _shareRequests.receiveAsFlow()

        fun share(bitmap: Bitmap) {
            viewModelScope.launch {
                val uri = shareImageExporter.exportToShareUri(bitmap, HISTORY_SHARE_FILE_NAME_PREFIX)
                _shareRequests.send(uri)
            }
        }

        private val _pdfRequests = Channel<Uri>(Channel.BUFFERED)
        val pdfRequests: Flow<Uri> = _pdfRequests.receiveAsFlow()

        /** [content] arrives fully assembled by the Composable (Voice text, uncapped rows); this just writes it and emits the resulting Uri - no bitmap capture needed, unlike [ShareViewModel.sharePdf]. */
        fun sharePdf(content: HistoryPdfContent) {
            viewModelScope.launch {
                val uri = pdfExporter.exportHistoryPdf(content, HISTORY_PDF_FILE_NAME_PREFIX)
                _pdfRequests.send(uri)
            }
        }
    }
