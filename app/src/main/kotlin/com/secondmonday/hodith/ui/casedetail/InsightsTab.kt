package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.loggedZone
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.AFTERNOON_START_HOUR
import com.secondmonday.hodith.domain.EVENING_START_HOUR
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.domain.INTENSITY_MIN
import com.secondmonday.hodith.domain.MORNING_START_HOUR
import com.secondmonday.hodith.domain.NIGHT_START_HOUR
import com.secondmonday.hodith.domain.RHYTHM_TIER_COUNT
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.domain.activeSpanEnd
import com.secondmonday.hodith.domain.datesCovered
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.domain.timeOfDayFor
import com.secondmonday.hodith.ui.common.CenteredEmptyState
import com.secondmonday.hodith.ui.common.InfoDialog
import com.secondmonday.hodith.ui.common.OngoingElapsedText
import com.secondmonday.hodith.ui.common.SectionWithInfo
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.toCellColor
import com.secondmonday.hodith.ui.common.toTextColor
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.GlowCard
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.DurationDisplay
import com.secondmonday.hodith.viewmodel.FrequencyBar
import com.secondmonday.hodith.viewmodel.FrequencyDisplay
import com.secondmonday.hodith.viewmodel.GapsDisplay
import com.secondmonday.hodith.viewmodel.HeatmapDay
import com.secondmonday.hodith.viewmodel.HeatmapMonth
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.IntensityDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.StatsSections
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatClockTime
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.formatFrequencyTickLabel
import com.secondmonday.hodith.viewmodel.formatMediumDate
import com.secondmonday.hodith.viewmodel.formatMinutesDuration
import com.secondmonday.hodith.viewmodel.frequencyTickCount
import com.secondmonday.hodith.viewmodel.frequencyTickIndices
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val HEATMAP_DEFAULT_MONTH_COUNT = 3
private const val TRENDS_DEFAULT_VISIBLE_COUNT = 3
private const val FREQUENCY_BAR_CHART_HEIGHT = 80
private const val FREQUENCY_MIN_BAR_HEIGHT_FRACTION = 0.02f

/** Bars use only this fraction of the chart's height, reserving headroom so a full-height bar's count label never crowds the row above. */
private const val FREQUENCY_BAR_MAX_HEIGHT_FRACTION = 0.8f
private const val FREQUENCY_BAR_LABEL_GAP = 2
private const val FREQUENCY_CHART_TOP_SPACING = 16
private const val FREQUENCY_TICK_LABEL_GAP = 4
private const val RHYTHM_CELL_SIZE = 26

/** Meant to fit "Afternoon" — the longest time-of-day label, at [MaterialTheme.typography]'s `bodyMedium` — on one line; falls back to an ellipsis (`TextOverflow.Ellipsis` on the label `Text`) rather than breaking the row's layout if a theme's display font doesn't quite fit it. A fixed width (not `Modifier.weight`) keeps the label snug against the grid instead of stretching to fill the row. */
private const val RHYTHM_LABEL_WIDTH = 112

/** Separation between the label column and the grid, independent of [RHYTHM_LABEL_WIDTH] itself. */
private const val RHYTHM_GRID_GAP = 8

/**
 * Case Detail's Insights tab (spec §9-10): the seven stat cards followed by the per-case calendar
 * heatmap. With zero events a flat invitation replaces the whole tab; from the first event the
 * heatmap, a one-line count note, and the Rhythm and Gaps cards render (spec §9's Big Picture
 * carve-out), with Frequency and Trend held back until [INSIGHTS_MIN_EVENTS] events.
 *
 * Spec §9/§10 drill-down (S10): a heatmap day, an intensity square, a tag row, or a rhythm cell
 * opens the logged events behind it in a shared [InsightsDrillDownDialog], filtered in memory over
 * [events] — [case]/[now] carry just enough to format and open a row via [onEditEvent], same shape
 * as the Log tab's own [EventEntity]-keyed callback.
 */
