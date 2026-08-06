package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.domain.INTENSITY_MIN
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.ui.common.SectionWithInfo
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.common.toCellColor
import com.secondmonday.hodith.ui.common.toTextColor
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.GlowCard
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
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
import com.secondmonday.hodith.viewmodel.TimelineDisplay
import com.secondmonday.hodith.viewmodel.TimelineToken
import com.secondmonday.hodith.viewmodel.TrendDisplay
import com.secondmonday.hodith.viewmodel.formatMinutesDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private const val TIMELINE_NOW_LABEL = "Today"
private const val DOT_SIZE = 8
private const val NOW_TICK_HEIGHT = 14
private const val HEATMAP_DEFAULT_MONTH_COUNT = 3
private const val FREQUENCY_BAR_CHART_HEIGHT = 80
private const val FREQUENCY_MIN_BAR_HEIGHT_FRACTION = 0.02f

/** Bars use only this fraction of the chart's height, reserving headroom so a full-height bar's count label never crowds the row above. */
private const val FREQUENCY_BAR_MAX_HEIGHT_FRACTION = 0.8f
private const val FREQUENCY_BAR_LABEL_GAP = 2
private const val FREQUENCY_CHART_TOP_SPACING = 16
private const val RHYTHM_CELL_SIZE = 20

/**
 * Case Detail's Insights tab (spec §9's visuals half): a full-width dot timeline, primary, atop a
 * per-case calendar heatmap, secondary. Below [com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS]
 * events neither has a gap or a pattern to show, so a placeholder replaces both entirely.
 */
@Composable
internal fun InsightsTabContent(
    state: InsightsTabState,
    voice: Voice,
    frequencyGranularityOverride: FrequencyGranularity?,
    onFrequencyGranularityChange: (FrequencyGranularity?) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is InsightsTabState.NotEnoughData ->
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(voice.insightsNotEnoughDataMessage)
            }
        is InsightsTabState.Ready ->
            Column(
                modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DotTimelineCard(state.timeline, voice)
                CalendarHeatmapCard(state.heatmapMonths, voice)
                StatsSectionCards(state.stats, frequencyGranularityOverride, onFrequencyGranularityChange, voice)
            }
    }
}

