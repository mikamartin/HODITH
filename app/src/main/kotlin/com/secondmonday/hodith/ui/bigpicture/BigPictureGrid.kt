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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.weeksInGrid
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.theme.BigPictureCellStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalBigPictureCellStyle
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
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
    onOpenCase: (Long) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var visibleCaseIds by remember(cases) { mutableStateOf(cases.map { it.id }.toSet()) }
    val allTagNames = remember(events) { events.flatMap { it.tags }.distinct().sorted() }
    var visibleTagNames by remember(allTagNames) { mutableStateOf(allTagNames.toSet()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedWeek by remember { mutableStateOf<List<LocalDate>?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    val isEventVisible: (CalendarEvent) -> Boolean = { event ->
        event.caseId in visibleCaseIds &&
            (visibleTagNames.size == allTagNames.size || event.tags.any { it in visibleTagNames })
    }

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
            FilterChipsRow(
                cases = cases,
                visibleCaseIds = visibleCaseIds,
                onToggleCase = { caseId ->
                    visibleCaseIds = if (caseId in visibleCaseIds) visibleCaseIds - caseId else visibleCaseIds + caseId
                },
                allTagNames = allTagNames,
                visibleTagNames = visibleTagNames,
                onToggleTag = { tag ->
                    visibleTagNames = if (tag in visibleTagNames) visibleTagNames - tag else visibleTagNames + tag
                },
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
                            .filter { week -> !week.first().isAfter(today) }
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
            events = eventsByDay[day].orEmpty().filter(isEventVisible),
            caseById = caseById,
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
            zoneId = zoneId,
            onOpenCase = onOpenCase,
            onDismiss = { selectedWeek = null },
        )
    }
}

/** Case chips double as a legend and visibility toggle; the tag row below is omitted entirely when no event carries a tag. */
@Composable
private fun FilterChipsRow(
    cases: List<CalendarCase>,
    visibleCaseIds: Set<Long>,
    onToggleCase: (Long) -> Unit,
    allTagNames: List<String>,
    visibleTagNames: Set<String>,
    onToggleTag: (String) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cases.forEach { case ->
            CaseFilterChip(case = case, selected = case.id in visibleCaseIds, onToggle = { onToggleCase(case.id) })
        }
    }
    if (allTagNames.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            allTagNames.forEach { tag ->
                TagFilterChip(tag = tag, selected = tag in visibleTagNames, onToggle = { onToggleTag(tag) })
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
    isEventVisible: (CalendarEvent) -> Boolean,
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
                            .filter(isEventVisible)
                            .mapNotNull { caseById[it.caseId] }
                            .distinctBy { it.id },
                    onClick = { onDayTap(day) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

@Composable
private fun DayDetailDialog(
    day: LocalDate,
    events: List<CalendarEvent>,
    caseById: Map<Long, CalendarCase>,
    zoneId: ZoneId,
    onOpenCase: (Long) -> Unit,
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
                    EventDetailRow(event, caseById[event.caseId], zoneId, onOpenCase, onDismiss, voice)
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
    isEventVisible: (CalendarEvent) -> Boolean,
    zoneId: ZoneId,
    onOpenCase: (Long) -> Unit,
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
                val dayEvents = eventsByDay[day].orEmpty().filter(isEventVisible)
                if (dayEvents.isNotEmpty()) {
                    Text(
                        text = day.format(DateTimeFormatter.ofPattern("EEE d", Locale.US)),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    dayEvents.forEach { event ->
                        EventDetailRow(event, caseById[event.caseId], zoneId, onOpenCase, onDismiss, voice)
                    }
                }
            }
            if (validDays.all { eventsByDay[it].orEmpty().none(isEventVisible) }) {
                Text(voice.bigPictureWeekDetailEmptyState)
            }
        }
    }
}

@Composable
private fun EventDetailRow(
    event: CalendarEvent,
    case: CalendarCase?,
    zoneId: ZoneId,
    onOpenCase: (Long) -> Unit,
    onDismiss: () -> Unit,
    voice: Voice,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    onDismiss()
                    onOpenCase(event.caseId)
                }.padding(vertical = 6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${case?.icon.orEmpty()} ${case?.name.orEmpty()}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    Instant
                        .ofEpochMilli(event.occurredAt)
                        .atZone(zoneId)
                        .toLocalTime()
                        .format(EVENT_TIME_FORMATTER),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = event.note?.takeIf { it.isNotBlank() } ?: voice.bigPictureEventNoteEmptyState,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (event.tags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                event.tags.forEach { tag -> TagPill(tag) }
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
private fun TagFilterChip(
    tag: String,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val background = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
    val border = if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.outlineVariant
    val content = if (selected) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .border(1.dp, border, RoundedCornerShape(16.dp))
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
    )
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
 * Dispatches to the active theme's bespoke cell treatment (spec §12; concepts validated against
 * a mockup before this was written — see PROGRESS.md's Phase 5 entry). The three variants below
 * are the only place in this file that branch on [BigPictureCellStyle]; everything else (filter
 * chips, week borders) is unchanged and stays theme-agnostic via [MaterialTheme] tokens alone.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<CalendarCase>,
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

/** Dossier-file read: a neutral tab carries the day number; crimson is reserved for today. */
@Composable
private fun IntenseDayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<CalendarCase>,
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
            icons.take(MAX_ICONS_PER_CELL).forEach { case ->
                Box(
                    modifier =
                        Modifier
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
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

/** Playful-reveal read: a floating shadowed card, case icons as a fanned sticker cluster. */
@Composable
private fun BrightDayCell(
    day: LocalDate,
    isToday: Boolean,
    icons: List<CalendarCase>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small

    Surface(
        modifier = modifier.aspectRatio(1f).padding(2.dp).clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = if (isToday) 6.dp else 3.dp,
        border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
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
                icons.take(MAX_ICONS_PER_CELL).forEachIndexed { index, case ->
                    Box(
                        modifier =
                            Modifier
                                .size(15.dp)
                                .offset(x = (-4 * index).dp)
                                .rotate(if (index % 2 == 0) -6f else 5f)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .then(
                                    if (isToday) {
                                        Modifier
                                    } else {
                                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
            }
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

@Preview(name = "Bright", showBackground = true, widthDp = 380, heightDp = 700)
@Composable
private fun BigPictureGridBrightPreview() {
    CompositionLocalProvider(LocalBigPictureCellStyle provides BigPictureCellStyle.BRIGHT) {
        HodithTheme(theme = AppTheme.BRIGHT) {
            BigPictureGridPreviewContent()
        }
    }
}