@Composable
internal fun InsightsTabContent(
    state: InsightsTabState,
    case: CaseEntity,
    events: List<EventWithTags>,
    now: Long,
    voice: Voice,
    frequencyGranularityOverride: FrequencyGranularity?,
    onFrequencyGranularityChange: (FrequencyGranularity?) -> Unit,
    onEditEvent: (EventEntity) -> Unit,
    onOpenTrends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedIntensity by remember { mutableStateOf<Int?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var selectedRhythmCell by remember { mutableStateOf<Pair<DayOfWeek, TimeOfDay>?>(null) }
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalLocale.current.platformLocale

    when (state) {
        is InsightsTabState.NothingLogged ->
            Box(modifier = modifier.fillMaxSize()) {
                CenteredEmptyState(voice.insightsNothingLoggedMessage)
            }
        is InsightsTabState.Ready ->
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // The count note fills the gap left by the still-hidden Frequency/Trend cards; its
                // singular copy assumes the sparse band is exactly one event (true while
                // INSIGHTS_MIN_EVENTS == 2).
                if (state.stats.totalEventCount < INSIGHTS_MIN_EVENTS) {
                    // Same muted aside as the Hunch tab's `hunchTabNoneDataNote` on the sibling tab.
                    Text(
                        text = voice.insightsSingleEventNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatsSectionCards(
                    stats = state.stats,
                    frequencyGranularityOverride = frequencyGranularityOverride,
                    onFrequencyGranularityChange = onFrequencyGranularityChange,
                    voice = voice,
                    onRhythmCellTap = { day, timeOfDay -> selectedRhythmCell = day to timeOfDay },
                    onIntensityTap = { selectedIntensity = it },
                    onTagTap = { selectedTag = it },
                    onOpenTrends = onOpenTrends,
                )
                CalendarHeatmapCard(state.heatmapMonths, voice, onDayTap = { selectedDay = it })
            }
    }

    selectedDay?.let { day ->
        InsightsDrillDownDialog(
            title = formatMediumDate(day),
            events =
                events
                    .filter { ew ->
                        // A still-running event's open end is "now," not a captured instant, so it
                        // resolves via the live current zone rather than the event's own (possibly
                        // stale, pre-travel) offset — matching BigPictureGrid's private coveredDates.
                        val isOngoing = case.durationMode == DurationMode.START_STOP && ew.event.endedAt == null
                        val endZone = if (isOngoing) zone else ew.event.loggedZone()
                        day in
                            datesCovered(
                                ew.event.occurredAt,
                                activeSpanEnd(ew.event, case.durationMode, now),
                                ew.event.loggedZone(),
                                endZone,
                            )
                    }.sortedBy { it.event.occurredAt },
            now = now,
            durationMode = case.durationMode,
            voice = voice,
            onEditEvent = onEditEvent,
            onDismiss = { selectedDay = null },
        )
    }
    selectedIntensity?.let { level ->
        InsightsDrillDownDialog(
            title = voice.insightsIntensityDrillDownTitle(level),
            events = events.filter { it.event.intensity == level }.sortedByDescending { it.event.occurredAt },
            now = now,
            durationMode = case.durationMode,
            voice = voice,
            // Every row shares this exact intensity -- the dialog's own title already says so.
            showIntensity = false,
            onEditEvent = onEditEvent,
            onDismiss = { selectedIntensity = null },
        )
    }
    selectedTag?.let { tagName ->
        InsightsDrillDownDialog(
            title = voice.insightsTagDrillDownTitle(tagName),
            events = events.filter { ew -> ew.tags.any { it.name == tagName } }.sortedByDescending { it.event.occurredAt },
            now = now,
            durationMode = case.durationMode,
            voice = voice,
            // Every row already matched this tag -- the dialog's own title already says so. Any
            // other tags an event carries are still shown, since those aren't redundant here.
            suppressTagName = tagName,
            onEditEvent = onEditEvent,
            onDismiss = { selectedTag = null },
        )
    }
    selectedRhythmCell?.let { (day, timeOfDay) ->
        val dayLabel = day.getDisplayName(TextStyle.FULL, locale)
        val timeOfDayLabel = rhythmTimeOfDayLabel(voice, timeOfDay)
        InsightsDrillDownDialog(
            title = voice.insightsRhythmDrillDownTitle(dayLabel, timeOfDayLabel),
            events =
                events
                    .filter { ew ->
                        val dateTime = Instant.ofEpochMilli(ew.event.occurredAt).atZone(ew.event.loggedZone())
                        dateTime.dayOfWeek == day && timeOfDayFor(dateTime.hour) == timeOfDay
                    }.sortedByDescending { it.event.occurredAt },
            now = now,
            durationMode = case.durationMode,
            voice = voice,
            onEditEvent = onEditEvent,
            onDismiss = { selectedRhythmCell = null },
        )
    }
}

/** Spec §10's seven stat sections, in spec order. [StatsSections.frequency]/[trend]/[duration]/[intensity] omit their card entirely when absent. */
@Composable
private fun StatsSectionCards(
    stats: StatsSections,
    frequencyGranularityOverride: FrequencyGranularity?,
    onFrequencyGranularityChange: (FrequencyGranularity?) -> Unit,
    voice: Voice,
    onRhythmCellTap: (DayOfWeek, TimeOfDay) -> Unit,
    onIntensityTap: (Int) -> Unit,
    onTagTap: (String) -> Unit,
    onOpenTrends: () -> Unit,
) {
    if (stats.trends.isNotEmpty()) TrendsCard(stats.trends, voice, onOpenTrends)
    stats.frequency?.let { FrequencyCard(it, frequencyGranularityOverride, onFrequencyGranularityChange, voice) }
    RhythmCard(stats.rhythm, voice, onRhythmCellTap)
    GapsCard(stats.gaps, voice)
    stats.duration?.let { DurationCard(it, voice) }
    stats.intensity?.let { IntensityCard(it, voice, onIntensityTap) }
    if (stats.tags.isNotEmpty()) TagsCard(stats.tags, stats.totalEventCount, voice, onTagTap)
}

/**
 * Shared shell for every Insights card — full-width [Card] with a padded, vertically-spaced
 * [Column]. Bright branches to [GlowCard], same dispatch as
 * [com.secondmonday.hodith.ui.home.HomeCaseListItem].
 */
@Composable
private fun InsightsCard(content: @Composable ColumnScope.() -> Unit) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT -> GlowCard(content = content)
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
            }
    }
}

/** Most recent month first, [HEATMAP_DEFAULT_MONTH_COUNT] shown by default with the rest behind a toggle. */
@Composable
private fun CalendarHeatmapCard(
    months: List<HeatmapMonth>,
    voice: Voice,
    onDayTap: (LocalDate) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val orderedMonths = months.asReversed()
    val visibleMonths = if (expanded) orderedMonths else orderedMonths.take(HEATMAP_DEFAULT_MONTH_COUNT)

    InsightsCard {
        Text(voice.insightsSectionLabelHeatmap, style = MaterialTheme.typography.titleSmall)
        HeatmapWeekdayHeader()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleMonths.forEach { month -> HeatmapMonthGrid(month, voice, onDayTap) }
        }
        if (orderedMonths.size > HEATMAP_DEFAULT_MONTH_COUNT) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) voice.insightsHeatmapShowFewerAction else voice.insightsHeatmapShowMoreAction)
            }
        }
    }
}