/** Spec §10's seven stat sections, in spec order. [StatsSections.trend]/[duration]/[intensity] omit their card entirely when absent. */
@Composable
private fun StatsSectionCards(
    stats: StatsSections,
    frequencyGranularityOverride: FrequencyGranularity?,
    onFrequencyGranularityChange: (FrequencyGranularity?) -> Unit,
    voice: Voice,
) {
    FrequencyCard(stats.frequency, frequencyGranularityOverride, onFrequencyGranularityChange, voice)
    RhythmCard(stats.rhythm, voice)
    GapsCard(stats.gaps, voice)
    stats.trend?.let { TrendCard(it, voice) }
    stats.duration?.let { DurationCard(it, voice) }
    stats.intensity?.let { IntensityCard(it, voice) }
    if (stats.tags.isNotEmpty()) TagsCard(stats.tags, stats.totalEventCount, voice)
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

@Composable
private fun DotTimelineCard(
    timeline: TimelineDisplay,
    voice: Voice,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelTimeline, style = MaterialTheme.typography.titleSmall)
        DotTimelineRow(timeline.tokens)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTimelineWindowStartLabel(timeline.windowDays),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = TIMELINE_NOW_LABEL,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text =
                if (timeline.isCurrentGapLongest) {
                    voice.insightsGapNoteLongest(timeline.currentGapDays)
                } else {
                    voice.insightsGapNoteCurrent(timeline.currentGapDays)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/**
 * Dots and gaps float over a thin baseline (mirrors the validated mockup's `.dot-timeline::before`).
 * Several events on the same day collapse into one [TimelineToken.Dot], shaded by
 * [HeatmapLevel] instead of drawn as separate overlapping dots.
 */
@Composable
private fun DotTimelineRow(tokens: List<TimelineToken>) {
    Box(modifier = Modifier.fillMaxWidth().height(22.dp)) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            tokens.forEach { token ->
                when (token) {
                    is TimelineToken.Dot ->
                        Box(
                            modifier =
                                Modifier
                                    .size(DOT_SIZE.dp)
                                    .clip(CircleShape)
                                    .background(token.level.toCellColor()),
                        )
                    is TimelineToken.Gap -> Spacer(modifier = Modifier.weight(token.weight))
                }
            }
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .height(NOW_TICK_HEIGHT.dp)
                        .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

private fun formatTimelineWindowStartLabel(windowDays: Long): String {
    val weeks = windowDays / 7
    val remainderDays = windowDays % 7
    return when {
        remainderDays == 0L && weeks >= 1 -> if (weeks == 1L) "1 week ago" else "$weeks weeks ago"
        windowDays == 1L -> "1 day ago"
        else -> "$windowDays days ago"
    }
}

/** Most recent month first, [HEATMAP_DEFAULT_MONTH_COUNT] shown by default with the rest behind a toggle. */
@Composable
private fun CalendarHeatmapCard(
    months: List<HeatmapMonth>,
    voice: Voice,
) {
    var expanded by remember { mutableStateOf(false) }
    val orderedMonths = months.asReversed()
    val visibleMonths = if (expanded) orderedMonths else orderedMonths.take(HEATMAP_DEFAULT_MONTH_COUNT)

    InsightsCard {
        Text(voice.insightsSectionLabelHeatmap, style = MaterialTheme.typography.titleSmall)
        HeatmapWeekdayHeader()
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            visibleMonths.forEach { month -> HeatmapMonthGrid(month) }
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
private fun HeatmapMonthGrid(month: HeatmapMonth) {
    Column {
        Text(
            text = month.month.monthYearLabel(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        month.weeks.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                week.forEach { day -> HeatmapCell(day, modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: HeatmapDay?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(1.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (day == null) Color.Transparent else day.level.toCellColor()),
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
                    text = formatFrequencyPeriodLabel(display.bars.first().periodStart, display.granularity, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatFrequencyPeriodLabel(display.bars.last().periodStart, display.granularity, locale),
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

private fun formatFrequencyPeriodLabel(
    periodStart: LocalDate,
    granularity: FrequencyGranularity,
    locale: Locale,
): String =
    when (granularity) {
        FrequencyGranularity.DAY -> periodStart.format(DateTimeFormatter.ofPattern("MMM d", locale))
        FrequencyGranularity.WEEK -> "Week of ${periodStart.format(DateTimeFormatter.ofPattern("MMM d", locale))}"
        FrequencyGranularity.MONTH -> "${periodStart.month.getDisplayName(TextStyle.SHORT, locale)} ${periodStart.year}"
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
        Text(voice.insightsSectionLabelRhythm, style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.weight(1f))
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
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DayOfWeek.entries.forEach { day ->
                    val level = display.cells.first { it.dayOfWeek == day && it.timeOfDay == timeOfDay }.level
                    Box(
                        modifier =
                            Modifier
                                .size(RHYTHM_CELL_SIZE.dp)
                                .padding(1.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(level.toCellColor()),
                    )
                }
            }
        }
    }
}

/** Spec §10 gaps & clusters: longest/current/average gap plus the "tends to come in bursts" flag. */
@Composable
private fun GapsCard(
    display: GapsDisplay,
    voice: Voice,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelGaps, style = MaterialTheme.typography.titleSmall)
        StatRow(voice.insightsGapsLongestLabel, formatDays(display.longestGapDays.toDouble()))
        StatRow(voice.insightsGapsCurrentLabel, formatDays(display.currentGapDays.toDouble()))
        StatRow(voice.insightsGapsAverageLabel, formatDays(display.averageGapDays))
        if (display.isBursty) {
            Text(
                text = voice.insightsBurstFlagLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Spec §10 trend arrow: last 30 days vs. the 30 before — descriptive only, never a value judgement. */
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

/** Spec §10 intensity stats — only shown when the Case has `intensityEnabled`. A row of five shaded squares, one per intensity level. */
@Composable
private fun IntensityCard(
    display: IntensityDisplay,
    voice: Voice,
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
                            .background(level.toCellColor()),
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
 * entirely when no event carries a tag.
 */
@Composable
private fun TagsCard(
    tags: List<TagBreakdownEntry>,
    totalEventCount: Int,
    voice: Voice,
) {
    InsightsCard {
        Text(voice.insightsSectionLabelTags, style = MaterialTheme.typography.titleSmall)
        StatRow(voice.insightsTagsTotalLabel, totalEventCount.toString())
        tags.forEach { tag -> StatRow(tag.tagName, tag.count.toString()) }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
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

private val previewGapsDisplay = GapsDisplay(longestGapDays = 5, currentGapDays = 2, averageGapDays = 3.5, isBursty = true)

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
