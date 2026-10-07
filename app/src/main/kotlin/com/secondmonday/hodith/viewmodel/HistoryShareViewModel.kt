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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
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

/** Local start-of-day millis for [date] in this zone. */
internal fun ZoneId.startOfDayMillis(date: LocalDate): Long =
    date
        .atStartOfDay(this)
        .toInstant()
        .toEpochMilli()

/** Local end-of-day millis (23:59:59.999) for [date] in this zone — the inclusive upper bound [filterAndSortEvents] expects. */
internal fun ZoneId.endOfDayMillis(date: LocalDate): Long =
    date
        .plusDays(1)
        .atStartOfDay(this)
        .toInstant()
        .toEpochMilli() - 1

internal fun Long.toLocalDateIn(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

@HiltViewModel
class HistoryShareViewModel
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
        private val shareImageExporter: ShareImageExporter,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val caseId: Long = requireNotNull(savedStateHandle.get<Long>("caseId"))
        private val zone = ZoneId.systemDefault()

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
    }