@Composable
private fun HeatmapWeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                text = day.name.take(1),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeatmapMonthGrid(
    month: HeatmapMonth,
    voice: Voice,
    onDayTap: (LocalDate) -> Unit,
) {
    Column {
        Text(
            text = month.month.monthYearLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        month.weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                week.forEach { day -> HeatmapCell(day, voice, onDayTap, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Spec §9/§10 drill-down: [enabled] makes the modified element a tap target with a
 * [contentDescription] via [Role.Button] semantics; otherwise it's left untouched (inert). Shared
 * by [HeatmapCell], [IntensityCard]'s squares, and [StatRow], which would otherwise each hand-roll
 * the same conditional-clickable-plus-semantics block.
 */
private fun Modifier.tappableWithDescription(
    enabled: Boolean,
    description: () -> String,
    onClick: () -> Unit,
): Modifier =
    if (enabled) {
        clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description() }
    } else {
        this
    }

/**
 * A day with at least one active event (spec §9) is a drill-down tap target, expanded to the
 * platform's 48dp minimum touch size via [minimumInteractiveComponentSize] since the cell itself
 * renders smaller in a 7-column week row; a zero-count or padding day stays inert.
 */
@Composable
private fun HeatmapCell(
    day: HeatmapDay?,
    voice: Voice,
    onDayTap: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTappable = day != null && day.level != HeatmapLevel.EMPTY
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(1.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (day == null) Color.Transparent else day.level.toCellColor())
                .then(if (isTappable) Modifier.minimumInteractiveComponentSize() else Modifier)
                .tappableWithDescription(
                    enabled = isTappable,
                    description = { day?.let { voice.insightsHeatmapDayTapDescription(formatMediumDate(it.date)) }.orEmpty() },
                    onClick = { day?.let { onDayTap(it.date) } },
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (day != null) {
            Text(
                text = day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = day.level.toTextColor(),
            )
        }
    }
}

private fun YearMonth.monthYearLabel(): String = "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"

/** Spec §10 frequency-over-time: a bar chart with a granularity override (Day/Week/Month), `null` meaning auto-pick. */
@Composable
private fun FrequencyCard(
    display: FrequencyDisplay,
    granularityOverride: FrequencyGranularity?,
    onGranularityChange: (FrequencyGranularity?) -> Unit,
    voice: Voice,
) {
    val locale = LocalLocale.current.platformLocale
    val tickIndices =
        remember(display.bars.size, display.granularity) {
            frequencyTickIndices(display.bars.size, frequencyTickCount(display.granularity)).toSet()
        }

    InsightsCard {
        SectionWithInfo(
            label = voice.insightsSectionLabelFrequency,
            infoTitle = voice.insightsFrequencyInfoTitle,
            infoBody = voice.insightsFrequencyInfoBody(display.granularity),
            infoDescription = voice.caseSectionInfoDescription,
            labelStyle = MaterialTheme.typography.titleSmall,
        ) {
            SegmentedChoiceRow(
                options =
                    listOf(
                        FrequencyGranularity.DAY to voice.insightsFrequencyGranularityDay,
                        FrequencyGranularity.WEEK to voice.insightsFrequencyGranularityWeek,
                        FrequencyGranularity.MONTH to voice.insightsFrequencyGranularityMonth,
                    ),
                selected = granularityOverride ?: display.granularity,
                onSelect = onGranularityChange,
            )
            val barBrush = frequencyBarBrush(LocalCardDecorationStyle.current)
            Row(modifier = Modifier.fillMaxWidth().padding(top = FREQUENCY_CHART_TOP_SPACING.dp)) {
                display.bars.forEachIndexed { index, bar ->
                    val barHeight =
                        FREQUENCY_BAR_CHART_HEIGHT.dp *
                            bar.heightFraction.coerceAtLeast(FREQUENCY_MIN_BAR_HEIGHT_FRACTION) *
                            FREQUENCY_BAR_MAX_HEIGHT_FRACTION
                    // One column per bar holds both the bar and its (optional) tick label, so a
                    // label can never drift from the bar it names the way the old separate
                    // space-between row could (spec S9).
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.fillMaxWidth().height(FREQUENCY_BAR_CHART_HEIGHT.dp)) {
                            Box(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .padding(horizontal = 1.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                        .background(barBrush),
                            )
                            if (bar.count > 0) {
                                Text(
                                    text = bar.count.toString(),
                                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = -(barHeight + FREQUENCY_BAR_LABEL_GAP.dp)),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index in tickIndices) {
                            Text(
                                text = formatFrequencyTickLabel(bar.periodStart, display.granularity, locale),
                                modifier = Modifier.padding(top = FREQUENCY_TICK_LABEL_GAP.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Bright's bars fade from full [Color]`.primary` at the top to this fraction toward the surface at the bottom; Plain/Intense keep a flat fill. */
private const val FREQUENCY_BAR_GRADIENT_END_TINT_FRACTION = 0.4f

@Composable
private fun frequencyBarBrush(decorationStyle: CardDecorationStyle): Brush {
    val primary = MaterialTheme.colorScheme.primary
    return when (decorationStyle) {
        CardDecorationStyle.BRIGHT ->
            Brush.verticalGradient(
                listOf(primary, lerp(primary, MaterialTheme.colorScheme.surface, FREQUENCY_BAR_GRADIENT_END_TINT_FRACTION)),
            )
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE -> Brush.verticalGradient(listOf(primary, primary))
    }
}

/** Shared with [InsightsTabContent] so the drill-down dialog title uses the same wording as the card's own row labels. */
private fun rhythmTimeOfDayLabel(
    voice: Voice,
    timeOfDay: TimeOfDay,
): String =
    when (timeOfDay) {
        TimeOfDay.MORNING -> voice.insightsTimeOfDayMorning
        TimeOfDay.AFTERNOON -> voice.insightsTimeOfDayAfternoon
        TimeOfDay.EVENING -> voice.insightsTimeOfDayEvening
        TimeOfDay.NIGHT -> voice.insightsTimeOfDayNight
    }

/**
 * Spec §10 rhythm heatmap: day-of-week columns x time-of-day rows, shaded like the calendar
 * heatmap, with an info icon spelling out the four [TimeOfDay] boundaries in the viewer's own
 * [LocalTimeFormat]. A cell with at least one event is a drill-down tap target (spec §10); a
 * zero-count cell stays inert, matching [IntensityCard]'s squares and [HeatmapCell]. Unlike those
 * two, cells here don't get [HeatmapCell]'s [minimumInteractiveComponentSize] touch-target
 * expansion — this grid's fixed-width `Row` (not weight-based) reports each cell's *expanded* size
 * straight into the row's layout width, ballooning all 7 columns well past the card's available
 * width instead of staying an invisible touch-catching margin. Below the 48dp guideline at
 * [RHYTHM_CELL_SIZE], same tradeoff as [IntensityCard]'s own squares already accept when width is
 * tight.
 */
@Composable
private fun RhythmCard(
    display: RhythmDisplay,
    voice: Voice,
    onCellTap: (DayOfWeek, TimeOfDay) -> Unit,
) {
    val locale = LocalLocale.current.platformLocale
    val use24Hour = LocalTimeFormat.current.is24Hour

    InsightsCard {
        SectionWithInfo(
            label = if (display.plottedByStart) voice.insightsSectionLabelRhythmStarts else voice.insightsSectionLabelRhythm,
            infoTitle = voice.insightsRhythmInfoTitle,
            infoBody =
                voice.insightsRhythmInfoBody(
                    morningStart = formatClockTime(LocalTime.of(MORNING_START_HOUR, 0), use24Hour),
                    afternoonStart = formatClockTime(LocalTime.of(AFTERNOON_START_HOUR, 0), use24Hour),
                    eveningStart = formatClockTime(LocalTime.of(EVENING_START_HOUR, 0), use24Hour),
                    nightStart = formatClockTime(LocalTime.of(NIGHT_START_HOUR, 0), use24Hour),
                ),
            infoDescription = voice.caseSectionInfoDescription,
            labelStyle = MaterialTheme.typography.titleSmall,
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(RHYTHM_LABEL_WIDTH.dp))
                Spacer(modifier = Modifier.width(RHYTHM_GRID_GAP.dp))
                DayOfWeek.entries.forEach { day ->
                    Text(
                        text = day.getDisplayName(TextStyle.NARROW, locale),
                        modifier = Modifier.width(RHYTHM_CELL_SIZE.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TimeOfDay.entries.forEach { timeOfDay ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rhythmTimeOfDayLabel(voice, timeOfDay),
                        modifier = Modifier.width(RHYTHM_LABEL_WIDTH.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.width(RHYTHM_GRID_GAP.dp))
                    DayOfWeek.entries.forEach { day ->
                        val cell = display.cells.first { it.dayOfWeek == day && it.timeOfDay == timeOfDay }
                        val dayLabel = day.getDisplayName(TextStyle.FULL, locale)
                        val timeOfDayLabel = rhythmTimeOfDayLabel(voice, timeOfDay)
                        val isTappable = cell.count > 0
                        Box(
                            modifier =
                                Modifier
                                    .size(RHYTHM_CELL_SIZE.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(cell.level.toCellColor(tierCount = RHYTHM_TIER_COUNT))
                                    .tappableWithDescription(
                                        enabled = isTappable,
                                        description = { voice.insightsRhythmCellTapDescription(dayLabel, timeOfDayLabel) },
                                        onClick = { onCellTap(day, timeOfDay) },
                                    ),
                        )
                    }
                }
            }
        }
    }
}

/** Spec §10 gaps & streaks: longest/current/average gap, longest/average streak, plus the "tends to come in bursts" flag. */
@Composable
private fun GapsCard(
    display: GapsDisplay,
    voice: Voice,
) {
    InsightsCard {
        SectionWithInfo(
            label = voice.insightsSectionLabelGaps,
            infoTitle = voice.insightsGapsInfoTitle,
            infoBody = voice.insightsGapsInfoBody,
            infoDescription = voice.caseSectionInfoDescription,
            labelStyle = MaterialTheme.typography.titleSmall,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow(voice.insightsGapsLongestLabel, formatDays(display.longestGapDays.toDouble()))
                StatRow(voice.insightsGapsCurrentLabel, formatDays(display.currentGapDays.toDouble()))
                StatRow(voice.insightsGapsAverageLabel, formatDays(display.averageGapDays))
                StatRow(voice.insightsStreakLongestLabel, formatDays(display.longestStreakDays.toDouble()))
                StatRow(voice.insightsStreakAverageLabel, formatDays(display.averageStreakDays))
                if (display.isBursty) {
                    Text(
                        text = voice.insightsBurstFlagLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/**
 * Spec §10 Trends section (Story C T1's scaffold): the first Insights card, shown only when at
 * least one [TrendFinding] exists — mirrors [TagsCard]'s `.isNotEmpty()` gate at the call site. No
 * info icon and no Hint/Pattern tags here — at this level of detail (sentence with real numbers)
 * neither earns its screen space; both live one tap away on the full-list screen
 * ([com.secondmonday.hodith.ui.casedetail.trends.TrendsListScreen] via [onShowMore]) instead. Shows
 * the first [TRENDS_DEFAULT_VISIBLE_COUNT] findings; "show more" is right-aligned under them,
 * matching a trailing/secondary action rather than a primary one.
 */
@Composable
private fun TrendsCard(
    findings: List<TrendFinding>,
    voice: Voice,
    onShowMore: () -> Unit,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelTrends, style = MaterialTheme.typography.titleSmall)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            findings.take(TRENDS_DEFAULT_VISIBLE_COUNT).forEach { finding -> TrendFindingRow(finding, voice) }
        }
        if (findings.size > TRENDS_DEFAULT_VISIBLE_COUNT) {
            TextButton(onClick = onShowMore, modifier = Modifier.align(Alignment.End)) {
                Text(voice.insightsTrendsShowMoreAction)
            }
        }
    }
}

/** [TrendFinding]'s reliability tag — plain colored text, the same "flag" idiom [insightsBurstFlagLabel] already uses on the Gaps card, not a filled chip. Pattern reads more prominent than Hint, matching that it carries more statistical weight. Shown only on the full-list screen ([TrendFindingPlank]), not the compact card. */
@Composable
private fun TrendReliabilityTag(
    reliability: TrendReliability,
    voice: Voice,
) {
    val (label, color) =
        when (reliability) {
            TrendReliability.HINT -> voice.trendReliabilityHintLabel to MaterialTheme.colorScheme.onSurfaceVariant
            TrendReliability.PATTERN -> voice.trendReliabilityPatternLabel to MaterialTheme.colorScheme.primary
        }
    Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
}

/**
 * One [TrendFinding]'s content: its sentence (with the real prior/recent averages) and its
 * evidence count on the line below, always visible without a tap. [showReliabilityTag] adds the
 * Hint/Pattern tag alongside the sentence — off for [TrendFindingRow] (compact card, keeps that
 * surface to sentence + numbers only), on for [TrendFindingPlank] (full-list screen, more room and
 * more reason to want the tier at a glance).
 */
@Composable
private fun TrendFindingContent(
    finding: TrendFinding,
    voice: Voice,
    showReliabilityTag: Boolean,
    modifier: Modifier = Modifier,
) {
    val sentence: String
    val evidenceLabel: String
    when (finding.kind) {
        TrendFindingKind.WENT_QUIET -> {
            sentence = voice.insightsWentQuietSentence(formatDays(finding.recentValue), formatDays(finding.priorValue))
            evidenceLabel = voice.insightsWentQuietEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.GAP_SHIFT -> {
            sentence = voice.insightsGapShiftSentence(finding.direction, formatDays(finding.priorValue), formatDays(finding.recentValue))
            evidenceLabel = voice.insightsGapShiftEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.STREAK_SHIFT -> {
            sentence =
                voice.insightsStreakShiftSentence(finding.direction, formatDays(finding.priorValue), formatDays(finding.recentValue))
            evidenceLabel = voice.insightsStreakShiftEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.FREQUENCY_SHIFT -> {
            // FLAT never reaches here -- computeTrendFindings excludes it, the same "silent when
            // nothing moved" rule gap/streak shift already follow (spec §10, Story C T1).
            val trendDirection = if (finding.direction == ShiftDirection.UP) TrendDirection.UP else TrendDirection.DOWN
            sentence = voice.insightsTrendSentence(trendDirection, finding.recentValue.roundToInt(), finding.priorValue.roundToInt())
            evidenceLabel = voice.insightsFrequencyShiftEvidenceLabel()
        }
        TrendFindingKind.TAG_SHARE_SHIFT -> {
            // tagName is always set for this kind -- see TrendFinding's doc comment.
            sentence =
                voice.insightsTagShareShiftSentence(
                    finding.tagName.orEmpty(),
                    finding.direction,
                    formatPercent(finding.priorValue),
                    formatPercent(finding.recentValue),
                )
            evidenceLabel = voice.insightsTagShareShiftEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.RECURRENCE_SHAPE -> {
            sentence =
                voice.insightsRecurrenceShapeSentence(finding.direction, formatDays(finding.priorValue), formatPercent(finding.recentValue))
            evidenceLabel = voice.insightsRecurrenceShapeEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.TAG_OUTCOME -> {
            // tagName and outcome are always set for this kind -- see TrendFinding's KDoc.
            val outcome = finding.outcome ?: TagOutcome.INTENSITY
            val (withoutTagLabel, withTagLabel) =
                when (outcome) {
                    TagOutcome.INTENSITY -> formatIntensity(finding.priorValue) to formatIntensity(finding.recentValue)
                    TagOutcome.DURATION ->
                        formatMinutesDuration(finding.priorValue.roundToLong()) to formatMinutesDuration(finding.recentValue.roundToLong())
                }
            val relativeDifferenceLabel = formatPercent(abs((finding.recentValue - finding.priorValue) / finding.priorValue))
            sentence =
                voice.insightsTagOutcomeSentence(
                    finding.tagName.orEmpty(),
                    outcome,
                    finding.direction,
                    relativeDifferenceLabel,
                    withoutTagLabel,
                    withTagLabel,
                )
            evidenceLabel = voice.insightsTagOutcomeEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.CHANGE_POINT -> {
            // changePointDate is always set for this kind -- see TrendFinding's KDoc.
            sentence =
                voice.insightsChangePointSentence(
                    finding.direction,
                    formatApproximateMonth(finding.changePointDate ?: LocalDate.now()),
                    formatDays(finding.priorValue),
                    formatDays(finding.recentValue),
                )
            evidenceLabel = voice.insightsChangePointEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.TREND_SLOPE -> {
            // outcome is always set for this kind -- see TrendFinding's KDoc.
            val outcome = finding.outcome ?: TagOutcome.INTENSITY
            val (priorLabel, recentLabel) =
                when (outcome) {
                    TagOutcome.INTENSITY -> formatIntensity(finding.priorValue) to formatIntensity(finding.recentValue)
                    TagOutcome.DURATION ->
                        formatMinutesDuration(finding.priorValue.roundToLong()) to formatMinutesDuration(finding.recentValue.roundToLong())
                }
            sentence = voice.insightsTrendSlopeSentence(outcome, finding.direction, priorLabel, recentLabel)
            evidenceLabel = voice.insightsTrendSlopeEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.TIME_OF_DAY_SPLIT -> {
            // outcome is always set for this kind -- see TrendFinding's KDoc.
            val outcome = finding.outcome ?: TagOutcome.INTENSITY
            val (dayLabel, eveningLabel) =
                when (outcome) {
                    TagOutcome.INTENSITY -> formatIntensity(finding.priorValue) to formatIntensity(finding.recentValue)
                    TagOutcome.DURATION ->
                        formatMinutesDuration(finding.priorValue.roundToLong()) to formatMinutesDuration(finding.recentValue.roundToLong())
                }
            sentence = voice.insightsTimeOfDaySplitSentence(outcome, finding.direction, dayLabel, eveningLabel)
            evidenceLabel = voice.insightsTimeOfDaySplitEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.TAG_TIMING -> {
            // tagName is always set; exactly one of weekday/timeOfDay is set -- see TrendFinding's KDoc.
            val locale = LocalLocale.current.platformLocale
            val bucketPhrase =
                finding.weekday?.let { "on ${it.getDisplayName(TextStyle.FULL, locale)}s" }
                    ?: "in the ${rhythmTimeOfDayLabel(voice, finding.timeOfDay ?: TimeOfDay.MORNING).lowercase()}"
            sentence =
                voice.insightsTagTimingSentence(
                    finding.tagName.orEmpty(),
                    bucketPhrase,
                    formatPercent(finding.priorValue),
                    formatPercent(finding.recentValue),
                )
            evidenceLabel = voice.insightsTagTimingEvidenceLabel(finding.sampleCount)
        }
        TrendFindingKind.WEEKDAY_WEEKEND_SPLIT -> {
            sentence =
                voice.insightsWeekdayWeekendSentence(
                    finding.direction,
                    weekdayLabel = formatPercent(1 - finding.recentValue),
                    weekendLabel = formatPercent(finding.recentValue),
                )
            evidenceLabel = voice.insightsWeekdayWeekendEvidenceLabel(finding.sampleCount)
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = sentence, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (showReliabilityTag) TrendReliabilityTag(finding.reliability, voice)
        }
        Text(text = evidenceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One Trends finding inside the compact [TrendsCard] — no reliability tag (see [TrendFindingContent]). */
@Composable
private fun TrendFindingRow(
    finding: TrendFinding,
    voice: Voice,
) {
    TrendFindingContent(finding, voice, showReliabilityTag = false, modifier = Modifier.fillMaxWidth())
}

/**
 * One Trends finding on the full-list screen, with its reliability tag (see [TrendFindingContent]).
 * Plain wraps it in its own white plank [Card] on the tinted screen background, matching
 * [EventRow]'s Log-tab convention; Intense and Bright keep a flat row, same split as [EventRow].
 * Internal so [com.secondmonday.hodith.ui.casedetail.trends.TrendsListScreen] can render it.
 */
@Composable
internal fun TrendFindingPlank(
    finding: TrendFinding,
    voice: Voice,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.PLAIN ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                TrendFindingContent(
                    finding,
                    voice,
                    showReliabilityTag = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        CardDecorationStyle.INTENSE, CardDecorationStyle.BRIGHT ->
            TrendFindingContent(
                finding,
                voice,
                showReliabilityTag = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            )
    }
}

/** Spec §10 duration stats — only shown when the Case's `durationMode != NONE`. */
@Composable
private fun DurationCard(
    display: DurationDisplay,
    voice: Voice,
) {
    InsightsCard {
        SectionWithInfo(
            label = voice.insightsSectionLabelDuration,
            infoTitle = voice.insightsDurationInfoTitle,
            infoBody = voice.insightsDurationInfoBody,
            infoDescription = voice.caseSectionInfoDescription,
            labelStyle = MaterialTheme.typography.titleSmall,
        ) {
            StatRow(voice.insightsDurationAverageLabel, formatMinutesDuration(display.averageMinutes.roundToInt().toLong()))
            StatRow(voice.insightsDurationLongestLabel, formatMinutesDuration(display.longestMinutes))
            StatRow(voice.insightsDurationTotalLabel, formatMinutesDuration(display.totalMinutes))
        }
    }
}

/**
 * Spec §10 intensity stats — only shown when the Case has `intensityEnabled`. A row of five shaded
 * squares, one per intensity level; a square with at least one event is a drill-down tap target
 * (spec §10), a zero-count square stays inert.
 */
@Composable
private fun IntensityCard(
    display: IntensityDisplay,
    voice: Voice,
    onIntensityTap: (Int) -> Unit,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelIntensity, style = MaterialTheme.typography.titleSmall)
        StatRow(voice.insightsIntensityAverageLabel, formatIntensity(display.averageIntensity))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (INTENSITY_MIN..INTENSITY_MAX).forEach { value ->
                val count = display.distribution[value] ?: 0
                val level = heatmapLevelFor(count, display.maxCount)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(level.toCellColor())
                            .tappableWithDescription(
                                enabled = count > 0,
                                description = { voice.insightsIntensitySquareTapDescription(value) },
                                onClick = { onIntensityTap(value) },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = value.toString(), style = MaterialTheme.typography.labelSmall, color = level.toTextColor())
                }
            }
        }
    }
}

/**
 * Spec §10 tag breakdown: counts per tag, busiest first, against [totalEventCount] so an
 * individual tag's count reads in proportion to the Case's whole history. Card is omitted
 * entirely when no event carries a tag. Every tag row is a drill-down tap target (spec §10) — a
 * tag only ever appears here once it has counted at least one event.
 */
@Composable
private fun TagsCard(
    tags: List<TagBreakdownEntry>,
    totalEventCount: Int,
    voice: Voice,
    onTagTap: (String) -> Unit,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelTags, style = MaterialTheme.typography.titleSmall)
        StatRow(voice.insightsTagsTotalLabel, totalEventCount.toString())
        tags.forEach { tag ->
            StatRow(
                label = tag.tagName,
                value = tag.count.toString(),
                onClick = { onTagTap(tag.tagName) },
                contentDescription = voice.insightsTagRowTapDescription(tag.tagName),
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .tappableWithDescription(
                    enabled = onClick != null,
                    description = { contentDescription.orEmpty() },
                    onClick = { onClick?.invoke() },
                ),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Spec §9/§10 drill-down (S10): the shared dialog opened by a heatmap day, an intensity square,
 * or a tag row, listing the [events] behind it. [voice.insightsDrillDownEmptyState] is a defensive
 * fallback only — every caller already gates its tap target on having a match.
 *
 * [showIntensity] and [suppressTagName] let the intensity/tag filters hide the one piece of
 * per-row detail their own dialog title already states — every row an intensity-filtered dialog
 * lists shares that exact intensity, and every row a tag-filtered dialog lists already matched
 * that exact tag, so repeating either on each row is noise rather than information. The
 * day-filtered dialog passes neither, since intensity/tags are still genuinely informative there.
 */
@Composable
private fun InsightsDrillDownDialog(
    title: String,
    events: List<EventWithTags>,
    now: Long,
    durationMode: DurationMode,
    voice: Voice,
    onEditEvent: (EventEntity) -> Unit,
    onDismiss: () -> Unit,
    showIntensity: Boolean = true,
    suppressTagName: String? = null,
) {
    InfoDialog(title = title, onDismiss = onDismiss) {
        if (events.isEmpty()) {
            Text(voice.insightsDrillDownEmptyState)
        } else {
            // AlertDialog doesn't scroll its `text` slot on its own -- content taller than the
            // dialog's window just clips silently rather than scrolling, so a long event list
            // needs its own scroll here.
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                events.forEach { eventWithTags ->
                    InsightsDrillDownEventRow(
                        eventWithTags = eventWithTags,
                        now = now,
                        voice = voice,
                        durationMode = durationMode,
                        showIntensity = showIntensity,
                        suppressTagName = suppressTagName,
                        onClick = {
                            onDismiss()
                            onEditEvent(eventWithTags.event)
                        },
                    )
                }
            }
        }
    }
}

/**
 * One event inside [InsightsDrillDownDialog] — timestamp (or live elapsed time while ongoing),
 * then [eventDetailSummary]'s duration/intensity/note/tags line. No case icon/name: unlike Big
 * Picture's cross-case [com.secondmonday.hodith.ui.bigpicture.BigPictureGrid] dialogs, the
 * Insights tab is already scoped to one Case.
 */
@Composable
private fun InsightsDrillDownEventRow(
    eventWithTags: EventWithTags,
    now: Long,
    voice: Voice,
    durationMode: DurationMode,
    onClick: () -> Unit,
    showIntensity: Boolean = true,
    suppressTagName: String? = null,
) {
    val event = eventWithTags.event
    val isOngoing = durationMode == DurationMode.START_STOP && event.endedAt == null

    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(
            text = formatEventTime(event.occurredAt, now, LocalTimeFormat.current.is24Hour, zone = event.loggedZone()),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (isOngoing) {
            OngoingElapsedText(startedAt = event.occurredAt, now = now, voice = voice)
        }
        val details =
            eventDetailSummary(
                event,
                eventWithTags.tags.filter { it.name != suppressTagName },
                voice,
                isOngoing = isOngoing,
                tracksDuration = durationMode.tracksDuration,
                showIntensity = showIntensity,
            )
        if (details != null) {
            Text(text = details, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** "3.5 days" for non-integer values (e.g. average gap), "3 days" for whole ones (e.g. longest/current gap). */
internal fun formatDays(days: Double): String {
    val label = if (days == days.roundToInt().toDouble()) days.roundToInt().toString() else String.format(Locale.US, "%.1f", days)
    return "$label days"
}

/** A share fraction (0.0–1.0) as a whole-number percentage, e.g. "40%". */
internal fun formatPercent(share: Double): String = "${(share * 100).roundToInt()}%"

/** An average intensity score to one decimal place, e.g. "3.2". */
internal fun formatIntensity(value: Double): String = String.format(Locale.US, "%.1f", value)

/**
 * A calendar date bucketed to a third of its month against the month's own name, e.g. "early March",
 * "mid-March", "late March" — [TrendFindingKind.CHANGE_POINT]'s change-point date is a
 * best-supported estimate, not a claim of the exact day, so its sentence states it at this coarser
 * grain on purpose.
 */
internal fun formatApproximateMonth(date: LocalDate): String {
    val monthName = date.month.getDisplayName(TextStyle.FULL, Locale.US)
    val partOfMonth =
        when {
            date.dayOfMonth <= 10 -> "early"
            date.dayOfMonth <= 20 -> "mid-"
            else -> "late"
        }
    return if (partOfMonth == "mid-") "$partOfMonth$monthName" else "$partOfMonth $monthName"
}

// 12-bar fixtures (matching the real FREQUENCY_MAX_BUCKETS) for all three granularities, so
// previews exercise the actual tick-label density instead of the 6-bar stand-in this used to be.
private val previewFrequencyDisplayDay =
    FrequencyDisplay(
        granularity = FrequencyGranularity.DAY,
        bars =
            listOf(
                FrequencyBar(LocalDate.of(2026, 9, 4), 1, 1f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 5), 0, 0f),
                FrequencyBar(LocalDate.of(2026, 9, 6), 2, 2f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 7), 3, 3f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 8), 1, 1f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 9), 0, 0f),
                FrequencyBar(LocalDate.of(2026, 9, 10), 4, 1f),
                FrequencyBar(LocalDate.of(2026, 9, 11), 2, 2f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 12), 1, 1f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 13), 3, 3f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 14), 2, 2f / 4),
                FrequencyBar(LocalDate.of(2026, 9, 15), 1, 1f / 4),
            ),
    )

private val previewFrequencyDisplayWeek =
    FrequencyDisplay(
        granularity = FrequencyGranularity.WEEK,
        bars =
            listOf(
                FrequencyBar(LocalDate.of(2026, 6, 29), 3, 3f / 5),
                FrequencyBar(LocalDate.of(2026, 7, 6), 2, 2f / 5),
                FrequencyBar(LocalDate.of(2026, 7, 13), 4, 4f / 5),
                FrequencyBar(LocalDate.of(2026, 7, 20), 1, 1f / 5),
                FrequencyBar(LocalDate.of(2026, 7, 27), 5, 1f),
                FrequencyBar(LocalDate.of(2026, 8, 3), 3, 3f / 5),
                FrequencyBar(LocalDate.of(2026, 8, 10), 2, 2f / 5),
                FrequencyBar(LocalDate.of(2026, 8, 17), 4, 4f / 5),
                FrequencyBar(LocalDate.of(2026, 8, 24), 3, 3f / 5),
                FrequencyBar(LocalDate.of(2026, 8, 31), 1, 1f / 5),
                FrequencyBar(LocalDate.of(2026, 9, 7), 2, 2f / 5),
                FrequencyBar(LocalDate.of(2026, 9, 14), 3, 3f / 5),
            ),
    )

private val previewFrequencyDisplayMonth =
    FrequencyDisplay(
        granularity = FrequencyGranularity.MONTH,
        bars =
            listOf(
                FrequencyBar(LocalDate.of(2025, 10, 1), 5, 5f / 7),
                FrequencyBar(LocalDate.of(2025, 11, 1), 3, 3f / 7),
                FrequencyBar(LocalDate.of(2025, 12, 1), 6, 6f / 7),
                FrequencyBar(LocalDate.of(2026, 1, 1), 4, 4f / 7),
                FrequencyBar(LocalDate.of(2026, 2, 1), 2, 2f / 7),
                FrequencyBar(LocalDate.of(2026, 3, 1), 7, 1f),
                FrequencyBar(LocalDate.of(2026, 4, 1), 5, 5f / 7),
                FrequencyBar(LocalDate.of(2026, 5, 1), 3, 3f / 7),
                FrequencyBar(LocalDate.of(2026, 6, 1), 4, 4f / 7),
                FrequencyBar(LocalDate.of(2026, 7, 1), 6, 6f / 7),
                FrequencyBar(LocalDate.of(2026, 8, 1), 5, 5f / 7),
                FrequencyBar(LocalDate.of(2026, 9, 1), 4, 4f / 7),
            ),
    )

private val previewGapsDisplay =
    GapsDisplay(
        longestGapDays = 5,
        currentGapDays = 2,
        averageGapDays = 3.5,
        isBursty = true,
        longestStreakDays = 3,
        averageStreakDays = 1.8,
    )

/** One realistic Trends finding set — a compact card sitting first in the stack, not an isolated showcase of every count scenario (guardrail/cap behavior is covered by tests, not by eyeballing variants here). Includes a frequency-shift finding since that now absorbs the former standalone arrow card. */
private val previewTrendsFindings =
    listOf(
        TrendFinding(TrendFindingKind.FREQUENCY_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 20, 8.0, 12.0),
        TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 9, 3.2, 5.8),
        TrendFinding(TrendFindingKind.STREAK_SHIFT, ShiftDirection.DOWN, TrendReliability.HINT, 7, 4.0, 2.0),
    )

/** Exercises [InsightsCard]'s Bright branch (via [TrendsCard]/[FrequencyCard]/[GapsCard]) and [FrequencyCard]'s gradient bars together. */
@Composable
private fun InsightsBrightCardsPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
        LocalVoice provides voiceFor(AppTheme.BRIGHT),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            TrendsCard(previewTrendsFindings, LocalVoice.current, onShowMore = {})
            FrequencyCard(previewFrequencyDisplayWeek, null, {}, LocalVoice.current)
            GapsCard(previewGapsDisplay, LocalVoice.current)
        }
    }
}

/** Plain's white-plank cards (`surfaceContainerLow` authored white) on the tinted screen background. */
@Composable
private fun InsightsPlainCardsPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
        LocalVoice provides voiceFor(AppTheme.PLAIN),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TrendsCard(previewTrendsFindings, LocalVoice.current, onShowMore = {})
                FrequencyCard(previewFrequencyDisplayWeek, null, {}, LocalVoice.current)
                GapsCard(previewGapsDisplay, LocalVoice.current)
            }
        }
    }
}

/** As [InsightsPlainCardsPreviewContent], for Intense — this card stack previously had no Intense coverage at all. */
@Composable
private fun InsightsIntenseCardsPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.INTENSE,
        LocalVoice provides voiceFor(AppTheme.INTENSE),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TrendsCard(previewTrendsFindings, LocalVoice.current, onShowMore = {})
                FrequencyCard(previewFrequencyDisplayWeek, null, {}, LocalVoice.current)
                GapsCard(previewGapsDisplay, LocalVoice.current)
            }
        }
    }
}

@Preview(name = "Insights cards — Plain light", showBackground = true, widthDp = 380)
@Composable
private fun InsightsPlainCardsLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        InsightsPlainCardsPreviewContent()
    }
}

@Preview(name = "Insights cards — Intense light", showBackground = true, widthDp = 380)
@Composable
private fun InsightsIntenseCardsLightPreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        InsightsIntenseCardsPreviewContent()
    }
}

@Preview(name = "Insights cards — Bright light", showBackground = true, widthDp = 380)
@Composable
private fun InsightsBrightCardsLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        InsightsBrightCardsPreviewContent()
    }
}

@Preview(name = "Insights cards — Bright dark", showBackground = true, widthDp = 380)
@Composable
private fun InsightsBrightCardsDarkPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = true) {
        InsightsBrightCardsPreviewContent()
    }
}

/** More than [TRENDS_DEFAULT_VISIBLE_COUNT] findings — exercises the "show more" link on its own, as a single card rather than stacked next to other scenarios. */
private val previewTrendsFindingsOverCap =
    previewTrendsFindings +
        listOf(
            TrendFinding(TrendFindingKind.STREAK_SHIFT, ShiftDirection.UP, TrendReliability.PATTERN, 11, 2.0, 4.5),
            TrendFinding(TrendFindingKind.GAP_SHIFT, ShiftDirection.UP, TrendReliability.HINT, 6, 6.0, 8.5),
        )

@Composable
private fun TrendsCardShowMorePreviewContent(
    theme: AppTheme,
    cardStyle: CardDecorationStyle,
) {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides cardStyle,
        LocalVoice provides voiceFor(theme),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.padding(16.dp)) {
                TrendsCard(previewTrendsFindingsOverCap, LocalVoice.current, onShowMore = {})
            }
        }
    }
}

