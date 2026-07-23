package com.secondmonday.hodith.ui.bigpicture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.viewmodel.CalendarCase
import com.secondmonday.hodith.viewmodel.CalendarEvent
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

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
 * Scroll range is [earliestMonth]..[currentMonth] inclusive, opening at the bottom (current
 * month). Replaces an earlier row-per-case/shared-horizontal-time-axis/pinch-zoom design, retired
 * after on-device testing showed it didn't read clearly (see PROGRESS.md for the build history).
 */
private const val MAX_ICONS_PER_CELL = 3
private val WEEK_CHEVRON_TOUCH_TARGET = 48.dp

@Composable
fun BigPictureGrid(
    earliestMonth: YearMonth,
    currentMonth: YearMonth,
    cases: List<CalendarCase>,
    events: List<CalendarEvent>,
    today: LocalDate,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var visibleCaseIds by remember(cases) { mutableStateOf(cases.map { it.id }.toSet()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedWeek by remember { mutableStateOf<List<LocalDate>?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val eventsByDay =
        remember(events, zoneId) {
            events.groupBy { Instant.ofEpochMilli(it.occurredAt).atZone(zoneId).toLocalDate() }
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
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                cases.forEach { case ->
                    CaseFilterChip(
                        case = case,
                        selected = case.id in visibleCaseIds,
                        onToggle = {
                            visibleCaseIds =
                                if (case.id in visibleCaseIds) visibleCaseIds - case.id else visibleCaseIds + case.id
                        },
                    )
                }
            }
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
                            .filter { week -> !week.first().isAfter(today) }
                            .forEach { week ->
                                WeekRow(
                                    week = week,
                                    month = month,
                                    today = today,
                                    eventsByDay = eventsByDay,
                                    caseById = caseById,
                                    visibleCaseIds = visibleCaseIds,
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
            events = eventsByDay[day].orEmpty().filter { it.caseId in visibleCaseIds },
            caseById = caseById,
            onDismiss = { selectedDay = null },
        )
    }
    selectedWeek?.let { week ->
        WeekDetailDialog(
            week = week,
            today = today,
            eventsByDay = eventsByDay,
            caseById = caseById,
            visibleCaseIds = visibleCaseIds,
            onDismiss = { selectedWeek = null },
        )
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
        dismissLabel = voice.bigPictureDialogCloseAction,
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
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    caseById: Map<Long, CalendarCase>,
    visibleCaseIds: Set<Long>,
    onDayTap: (LocalDate) -> Unit,
    onWeekTap: () -> Unit,
) {
    val voice = LocalVoice.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
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
            if (day.isAfter(today) || day.month != month.month) {
                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
            } else {
                DayCell(
                    day = day,
                    isToday = day == today,
                    icons =
                        eventsByDay[day]
                            .orEmpty()
                            .mapNotNull { caseById[it.caseId] }
                            .distinctBy { it.id }
                            .filter { it.id in visibleCaseIds },
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
    events: List<CalendarEvent>,
    caseById: Map<Long, CalendarCase>,
    onDismiss: () -> Unit,
) {
    val voice = LocalVoice.current
    InfoDialog(
        title = day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)),
        onDismiss = onDismiss,
        dismissLabel = voice.bigPictureDialogCloseAction,
    ) {
        if (events.isEmpty()) {
            Text(voice.bigPictureDayDetailEmptyState)
        } else {
            Column {
                events.forEach { event ->
                    val case = caseById[event.caseId]
                    Text(
                        text = "${case?.icon.orEmpty()} ${case?.name.orEmpty()}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = event.note?.takeIf { it.isNotBlank() } ?: voice.bigPictureEventNoteEmptyState,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekDetailDialog(
    week: List<LocalDate>,
    today: LocalDate,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    caseById: Map<Long, CalendarCase>,
    visibleCaseIds: Set<Long>,
    onDismiss: () -> Unit,
) {
    val voice = LocalVoice.current
    val validDays = week.filter { !it.isAfter(today) }
    InfoDialog(
        title =
            voice.bigPictureWeekDetailTitle(
                week.first().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)),
            ),
        onDismiss = onDismiss,
        dismissLabel = voice.bigPictureDialogCloseAction,
    ) {
        Column {
            validDays.forEach { day ->
                val dayEvents = eventsByDay[day].orEmpty().filter { it.caseId in visibleCaseIds }
                if (dayEvents.isNotEmpty()) {
                    Text(
                        text = day.format(DateTimeFormatter.ofPattern("EEE d", Locale.US)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    dayEvents.forEach { event ->
                        val case = caseById[event.caseId]
                        Text("${case?.icon.orEmpty()} ${case?.name.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (validDays.all { eventsByDay[it].orEmpty().none { event -> event.caseId in visibleCaseIds } }) {
                Text(voice.bigPictureWeekDetailEmptyState)
            }
        }
    }
}

@Composable
private fun CaseFilterChip(
    case: CalendarCase,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.outlineVariant
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(case.icon)
        Text(case.name, style = MaterialTheme.typography.labelSmall, color = content)
    }
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

@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<CalendarCase>,
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
            icons.take(MAX_ICONS_PER_CELL).forEach { case ->
                Box(
                    modifier = Modifier.size(16.dp).clip(CircleShape),
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

internal fun weeksInGrid(month: YearMonth): List<List<LocalDate>> {
    val firstOfMonth = month.atDay(1)
    val lastOfMonth = month.atEndOfMonth()
    val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

    val days = generateSequence(gridStart) { it.plusDays(1) }.takeWhile { !it.isAfter(gridEnd) }.toList()
    return days.chunked(7)
}

@Preview(showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun BigPictureGridPreview() {
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
    val notes =
        listOf(
            "Right after the walk, felt great",
            null,
            "Started at the temples",
            "Forgot where I put them again",
            null,
            "Leg day",
        )
    val events =
        generateSequence(earliestMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(currentMonth) }
            .flatMap { month ->
                (1..24).map { day ->
                    val caseIndex = day % cases.size
                    CalendarEvent(
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
                    caseId = caseId,
                    occurredAt =
                        currentMonth
                            .atDay(15)
                            .atStartOfDay(zoneId)
                            .toInstant()
                            .toEpochMilli(),
                    note = notes[(caseId - 1).toInt()],
                )
            }

    MaterialTheme {
        BigPictureGrid(
            earliestMonth = earliestMonth,
            currentMonth = currentMonth,
            cases = cases,
            events = events,
            today = LocalDate.now(),
        )
    }
}
