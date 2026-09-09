package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.BigPictureDetail
import com.secondmonday.hodith.data.BigPictureDetailField
import com.secondmonday.hodith.domain.weeksInGrid
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.theme.BigPictureCellStyle
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalBigPictureCellStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.CalendarCase
import com.secondmonday.hodith.viewmodel.CalendarEvent
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatClockTime
import com.secondmonday.hodith.viewmodel.formatMediumDate
import com.secondmonday.hodith.viewmodel.formatSpanDateTime
import com.secondmonday.hodith.viewmodel.formatWeekdayDayDate
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The Big Picture flagship view (spec §9) — a scrollable multi-month calendar grid, every active
 * Case's icon in the cell for each day it has an event.
 *
 * Every month always renders its full case-icon grid — Big Picture's whole point is cross-case
 * identity/correlation, so no view here ever collapses that into a magnitude-only summary (that
 * belongs to the spec's separate, single-case calendar heatmap, not this multi-case view).
 * Continuous pinch-zoom is replaced entirely by taps and swipes: scrolling swipes through time,
 * a tap on the month title opens a quick-jump picker for reaching distant months instantly, a day
 * cell opens that day's events, and a dedicated leading chevron per week opens a week view — kept
 * as a separate tap target from the day cells since a click handler spanning the whole row would
 * never fire for taps landing on a day cell (the innermost clickable wins).
 *
 * Intensity is not encoded on the grid. Duration is, but only for an event whose active span
 * (spec §9) covers more than one calendar day: its icon then appears on every covered day and the
 * start day's icon is ringed in `primary`. A still-running event's span runs to today. A same-day
 * duration event reads exactly like a moment event. The day/week detail dialogs give those events
 * an "ongoing since …" / "lasted …" label on its own line — carrying the start date *and* time
 * once the event began on a different day than the row, since a bare clock time would read as
 * belonging to that row's day. What else those rows carry (note, tags, a same-day duration line,
 * intensity) follows the user's [BigPictureDetail] preference, edited from the filter row's edit
 * icon.
 *
 * Scroll range is [earliestMonth]..[currentMonth] inclusive, opening at the bottom (current
 * month). Replaces an earlier row-per-case/shared-horizontal-time-axis/pinch-zoom design, retired
 * after on-device testing showed it didn't read clearly (see PROGRESS.md for the build history).
 */
private const val MAX_ICONS_PER_CELL = 3
private val WEEK_CHEVRON_TOUCH_TARGET = 48.dp
private val CHIP_SHAPE = RoundedCornerShape(16.dp)

/** Stroke for the ring that marks the icon on the day a multi-day event started (spec §9). */
private val SPAN_START_RING_WIDTH = 1.5.dp

/**
 * One event as it lands on a given day cell. [isSpanStart]/[isSpanCarried] are both false for a
 * single-day event (a moment, or a duration event that opened and closed within one calendar
 * day) — it renders exactly as before. For a multi-day span they mark day 1 vs. the rest.
 */
private data class DayEvent(
    val event: CalendarEvent,
    val isSpanStart: Boolean,
    val isSpanCarried: Boolean,
)

/** One case icon in a day cell. [ringed] when a multi-day span for that case starts on this day. */
private data class DayCellIcon(
    val case: CalendarCase,
    val ringed: Boolean,
)

/**
 * The local dates [event] covers on the grid: its single day unless a finished event runs past
 * midnight (`occurredAt … endedAt`) or a still-running one does (`occurredAt … today`).
 */
private fun coveredDates(
    event: CalendarEvent,
    today: LocalDate,
    zone: ZoneId,
): List<LocalDate> {
    val startDate = Instant.ofEpochMilli(event.occurredAt).atZone(zone).toLocalDate()
    val endDate =
        when {
            event.isOngoing -> today
            event.endedAt != null -> Instant.ofEpochMilli(event.endedAt).atZone(zone).toLocalDate()
            else -> startDate
        }
    if (!endDate.isAfter(startDate)) return listOf(startDate)
    return generateSequence(startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(endDate) }.toList()
}