@Preview(name = "Trends card — show more — Plain light", showBackground = true, widthDp = 380)
@Composable
private fun TrendsCardShowMorePlainPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        TrendsCardShowMorePreviewContent(AppTheme.PLAIN, CardDecorationStyle.PLAIN)
    }
}

@Preview(name = "Trends card — show more — Intense light", showBackground = true, widthDp = 380)
@Composable
private fun TrendsCardShowMoreIntensePreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        TrendsCardShowMorePreviewContent(AppTheme.INTENSE, CardDecorationStyle.INTENSE)
    }
}

@Preview(name = "Trends card — show more — Bright light", showBackground = true, widthDp = 380)
@Composable
private fun TrendsCardShowMoreBrightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        TrendsCardShowMorePreviewContent(AppTheme.BRIGHT, CardDecorationStyle.BRIGHT)
    }
}

/**
 * S9: renders [FrequencyCard] alone, for the six `@Preview`s below at `widthDp` 320 (the narrowest
 * width previewed anywhere in this codebase, standing in for "minimum supported screen width"
 * since no exact figure is documented) across Plain and Intense — Oswald Bold, the widest of the
 * three themes' tick-label typefaces and, until now, a theme this card had no preview coverage in
 * at all.
 */
@Composable
private fun FrequencyTickPreviewContent(
    theme: AppTheme,
    cardStyle: CardDecorationStyle,
    display: FrequencyDisplay,
) {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides cardStyle,
        LocalVoice provides voiceFor(theme),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                FrequencyCard(display, null, {}, LocalVoice.current)
            }
        }
    }
}

