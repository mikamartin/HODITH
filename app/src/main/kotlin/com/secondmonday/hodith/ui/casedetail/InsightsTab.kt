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
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.domain.INTENSITY_MIN
import com.secondmonday.hodith.domain.RHYTHM_TIER_COUNT
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.activeSpanEnd
import com.secondmonday.hodith.domain.datesCovered
import com.secondmonday.hodith.domain.heatmapLevelFor
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
import com.secondmonday.hodith.viewmodel.TrendDisplay
import com.secondmonday.hodith.viewmodel.eventDetailSummary
import com.secondmonday.hodith.viewmodel.formatEventTime
import com.secondmonday.hodith.viewmodel.formatFrequencyPeriodLabel
import com.secondmonday.hodith.viewmodel.formatMediumDate
import com.secondmonday.hodith.viewmodel.formatMinutesDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val HEATMAP_DEFAULT_MONTH_COUNT = 3
private const val FREQUENCY_BAR_CHART_HEIGHT = 80
private const val FREQUENCY_MIN_BAR_HEIGHT_FRACTION = 0.02f

/** Bars use only this fraction of the chart's height, reserving headroom so a full-height bar's count label never crowds the row above. */
private const val FREQUENCY_BAR_MAX_HEIGHT_FRACTION = 0.8f
private const val FREQUENCY_BAR_LABEL_GAP = 2
private const val FREQUENCY_CHART_TOP_SPACING = 16
private const val RHYTHM_CELL_SIZE = 20

/** Wide enough for "Afternoon" — the longest time-of-day label — to fit on one line in every theme's display font, Baloo2 Bold (Bright) included. A fixed width (not `Modifier.weight`) keeps the label snug against the grid instead of stretching to fill the row. */
private const val RHYTHM_LABEL_WIDTH = 88

/**
 * Case Detail's Insights tab (spec §9-10): the seven stat cards followed by the per-case calendar
 * heatmap. With zero events a flat invitation replaces the whole tab; from the first event the
 * heatmap, a one-line count note, and the Rhythm and Gaps cards render (spec §9's Big Picture
 * carve-out), with Frequency and Trend held back until [INSIGHTS_MIN_EVENTS] events.
 *
 * Spec §9/§10 drill-down (S10): a heatmap day, an intensity square, or a tag row opens the logged
 * events behind it in a shared [InsightsDrillDownDialog], filtered in memory over [events] —
 * [case]/[now] carry just enough to format and open a row via [onEditEvent], same shape as the Log
 * tab's own [EventEntity]-keyed callback.
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
    modifier: Modifier = Modifier,
) {
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }
    var selectedIntensity by remember { mutableStateOf<Int?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val zone = remember { ZoneId.systemDefault() }

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
                    onIntensityTap = { selectedIntensity = it },
                    onTagTap = { selectedTag = it },
                )
                CalendarHeatmapCard(state.heatmapMonths, voice, onDayTap = { selectedDay = it })
            }
    }

    selectedDay?.let { day ->
        InsightsDrillDownDialog(
            title = formatMediumDate(day),
            events =
                events
                    .filter { day in datesCovered(it.event.occurredAt, activeSpanEnd(it.event, case.durationMode, now), zone) }
                    .sortedBy { it.event.occurredAt },
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
}

/** Spec §10's seven stat sections, in spec order. [StatsSections.frequency]/[trend]/[duration]/[intensity] omit their card entirely when absent. */
@Composable
private fun StatsSectionCards(
    stats: StatsSections,
    frequencyGranularityOverride: FrequencyGranularity?,
    onFrequencyGranularityChange: (FrequencyGranularity?) -> Unit,
    voice: Voice,
    onIntensityTap: (Int) -> Unit,
    onTagTap: (String) -> Unit,
) {
    stats.frequency?.let { FrequencyCard(it, frequencyGranularityOverride, onFrequencyGranularityChange, voice) }
    RhythmCard(stats.rhythm, voice)
    GapsCard(stats.gaps, voice)
    stats.trend?.let { TrendCard(it, voice) }
    stats.duration?.let { DurationCard(it, voice) }
    stats.intensity?.let { IntensityCard(it, voice, onIntensityTap) }
    if (stats.tags.isNotEmpty()) TagsCard(stats.tags, stats.totalEventCount, voice, onTagTap)
}