@Composable
fun BigPictureGrid(
    earliestMonth: YearMonth,
    currentMonth: YearMonth,
    cases: List<CalendarCase>,
    events: List<CalendarEvent>,
    today: LocalDate,
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    detail: BigPictureDetail = BigPictureDetail.DEFAULT,
    onToggleDetail: (BigPictureDetailField, Boolean) -> Unit = { _, _ -> },
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var visibleCaseIds by remember(cases) { mutableStateOf(cases.map { it.id }.toSet()) }
    // Scoped to visibleCaseIds, not every Case's events: a tag only offered by a currently-hidden
    // Case would otherwise let the Cases and Tags dialogs each look non-empty while their AND
    // silently shows nothing. Re-scoping also re-keys visibleTagNames back to "everything in the
    // new scope" whenever Case selection changes, so the reset always moves toward more results,
    // never toward that empty trap.
    val allTagNames =
        remember(events, visibleCaseIds) {
            events
                .filter { it.caseId in visibleCaseIds }
                .flatMap { it.tags }
                .distinct()
                .sorted()
        }
    var visibleTagNames by remember(allTagNames) { mutableStateOf(allTagNames.toSet()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedWeek by remember { mutableStateOf<List<LocalDate>?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val isEventVisible: (CalendarEvent) -> Boolean = { event ->
        event.caseId in visibleCaseIds && isTagVisible(event.tags, visibleTagNames, allTagNames.size)
    }

    val eventsByDay =
        remember(events, zoneId, today) {
            buildMap<LocalDate, MutableList<DayEvent>> {
                events.forEach { event ->
                    val dates = coveredDates(event, today, zoneId)
                    val multiDay = dates.size > 1
                    dates.forEachIndexed { index, date ->
                        getOrPut(date) { mutableListOf() }
                            .add(
                                DayEvent(
                                    event = event,
                                    isSpanStart = multiDay && index == 0,
                                    isSpanCarried = multiDay && index > 0,
                                ),
                            )
                    }
                }
            }
        }
    val caseById = remember(cases) { cases.associateBy { it.id } }
    val months =
        remember(earliestMonth, currentMonth) {
            generateSequence(earliestMonth) { it.plusMonths(1) }.takeWhile { !it.isAfter(currentMonth) }.toList()
        }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(months.size) {
        if (months.isNotEmpty()) listState.scrollToItem(months.lastIndex)
    }

    Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
        Column {
            FilterSummaryRow(
                cases = cases,
                visibleCaseIds = visibleCaseIds,
                onToggleCase = { caseId ->
                    visibleCaseIds = if (caseId in visibleCaseIds) visibleCaseIds - caseId else visibleCaseIds + caseId
                },
                onSetVisibleCaseIds = { visibleCaseIds = it },
                allTagNames = allTagNames,
                visibleTagNames = visibleTagNames,
                onToggleTag = { tag ->
                    visibleTagNames = if (tag in visibleTagNames) visibleTagNames - tag else visibleTagNames + tag
                },
                onSetVisibleTagNames = { visibleTagNames = it },
                detail = detail,
                onToggleDetail = onToggleDetail,
            )
            WeekdayHeader(modifier = Modifier.padding(horizontal = 12.dp))
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(months) { month ->
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = "${month.monthLabel()} ›",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .wrapContentHeight(Alignment.CenterVertically)
                                    .clickable { showMonthPicker = true }
                                    .padding(bottom = 4.dp),
                        )
                        weeksInGrid(month)
                            .filter { week -> isPastOrToday(week.first(), today) }
                            .forEach { week ->
                                WeekRow(
                                    week = week,
                                    month = month,
                                    today = today,
                                    eventsByDay = eventsByDay,
                                    caseById = caseById,
                                    isEventVisible = isEventVisible,
                                    onDayTap = { selectedDay = it },
                                    onWeekTap = { selectedWeek = week },
                                )
                            }
                    }
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthPickerDialog(
            months = months,
            onMonthPicked = { picked ->
                showMonthPicker = false
                scope.launch { listState.scrollToItem(months.indexOf(picked)) }
            },
            onDismiss = { showMonthPicker = false },
        )
    }
    selectedDay?.let { day ->
        DayDetailDialog(
            day = day,
            dayEvents = eventsByDay[day].orEmpty().filter { isEventVisible(it.event) },
            today = today,
            caseById = caseById,
            detail = detail,
            zoneId = zoneId,
            onOpenCase = onOpenCase,
            onDismiss = { selectedDay = null },
        )
    }
    selectedWeek?.let { week ->
        WeekDetailDialog(
            week = week,
            today = today,
            eventsByDay = eventsByDay,
            caseById = caseById,
            isEventVisible = isEventVisible,
            detail = detail,
            zoneId = zoneId,
            onOpenCase = onOpenCase,
            onDismiss = { selectedWeek = null },
        )
    }
}

/**
 * Two small trigger chips ("Cases N of M ▸" / "Tags N of M ▸") open the full picker in an
 * [InfoDialog] each; a combined read-only legend row below summarizes the current selection
 * (spec §9). The tag trigger and its dialog are omitted entirely when no event carries a tag,
 * same as the old always-expanded tag row.
 */
@Composable
private fun FilterSummaryRow(
    cases: List<CalendarCase>,
    visibleCaseIds: Set<Long>,
    onToggleCase: (Long) -> Unit,
    onSetVisibleCaseIds: (Set<Long>) -> Unit,
    allTagNames: List<String>,
    visibleTagNames: Set<String>,
    onToggleTag: (String) -> Unit,
    onSetVisibleTagNames: (Set<String>) -> Unit,
    detail: BigPictureDetail,
    onToggleDetail: (BigPictureDetailField, Boolean) -> Unit,
) {
    val voice = LocalVoice.current
    var showCasesDialog by remember { mutableStateOf(false) }
    var showTagsDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterTriggerChip(
                label = voice.bigPictureCasesFilterLabel,
                count = filterCountLabel(voice, visibleCaseIds.size, cases.size),
                onClick = { showCasesDialog = true },
            )
            if (allTagNames.isNotEmpty()) {
                FilterTriggerChip(
                    label = voice.bigPictureTagsFilterLabel,
                    count = filterCountLabel(voice, visibleTagNames.size, allTagNames.size),
                    onClick = { showTagsDialog = true },
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Edit which fields the day/week detail rows carry (spec §9) — the filters above stay
            // left-aligned, this stays pinned right.
            IconButton(onClick = { showDetailDialog = true }) {
                Icon(Icons.Filled.Edit, contentDescription = voice.bigPictureDetailEditDescription)
            }
        }
        FilterLegendRow(
            cases = cases,
            visibleCaseIds = visibleCaseIds,
            allTagNames = allTagNames,
            visibleTagNames = visibleTagNames,
        )
    }

    if (showCasesDialog) {
        InfoDialog(
            title = voice.bigPictureCasesFilterLabel,
            onDismiss = { showCasesDialog = false },
            leadingAction = {
                BulkSelectionToggle(
                    allSelected = visibleCaseIds.size == cases.size,
                    onSelectAll = { onSetVisibleCaseIds(cases.map { it.id }.toSet()) },
                    onClearAll = { onSetVisibleCaseIds(emptySet()) },
                )
            },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                cases.forEach { case ->
                    CaseFilterChip(case = case, selected = case.id in visibleCaseIds, onToggle = { onToggleCase(case.id) })
                }
            }
        }
    }
    if (showTagsDialog) {
        InfoDialog(
            title = voice.bigPictureTagsFilterLabel,
            onDismiss = { showTagsDialog = false },
            leadingAction = {
                BulkSelectionToggle(
                    allSelected = visibleTagNames.size == allTagNames.size,
                    onSelectAll = { onSetVisibleTagNames(allTagNames.toSet()) },
                    onClearAll = { onSetVisibleTagNames(emptySet()) },
                )
            },
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allTagNames.forEach { tag ->
                    TagFilterChip(tag = tag, selected = tag in visibleTagNames, onToggle = { onToggleTag(tag) })
                }
            }
        }
    }
    if (showDetailDialog) {
        InfoDialog(
            title = voice.bigPictureDetailDialogTitle,
            onDismiss = { showDetailDialog = false },
        ) {
            Column {
                BigPictureDetailField.entries.forEach { field ->
                    ToggleRow(
                        label = detailFieldLabel(field, voice),
                        checked = detail.has(field),
                        onCheckedChange = { onToggleDetail(field, it) },
                        modifier =
                            Modifier
                                .testTag(BIG_PICTURE_DETAIL_TOGGLE_TAG_PREFIX + field.name)
                                .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Flips the whole dialog's selection in one tap — "Select all" when not everything is selected, "Clear all" once it is. */
@Composable
private fun BulkSelectionToggle(
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
) {
    val voice = LocalVoice.current
    TextButton(onClick = if (allSelected) onClearAll else onSelectAll) {
        Text(if (allSelected) voice.bigPictureClearAllAction else voice.bigPictureSelectAllAction)
    }
}

private fun filterCountLabel(
    voice: Voice,
    selected: Int,
    total: Int,
) = if (selected == total) voice.bigPictureFilterCountAll else voice.bigPictureFilterCount(selected, total)

/** Test hook — the same field label also appears in the row behind the open dialog. */
internal const val BIG_PICTURE_DETAIL_TOGGLE_TAG_PREFIX = "bp_detail_toggle_"

private fun detailFieldLabel(
    field: BigPictureDetailField,
    voice: Voice,
): String =
    when (field) {
        BigPictureDetailField.NOTES -> voice.bigPictureDetailNotesLabel
        BigPictureDetailField.TAGS -> voice.bigPictureDetailTagsLabel
        BigPictureDetailField.DURATION -> voice.bigPictureDetailDurationLabel
        BigPictureDetailField.INTENSITY -> voice.bigPictureDetailIntensityLabel
    }

/** Read-only summary of the current selection; collapsing rules are spec §9's (see [bigPictureCaseLegend]/[bigPictureTagLegend]). */
@Composable
private fun FilterLegendRow(
    cases: List<CalendarCase>,
    visibleCaseIds: Set<Long>,
    allTagNames: List<String>,
    visibleTagNames: Set<String>,
) {
    val voice = LocalVoice.current
    val caseLegend = bigPictureCaseLegend(cases, visibleCaseIds)
    if (caseLegend is CaseLegendState.NoneSelected) {
        Text(
            text = voice.bigPictureNoCasesSelectedNote,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        return
    }
    val tagLegend = bigPictureTagLegend(allTagNames, visibleTagNames)
    if (caseLegend is CaseLegendState.AllSelected && tagLegend is TagLegendState.AllSelected) {
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (caseLegend) {
            CaseLegendState.AllSelected -> CaseGroupChip(voice.bigPictureAllCasesLabel)
            is CaseLegendState.Some -> caseLegend.cases.forEach { case -> CaseFilterChip(case = case, selected = true, onToggle = null) }
            CaseLegendState.NoneSelected -> Unit
        }
        if (allTagNames.isNotEmpty()) {
            when (tagLegend) {
                TagLegendState.AllSelected -> TagFilterChip(tag = voice.bigPictureAllTagsLabel, selected = true, onToggle = null)
                is TagLegendState.Some -> tagLegend.tags.forEach { tag -> TagFilterChip(tag = tag, selected = true, onToggle = null) }
                TagLegendState.UntaggedOnly -> TagFilterChip(tag = voice.bigPictureUntaggedOnlyLabel, selected = true, onToggle = null)
            }
        }
    }
}

private fun YearMonth.monthLabel(): String = "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"

@Composable
private fun MonthPickerDialog(
    months: List<YearMonth>,
    onMonthPicked: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    val voice = LocalVoice.current
    InfoDialog(
        title = voice.bigPictureMonthPickerTitle,
        onDismiss = onDismiss,
    ) {
        Column {
            months.asReversed().forEach { month ->
                Text(
                    text = month.monthLabel(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onMonthPicked(month) }
                            .padding(vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun WeekRow(
    week: List<LocalDate>,
    month: YearMonth,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<DayEvent>>,
    caseById: Map<Long, CalendarCase>,
    isEventVisible: (CalendarEvent) -> Boolean,
    onDayTap: (LocalDate) -> Unit,
    onWeekTap: () -> Unit,
) {
    val voice = LocalVoice.current
    // Plain only: no week-row border. Intense/Bright keep the existing outlineVariant border —
    // each theme's colors stay independent of Plain's changes.
    val weekRowModifier =
        if (LocalBigPictureCellStyle.current == BigPictureCellStyle.PLAIN) {
            Modifier.fillMaxWidth().padding(vertical = 2.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
        }
    Row(
        modifier = weekRowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(WEEK_CHEVRON_TOUCH_TARGET)
                    .clickable(onClickLabel = voice.bigPictureWeekViewDescription, onClick = onWeekTap),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
        week.forEach { day ->
            if (!isPastOrToday(day, today) || day.month != month.month) {
                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            } else {
                DayCell(
                    day = day,
                    isToday = day == today,
                    icons =
                        eventsByDay[day]
                            .orEmpty()
                            .filter { isEventVisible(it.event) }
                            .groupBy { it.event.caseId }
                            .mapNotNull { (caseId, dayEvents) ->
                                caseById[caseId]?.let { case ->
                                    DayCellIcon(case, ringed = dayEvents.any { it.isSpanStart })
                                }
                            }.sortedByDescending { it.ringed },
                    onClick = { onDayTap(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayDetailDialog(
    day: LocalDate,
    dayEvents: List<DayEvent>,
    today: LocalDate,
    caseById: Map<Long, CalendarCase>,
    detail: BigPictureDetail,
    zoneId: ZoneId,
    onOpenCase: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val voice = LocalVoice.current
    InfoDialog(
        title = formatMediumDate(day),
        onDismiss = onDismiss,
    ) {
        if (dayEvents.isEmpty()) {
            Text(voice.bigPictureDayDetailEmptyState)
        } else {
            // AlertDialog doesn't scroll its `text` slot on its own -- content taller than the
            // dialog's window just clips silently rather than scrolling.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                dayEvents.forEach { dayEvent ->
                    EventDetailRow(dayEvent, caseById[dayEvent.event.caseId], today, detail, zoneId, onOpenCase, onDismiss, voice)
                }
            }
        }
    }
}

@Composable
private fun WeekDetailDialog(
    week: List<LocalDate>,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<DayEvent>>,
    caseById: Map<Long, CalendarCase>,
    isEventVisible: (CalendarEvent) -> Boolean,
    detail: BigPictureDetail,
    zoneId: ZoneId,
    onOpenCase: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val voice = LocalVoice.current
    val validDays = week.filter { isPastOrToday(it, today) }
    InfoDialog(
        title = voice.bigPictureWeekDetailTitle(formatMediumDate(week.first())),
        onDismiss = onDismiss,
    ) {
        // AlertDialog doesn't scroll its `text` slot on its own -- content taller than the
        // dialog's window just clips silently rather than scrolling.
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            validDays.forEach { day ->
                val dayEvents = eventsByDay[day].orEmpty().filter { isEventVisible(it.event) }
                if (dayEvents.isNotEmpty()) {
                    Text(
                        text = formatWeekdayDayDate(day),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    dayEvents.forEach { dayEvent ->
                        EventDetailRow(dayEvent, caseById[dayEvent.event.caseId], today, detail, zoneId, onOpenCase, onDismiss, voice)
                    }
                }
            }
            if (validDays.all { day -> eventsByDay[day].orEmpty().none { isEventVisible(it.event) } }) {
                Text(voice.bigPictureWeekDetailEmptyState)
            }
        }
    }
}

@Composable
private fun EventDetailRow(
    dayEvent: DayEvent,
    case: CalendarCase?,
    today: LocalDate,
    detail: BigPictureDetail,
    zoneId: ZoneId,
    onOpenCase: (Long) -> Unit,
    onDismiss: () -> Unit,
    voice: Voice,
) {
    val event = dayEvent.event
    val use24Hour = LocalTimeFormat.current.is24Hour
    val startDate = Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalDate()
    val startTime =
        formatClockTime(
            Instant.ofEpochMilli(event.occurredAt).atZone(zoneId).toLocalTime(),
            use24Hour,
        )
    val timeLabel =
        when {
            event.isOngoing ->
                voice.bigPictureEventOngoingSince(
                    // Started today → just the time (the dialog title already carries the date).
                    // Started earlier → date + time, so a carried-day row keeps the real start time.
                    if (startDate == today) startTime else formatSpanDateTime(event.occurredAt, use24Hour, zoneId),
                )
            dayEvent.isSpanStart || dayEvent.isSpanCarried ->
                voice.bigPictureEventSpanRange(
                    formatSpanDateTime(event.occurredAt, use24Hour, zoneId),
                    formatSpanDateTime(event.endedAt!!, use24Hour, zoneId),
                )
            else -> startTime
        }
    // A bare clock time sits inline after the Case name; the wordier "ongoing since …" / span-range
    // labels take their own line so a long Case name can't squeeze them into an ugly wrap.
    val timeOnOwnLine = event.isOngoing || dayEvent.isSpanStart || dayEvent.isSpanCarried
    // Duration/intensity line (spec §9), governed by the user's overview-detail preference. A
    // multi-day span and a still-running event already state their extent in [timeLabel], so the
    // "lasted N" line is limited to same-day finished events; `eventDetailSummary` renders only
    // the duration/intensity part here — note and tags get their own lines below.
    val showDuration = detail.duration && !dayEvent.isSpanStart && !dayEvent.isSpanCarried && !event.isOngoing
    val metaLine =
        eventDetailSummary(
            occurredAt = event.occurredAt,
            endedAt = event.endedAt.takeIf { showDuration },
            intensity = event.intensity,
            note = null,
            tagNames = emptyList(),
            voice = voice,
            isOngoing = event.isOngoing,
            showIntensity = detail.intensity,
        )
    val note = event.note?.takeIf { detail.notes && it.isNotBlank() }
    // Icon + name and, for a same-day event, its clock time are one wrapping text flow — a `Row`
    // of two `Text`s squeezes the time into an ugly wrap when the Case name is long, so they share
    // one `Text` and the time is a trailing muted span instead.
    val heading =
        buildAnnotatedString {
            append("${case?.icon.orEmpty()} ${case?.name.orEmpty()}")
            if (!timeOnOwnLine) {
                append("  ")
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        fontWeight = MaterialTheme.typography.labelSmall.fontWeight,
                    ),
                ) {
                    append(timeLabel)
                }
            }
        }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onDismiss()
                    onOpenCase(event.caseId)
                }.padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = heading, style = MaterialTheme.typography.titleSmall)
        if (timeOnOwnLine) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (metaLine != null) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (detail.tags && event.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                event.tags.forEach { tag -> TagPill(tag) }
            }
        }
    }
}

/** [onToggle] null renders a read-only pill (no click target) — used by the legend row's static chips. */
@Composable
private fun CaseFilterChip(
    case: CalendarCase,
    selected: Boolean,
    onToggle: (() -> Unit)?,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT -> BrightCaseFilterChip(case = case, selected = selected, onToggle = onToggle)
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> {
            val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
            val border = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
            val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier =
                    Modifier
                        .clip(CHIP_SHAPE)
                        .background(background)
                        .border(1.dp, border, CHIP_SHAPE)
                        .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(case.icon)
                Text(case.name, style = MaterialTheme.typography.labelSmall, color = content)
            }
        }
    }
}

/** [onToggle] null renders a read-only pill (no click target) — used by the legend row's static chips. */
@Composable
private fun TagFilterChip(
    tag: String,
    selected: Boolean,
    onToggle: (() -> Unit)?,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT -> BrightTagFilterChip(tag = tag, selected = selected, onToggle = onToggle)
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> {
            val background = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
            val border = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.outlineVariant
            val content = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                modifier =
                    Modifier
                        .clip(CHIP_SHAPE)
                        .background(background)
                        .border(1.dp, border, CHIP_SHAPE)
                        .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/**
 * Shared Bright chip pill: tint-wash background + hairline border when selected, plain surface
 * otherwise. Bright's selected-state ring was specced as a zero-blur `0 0 0 3px` spread, which
 * Compose has no direct primitive for; it's approximated here as an outer [Modifier.border] on a
 * Box padded out by the same 3dp, which at a 10%-alpha tint reads as the same soft halo.
 * [CaseFilterChip] and [TagFilterChip] share this rather than each
 * reimplementing the pill+ring chrome, since only their inner content (icon+name vs. tag text)
 * differs. [onToggle] null renders a read-only pill, same as the other two.
 */
@Composable
private fun BrightChip(
    selected: Boolean,
    onToggle: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    val tint = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val shape = CHIP_SHAPE
    Box(
        modifier =
            if (selected) {
                Modifier.border(3.dp, tint.copy(alpha = 0.10f), RoundedCornerShape(19.dp)).padding(3.dp)
            } else {
                Modifier
            },
    ) {
        Row(
            modifier =
                Modifier
                    .clip(shape)
                    .background(if (selected) lerp(surface, tint, 0.14f) else surface)
                    .border(
                        width = 1.dp,
                        color = if (selected) tint.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        shape = shape,
                    ).then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun BrightCaseFilterChip(
    case: CalendarCase,
    selected: Boolean,
    onToggle: (() -> Unit)?,
) {
    BrightChip(selected = selected, onToggle = onToggle) {
        Text(case.icon)
        Text(
            text = case.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BrightTagFilterChip(
    tag: String,
    selected: Boolean,
    onToggle: (() -> Unit)?,
) {
    BrightChip(selected = selected, onToggle = onToggle) {
        Text(
            text = tag,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Small trigger chip ("Cases N of M ▸") opening the full picker dialog; always the neutral/unselected pill look — the label+count communicate state, not the chip's own selection styling. */
@Composable
private fun FilterTriggerChip(
    label: String,
    count: String,
    onClick: () -> Unit,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            BrightChip(selected = false, onToggle = onClick) {
                FilterTriggerChipContent(label, count)
            }
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE ->
            Row(
                modifier =
                    Modifier
                        .clip(CHIP_SHAPE)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CHIP_SHAPE)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterTriggerChipContent(label, count)
            }
    }
}

@Composable
private fun RowScope.FilterTriggerChipContent(
    label: String,
    count: String,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    Text(count, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("▸", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

/** Case-toned aggregate legend label ("All Cases") — text only (no per-case icon), always read-only. */
@Composable
private fun CaseGroupChip(text: String) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            BrightChip(selected = true, onToggle = null) {
                Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier =
                    Modifier
                        .clip(CHIP_SHAPE)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(1.dp, MaterialTheme.colorScheme.secondaryContainer, CHIP_SHAPE)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
            )
    }
}

/** Exercises both chip kinds' on/off states side by side — they share [BrightChip]'s pill/ring but differ in content. */
@Composable
private fun FilterChipBrightPreviewContent() {
    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CaseFilterChip(case = CalendarCase(id = 1, icon = "🏃", name = "Runs"), selected = true, onToggle = {})
        CaseFilterChip(case = CalendarCase(id = 2, icon = "☕", name = "Coffee"), selected = false, onToggle = {})
        TagFilterChip(tag = "weekend", selected = true, onToggle = {})
        TagFilterChip(tag = "solo", selected = false, onToggle = {})
    }
}

@Preview(name = "FilterChip — Bright light", showBackground = true, widthDp = 380, heightDp = 100)
@Composable
private fun FilterChipBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            FilterChipBrightPreviewContent()
        }
    }
}

@Preview(name = "FilterChip — Bright dark", showBackground = true, widthDp = 380, heightDp = 100)
@Composable
private fun FilterChipBrightDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            FilterChipBrightPreviewContent()
        }
    }
}

/** Exercises the trigger-chip-row + read-only legend layout that replaced the old always-expanded [FilterChipBrightPreviewContent] rows. */
@Composable
private fun FilterSummaryRowBrightPreviewContent() {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterTriggerChip(label = "Cases", count = "2 of 3", onClick = {})
            FilterTriggerChip(label = "Tags", count = "All", onClick = {})
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Filled.Edit, contentDescription = "Edit which detail the rows show") }
        }
        FlowRow(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CaseFilterChip(case = CalendarCase(id = 1, icon = "🏃", name = "Runs"), selected = true, onToggle = null)
            CaseGroupChip(text = "All tags")
        }
    }
}

@Preview(name = "FilterSummaryRow — Bright light", showBackground = true, widthDp = 380, heightDp = 140)
@Composable
private fun FilterSummaryRowBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            FilterSummaryRowBrightPreviewContent()
        }
    }
}

@Preview(name = "FilterSummaryRow — Bright dark", showBackground = true, widthDp = 380, heightDp = 140)
@Composable
private fun FilterSummaryRowBrightDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        CompositionLocalProvider(LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT) {
            FilterSummaryRowBrightPreviewContent()
        }
    }
}

/** Read-only tag badge for event detail rows — no [onToggle], unlike [TagFilterChip]. */
@Composable
private fun TagPill(tag: String) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier =
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun WeekdayHeader(modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.size(WEEK_CHEVRON_TOUCH_TARGET))
        DayOfWeek.entries.forEach { day ->
            val isWeekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
            Text(
                text = day.name.take(1),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = if (isWeekend) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * Dispatches to the active theme's bespoke cell treatment (spec §12). The three variants below
 * are the only place in this file that branch on [BigPictureCellStyle]; everything else (filter
 * chips, week borders) is unchanged and stays theme-agnostic via [MaterialTheme] tokens alone.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<DayCellIcon>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (LocalBigPictureCellStyle.current) {
        BigPictureCellStyle.PLAIN -> PlainDayCell(day, isToday, icons, onClick, modifier)
        BigPictureCellStyle.INTENSE -> IntenseDayCell(day, isToday, icons, onClick, modifier)
        BigPictureCellStyle.BRIGHT -> BrightDayCell(day, isToday, icons, onClick, modifier)
    }
}

@Composable
private fun PlainDayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<DayCellIcon>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cellColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val dayNumberColor = MaterialTheme.colorScheme.onSurface
    val todayBorder = MaterialTheme.colorScheme.primary

    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(cellColor)
                .then(if (isToday) Modifier.border(2.dp, todayBorder, RoundedCornerShape(8.dp)) else Modifier)
                .clickable(onClick = onClick)
                .padding(4.dp),
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isToday) todayBorder else dayNumberColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            icons.take(MAX_ICONS_PER_CELL).forEach { (case, ringed) ->
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .then(
                                if (ringed) {
                                    Modifier.border(SPAN_START_RING_WIDTH, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(case.icon, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (icons.size > MAX_ICONS_PER_CELL) {
                Text(
                    "+${icons.size - MAX_ICONS_PER_CELL}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Dossier-file read: a neutral tab carries the day number; crimson is reserved for today. */
@Composable
private fun IntenseDayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<DayCellIcon>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val tabColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val tabContentColor =
        if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(2.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(onClick = onClick),
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = tabContentColor,
            modifier = Modifier.fillMaxWidth().background(tabColor).padding(horizontal = 3.dp, vertical = 1.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline))
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            icons.take(MAX_ICONS_PER_CELL).forEach { (case, ringed) ->
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                if (ringed) SPAN_START_RING_WIDTH else 1.dp,
                                if (ringed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(case.icon, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (icons.size > MAX_ICONS_PER_CELL) {
                Text(
                    "+${icons.size - MAX_ICONS_PER_CELL}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.primary).padding(horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * Playful-reveal read: a floating shadowed card, case icons as a fanned sticker cluster. Today's
 * cell also carries a blurred primary-tint ring (Bright's today-cell treatment) built from the
 * same blur+tint technique as [com.secondmonday.hodith.ui.theme.IconHalo],
 * not that composable itself — it's a fixed-size circular badge, and this cell is a dynamic-width
 * rounded square, so the ring is drawn locally instead of forcing a shape mismatch.
 */
@Composable
private fun BrightDayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<DayCellIcon>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val tint = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.aspectRatio(1f).padding(2.dp)) {
        if (isToday) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .blur(10.dp)
                        .background(tint.copy(alpha = 0.45f), shape),
            )
        }
        Surface(
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = if (isToday) 6.dp else 3.dp,
            border = if (isToday) BorderStroke(2.dp, tint) else null,
        ) {
            Column(modifier = Modifier.padding(5.dp)) {
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                Row {
                    icons.take(MAX_ICONS_PER_CELL).forEachIndexed { index, (case, ringed) ->
                        Box(
                            modifier =
                                Modifier
                                    .size(15.dp)
                                    .offset(x = (-4 * index).dp)
                                    .rotate(if (index % 2 == 0) -6f else 5f)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .then(
                                        when {
                                            ringed ->
                                                Modifier.border(
                                                    SPAN_START_RING_WIDTH,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                )
                                            isToday -> Modifier
                                            else ->
                                                Modifier.border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outlineVariant,
                                                    CircleShape,
                                                )
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(case.icon, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    if (icons.size > MAX_ICONS_PER_CELL) {
                        Box(
                            modifier =
                                Modifier
                                    .size(15.dp)
                                    .offset(x = (-4 * MAX_ICONS_PER_CELL).dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+${icons.size - MAX_ICONS_PER_CELL}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class PreviewSeedData(
    val cases: List<CalendarCase>,
    val events: List<CalendarEvent>,
    val earliestMonth: YearMonth,
    val currentMonth: YearMonth,
)

private fun previewSeedData(): PreviewSeedData {
    val cases =
        listOf(
            CalendarCase(1, "☕", "Perfect coffee"),
            CalendarCase(2, "🤕", "Migraine"),
            CalendarCase(3, "🔑", "Lost my keys"),
            CalendarCase(4, "😤", "Argument"),
            CalendarCase(5, "🏋️", "Workout"),
            CalendarCase(6, "🩸", "Nosebleed"),
        )
    val currentMonth = YearMonth.now()
    val earliestMonth = currentMonth.minusMonths(3)
    val zoneId = ZoneId.systemDefault()
    val today = LocalDate.now()
    val notes =
        listOf(
            "Right after the walk, felt great",
            null,
            "Started at the temples",
            "Forgot where I put them again",
            null,
            "Leg day",
        )
    var nextEventId = 1L
    val events =
        generateSequence(earliestMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(currentMonth) }
            .flatMap { month ->
                (1..24).map { day ->
                    val caseIndex = day % cases.size
                    CalendarEvent(
                        id = nextEventId++,
                        caseId = cases[caseIndex].id,
                        occurredAt =
                            month
                                .atDay((day % month.lengthOfMonth()) + 1)
                                .atStartOfDay(zoneId)
                                .toInstant()
                                .toEpochMilli(),
                        note = notes[caseIndex],
                    )
                }
            }.toList() +
            listOf(1L, 2L, 3L, 4L, 5L).map { caseId ->
                CalendarEvent(
                    id = nextEventId++,
                    caseId = caseId,
                    occurredAt =
                        currentMonth
                            .atDay(15)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    note = notes[(caseId - 1).toInt()],
                    tags = if (caseId == 1L) listOf("weekend", "late night") else emptyList(),
                )
            } +
            // A 4-day finished 🤕 and a still-running 🏋️ (runs to LocalDate.now()) — the spanned treatment.
            listOf(
                CalendarEvent(
                    id = nextEventId++,
                    caseId = 2L,
                    occurredAt =
                        today
                            .minusDays(9)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    endedAt =
                        today
                            .minusDays(5)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    note = "Rough patch",
                ),
                CalendarEvent(
                    id = nextEventId++,
                    caseId = 5L,
                    occurredAt =
                        today
                            .minusDays(2)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    isOngoing = true,
                    note = "Forgot to stop it",
                ),
            )
    return PreviewSeedData(cases, events, earliestMonth, currentMonth)
}

@Composable
private fun BigPictureGridPreviewContent() {
    val seed = previewSeedData()
    BigPictureGrid(
        earliestMonth = seed.earliestMonth,
        currentMonth = seed.currentMonth,
        cases = seed.cases,
        events = seed.events,
        today = LocalDate.now(),
        onOpenCase = {},
    )
}

@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun BigPictureGridPreview() {
    MaterialTheme {
        BigPictureGridPreviewContent()
    }
}

@Preview(name = "Intense", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun BigPictureGridIntensePreview() {
    CompositionLocalProvider(LocalBigPictureCellStyle provides BigPictureCellStyle.INTENSE) {
        HodithTheme(theme = AppTheme.INTENSE) {
            BigPictureGridPreviewContent()
        }
    }
}

@Preview(name = "Bright light", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun BigPictureGridBrightLightPreview() {
    CompositionLocalProvider(
        LocalBigPictureCellStyle provides BigPictureCellStyle.BRIGHT,
        LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
    ) {
        HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
            BigPictureGridPreviewContent()
        }
    }
}

@Preview(name = "Bright dark", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun BigPictureGridBrightDarkPreview() {
    CompositionLocalProvider(
        LocalBigPictureCellStyle provides BigPictureCellStyle.BRIGHT,
        LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
    ) {
        HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
            BigPictureGridPreviewContent()
        }
    }
}

/**
 * The day/week detail rows themselves (spec §9) — the detail dialogs can't render inside a static
 * `@Preview` (a platform Dialog limitation, same as `InsightsTab`'s drill-down rows), so this
 * exercises [EventDetailRow] directly: a point event with note + tags, a same-day duration event
 * with intensity, a still-running event, and a multi-day span — under a given [BigPictureDetail].
 */
@Composable
private fun EventDetailRowsPreviewContent(detail: BigPictureDetail) {
    val today = LocalDate.of(2026, 9, 9)
    val zone = ZoneId.systemDefault()

    fun at(
        day: LocalDate,
        hour: Int,
        minute: Int = 0,
    ) = day
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    val coffee = CalendarCase(1, "☕", "Perfect coffee")
    val migraine = CalendarCase(2, "🤕", "Migraine")
    val workout = CalendarCase(3, "🏋️", "Workout")
    val argument = CalendarCase(4, "😤", "Argument")
    val rows =
        listOf(
            DayEvent(
                CalendarEvent(1, 1, at(today, 7, 15), note = "Right after the walk, felt great", tags = listOf("weekend")),
                false,
                false,
            ),
            DayEvent(
                CalendarEvent(2, 2, at(today, 9, 10), endedAt = at(today, 9, 50), note = "Started at the temples", intensity = 3),
                false,
                false,
            ),
            DayEvent(CalendarEvent(3, 3, at(today, 8, 2), isOngoing = true, note = "Forgot to stop it"), false, false),
            DayEvent(
                CalendarEvent(4, 4, at(today.minusDays(8), 20), endedAt = at(today.minusDays(5), 10), note = "Rough patch"),
                true,
                false,
            ),
        )
    val caseById = listOf(coffee, migraine, workout, argument).associateBy { it.id }
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.padding(16.dp)) {
            rows.forEach { row ->
                EventDetailRow(row, caseById[row.event.caseId], today, detail, zone, {}, {}, LocalVoice.current)
            }
        }
    }
}

@Preview(name = "EventDetailRow — default detail", showBackground = true, widthDp = 340)
@Composable
private fun EventDetailRowsDefaultPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        EventDetailRowsPreviewContent(BigPictureDetail.DEFAULT)
    }
}

@Preview(name = "EventDetailRow — all on, Intense", showBackground = true, widthDp = 340)
@Composable
private fun EventDetailRowsAllOnIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE) {
        EventDetailRowsPreviewContent(BigPictureDetail(notes = true, tags = true, duration = true, intensity = true))
    }
}

@Preview(name = "EventDetailRow — all off", showBackground = true, widthDp = 340)
@Composable
private fun EventDetailRowsAllOffPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = true) {
        EventDetailRowsPreviewContent(BigPictureDetail.ALL_OFF)
    }
}