@Preview(name = "Frequency chart — Plain Day — 320dp", showBackground = true, widthDp = 320)
@Composable
private fun FrequencyChartPlainDayPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        FrequencyTickPreviewContent(AppTheme.PLAIN, CardDecorationStyle.PLAIN, previewFrequencyDisplayDay)
    }
}

@Preview(name = "Frequency chart — Plain Week — 320dp", showBackground = true, widthDp = 320)
@Composable
private fun FrequencyChartPlainWeekPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        FrequencyTickPreviewContent(AppTheme.PLAIN, CardDecorationStyle.PLAIN, previewFrequencyDisplayWeek)
    }
}

@Preview(name = "Frequency chart — Plain Month — 320dp", showBackground = true, widthDp = 320)
@Composable
private fun FrequencyChartPlainMonthPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        FrequencyTickPreviewContent(AppTheme.PLAIN, CardDecorationStyle.PLAIN, previewFrequencyDisplayMonth)
    }
}

@Preview(name = "Frequency chart — Intense Day — 320dp", showBackground = true, widthDp = 320)
@Composable
private fun FrequencyChartIntenseDayPreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        FrequencyTickPreviewContent(AppTheme.INTENSE, CardDecorationStyle.INTENSE, previewFrequencyDisplayDay)
    }
}

