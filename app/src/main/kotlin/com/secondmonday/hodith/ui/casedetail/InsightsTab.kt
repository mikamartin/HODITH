package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.HeatmapDay
import com.secondmonday.hodith.viewmodel.HeatmapMonth
import com.secondmonday.hodith.viewmodel.InsightsTabState
import com.secondmonday.hodith.viewmodel.TimelineDisplay
import com.secondmonday.hodith.viewmodel.TimelineToken
import java.time.DayOfWeek
import java.time.YearMonth

private const val TIMELINE_NOW_LABEL = "Today"
private const val DOT_SIZE = 8
private const val NOW_TICK_HEIGHT = 14
private const val HEATMAP_DEFAULT_MONTH_COUNT = 3

/**
 * Case Detail's Insights tab (spec §9's visuals half): a full-width dot timeline, primary, atop a
 * per-case calendar heatmap, secondary. Below [com.secondmonday.hodith.domain.INSIGHTS_MIN_EVENTS]
 * events neither has a gap or a pattern to show, so a placeholder replaces both entirely.
 */
@Composable
internal fun InsightsTabContent(
    state: InsightsTabState,
    voice: Voice,
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
            }
    }
}

/** Shared shell for both Insights cards — full-width [Card] with a padded, vertically-spaced [Column]. */
@Composable
private fun InsightsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
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

@Composable
private fun HeatmapLevel.toCellColor(): Color {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val full = MaterialTheme.colorScheme.primary
    return when (this) {
        HeatmapLevel.EMPTY -> empty
        HeatmapLevel.L1 -> lerp(empty, full, 0.28f)
        HeatmapLevel.L2 -> lerp(empty, full, 0.52f)
        HeatmapLevel.L3 -> lerp(empty, full, 0.76f)
        HeatmapLevel.L4 -> full
    }
}

/** L3/L4 cells are saturated enough to need on-primary contrast; lighter cells read fine with the muted default. */
@Composable
private fun HeatmapLevel.toTextColor(): Color =
    when (this) {
        HeatmapLevel.EMPTY, HeatmapLevel.L1, HeatmapLevel.L2 -> MaterialTheme.colorScheme.onSurfaceVariant
        HeatmapLevel.L3, HeatmapLevel.L4 -> MaterialTheme.colorScheme.onPrimary
    }

private fun YearMonth.monthYearLabel(): String = "${month.name.lowercase().replaceFirstChar { it.uppercase() }} $year"