/**
 * Shared shell for every Insights card — full-width [Card] with a padded, vertically-spaced
 * [Column]. Bright branches to [GlowCard] (Soft Glow mockup's `.card`), same dispatch as
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
    val axisLabel = { periodStart: LocalDate ->
        formatFrequencyPeriodLabel(periodStart, display.granularity, locale, voice::insightsFrequencyWeekAxisLabel)
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
            Row(modifier = Modifier.fillMaxWidth().padding(top = FREQUENCY_CHART_TOP_SPACING.dp).height(FREQUENCY_BAR_CHART_HEIGHT.dp)) {
                display.bars.forEach { bar ->
                    val barHeight =
                        FREQUENCY_BAR_CHART_HEIGHT.dp *
                            bar.heightFraction.coerceAtLeast(FREQUENCY_MIN_BAR_HEIGHT_FRACTION) *
                            FREQUENCY_BAR_MAX_HEIGHT_FRACTION
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
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
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = axisLabel(display.bars.first().periodStart),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = axisLabel(display.bars.last().periodStart),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Bright's bars fade from full [Color]`.primary` at the top to this fraction toward the surface at the bottom (mockup's `.bars-row .bar` gradient); Plain/Intense keep a flat fill. */
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

/** Spec §10 rhythm heatmap: day-of-week columns x time-of-day rows, shaded like the calendar heatmap. */
@Composable
private fun RhythmCard(
    display: RhythmDisplay,
    voice: Voice,
) {
    val timeOfDayLabel: (TimeOfDay) -> String = { timeOfDay ->
        when (timeOfDay) {
            TimeOfDay.MORNING -> voice.insightsTimeOfDayMorning
            TimeOfDay.AFTERNOON -> voice.insightsTimeOfDayAfternoon
            TimeOfDay.EVENING -> voice.insightsTimeOfDayEvening
            TimeOfDay.NIGHT -> voice.insightsTimeOfDayNight
        }
    }
    val locale = LocalLocale.current.platformLocale

    InsightsCard {
        Text(
            if (display.plottedByStart) voice.insightsSectionLabelRhythmStarts else voice.insightsSectionLabelRhythm,
            style = MaterialTheme.typography.titleSmall,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(RHYTHM_LABEL_WIDTH.dp))
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
                    text = timeOfDayLabel(timeOfDay),
                    modifier = Modifier.width(RHYTHM_LABEL_WIDTH.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayOfWeek.entries.forEach { day ->
                    val level = display.cells.first { it.dayOfWeek == day && it.timeOfDay == timeOfDay }.level
                    Box(
                        modifier =
                            Modifier
                                .size(RHYTHM_CELL_SIZE.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(level.toCellColor(tierCount = RHYTHM_TIER_COUNT)),
                    )
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

/** Spec §10 trend arrow: last 30 days vs. the 30 before — descriptive only, never a value judgement. Gap/streak shift notes are shown only when noticeable. */
@Composable
private fun TrendCard(
    display: TrendDisplay,
    voice: Voice,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelTrend, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text =
                    when (display.direction) {
                        TrendDirection.UP -> "↑"
                        TrendDirection.DOWN -> "↓"
                        TrendDirection.FLAT -> "→"
                    },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = voice.insightsTrendSentence(display.direction, display.recentCount, display.priorCount),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        display.gapShiftDirection?.let {
            Text(
                text = voice.insightsGapShiftSentence(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        display.streakShiftDirection?.let {
            Text(
                text = voice.insightsStreakShiftSentence(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Spec §10 duration stats — only shown when the Case's `durationMode != NONE`. */
@Composable
private fun DurationCard(
    display: DurationDisplay,
    voice: Voice,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelDuration, style = MaterialTheme.typography.titleSmall)
        StatRow(voice.insightsDurationAverageLabel, formatMinutesDuration(display.averageMinutes.roundToInt().toLong()))
        StatRow(voice.insightsDurationLongestLabel, formatMinutesDuration(display.longestMinutes))
        StatRow(voice.insightsDurationTotalLabel, formatMinutesDuration(display.totalMinutes))
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
        StatRow(voice.insightsIntensityAverageLabel, String.format(Locale.US, "%.1f", display.averageIntensity))
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
            text = formatEventTime(event.occurredAt, now, LocalTimeFormat.current.is24Hour),
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

private val previewFrequencyDisplay =
    FrequencyDisplay(
        granularity = FrequencyGranularity.WEEK,
        bars =
            listOf(
                FrequencyBar(LocalDate.of(2026, 6, 1), 2, 0.35f),
                FrequencyBar(LocalDate.of(2026, 6, 8), 3, 0.55f),
                FrequencyBar(LocalDate.of(2026, 7, 1), 3, 0.40f),
                FrequencyBar(LocalDate.of(2026, 7, 8), 5, 0.80f),
                FrequencyBar(LocalDate.of(2026, 7, 15), 4, 0.60f),
                FrequencyBar(LocalDate.of(2026, 8, 1), 6, 0.95f),
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

/** Exercises [InsightsCard]'s Bright branch (via [FrequencyCard]/[GapsCard]) and [FrequencyCard]'s gradient bars together. */
@Composable
private fun InsightsBrightCardsPreviewContent() {
    CompositionLocalProvider(
        LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
        LocalVoice provides voiceFor(AppTheme.BRIGHT),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FrequencyCard(previewFrequencyDisplay, null, {}, LocalVoice.current)
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
                FrequencyCard(previewFrequencyDisplay, null, {}, LocalVoice.current)
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