@Preview(name = "Frequency chart — Intense Week — 320dp", showBackground = true, widthDp = 320)
@Composable
private fun FrequencyChartIntenseWeekPreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        FrequencyTickPreviewContent(AppTheme.INTENSE, CardDecorationStyle.INTENSE, previewFrequencyDisplayWeek)
    }
}

@Preview(name = "Frequency chart — Intense Month — 320dp", showBackground = true, widthDp = 320)
@Composable
private fun FrequencyChartIntenseMonthPreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        FrequencyTickPreviewContent(AppTheme.INTENSE, CardDecorationStyle.INTENSE, previewFrequencyDisplayMonth)
    }
}

// S10 drill-down row previews. AlertDialog content doesn't render inside Android Studio's static
// @Preview surface (a platform Dialog/Popup limitation, same reason BigPictureGrid's own detail
// dialogs have no Preview), so these exercise InsightsDrillDownEventRow directly rather than the
// full InsightsDrillDownDialog — the part that actually varies per theme.
private val previewDrillDownEvents =
    listOf(
        EventWithTags(
            event =
                EventEntity(
                    id = 1,
                    caseId = 1,
                    occurredAt =
                        LocalDate
                            .of(2026, 7, 14)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    endedAt = null,
                    intensity = 4,
                    note = "Woke up mid-thunderstorm",
                    loggedAt = 0,
                ),
            tags = listOf(TagEntity(id = 1, name = "night")),
        ),
        EventWithTags(
            event =
                EventEntity(
                    id = 2,
                    caseId = 1,
                    occurredAt =
                        LocalDate
                            .of(2026, 7, 10)
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli(),
                    endedAt = null,
                    intensity = null,
                    note = null,
                    loggedAt = 0,
                ),
            tags = emptyList(),
        ),
    )

@Composable
private fun InsightsDrillDownRowsPreviewContent() {
    Column {
        previewDrillDownEvents.forEach { eventWithTags ->
            InsightsDrillDownEventRow(
                eventWithTags = eventWithTags,
                now =
                    LocalDate
                        .of(2026, 7, 15)
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                voice = LocalVoice.current,
                durationMode = DurationMode.NONE,
                onClick = {},
            )
        }
    }
}

@Preview(name = "Insights drill-down rows — Plain light", showBackground = true, widthDp = 380)
@Composable
private fun InsightsDrillDownRowsPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.padding(16.dp)) { InsightsDrillDownRowsPreviewContent() }
            }
        }
    }
}

@Preview(name = "Insights drill-down rows — Intense light", showBackground = true, widthDp = 380)
@Composable
private fun InsightsDrillDownRowsIntenseLightPreview() {
    HodithTheme(theme = AppTheme.INTENSE, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.INTENSE,
            LocalVoice provides voiceFor(AppTheme.INTENSE),
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.padding(16.dp)) { InsightsDrillDownRowsPreviewContent() }
            }
        }
    }
}

@Preview(name = "Insights drill-down rows — Bright light", showBackground = true, widthDp = 380)
@Composable
private fun InsightsDrillDownRowsBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.padding(16.dp)) { InsightsDrillDownRowsPreviewContent() }
            }
        }
    }
}
