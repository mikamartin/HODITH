package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.HeroRate
import com.secondmonday.hodith.domain.HeroRateComparison
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.domain.INTENSITY_MIN
import com.secondmonday.hodith.domain.RHYTHM_TIER_COUNT
import com.secondmonday.hodith.domain.RateUnit
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.ui.casedetail.formatCompactDecimal
import com.secondmonday.hodith.ui.casedetail.formatDaysCompact
import com.secondmonday.hodith.ui.casedetail.formatIntensity
import com.secondmonday.hodith.ui.casedetail.trendFindingSentence
import com.secondmonday.hodith.ui.common.toCellColor
import com.secondmonday.hodith.ui.common.toTextColor
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalShareCardSkin
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.theme.ShareCardSkin
import com.secondmonday.hodith.ui.voice.BrightVoice
import com.secondmonday.hodith.ui.voice.IntenseVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.DurationDisplay
import com.secondmonday.hodith.viewmodel.GapsDisplay
import com.secondmonday.hodith.viewmodel.IntensityDisplay
import com.secondmonday.hodith.viewmodel.LogCardRow
import com.secondmonday.hodith.viewmodel.RhythmCellDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.ShareCardData
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareTopBeat
import com.secondmonday.hodith.viewmodel.formatCardTimestamp
import com.secondmonday.hodith.viewmodel.formatMinutesDuration
import java.time.DayOfWeek
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Matches the render-pipeline spike's fixed capture width — see PROGRESS.md's Phase 10 share-cards width decision. */
private val SHARE_CARD_WIDTH = 360.dp

/** Square's 1:1 floor at [SHARE_CARD_WIDTH] — kept because chat/feed shares render whatever aspect ratio they're given, unlike Story's destination apps. */
private val SQUARE_MIN_HEIGHT = SHARE_CARD_WIDTH
private const val MINI_RHYTHM_CELL_SIZE = 24

/** Intensity squares are fixed-size, close to what full-width stretching gave, so they sit in scale with the Start times grid. */
private const val INTENSITY_CELL_SIZE = 48

/** Wide enough for "Afternoon" — the longest time-of-day label — to fit on one line in every theme's display font, Baloo2 Bold (Bright) included. */
private const val MINI_RHYTHM_LABEL_WIDTH = 88

/** Space between the time-of-day labels and the first grid column, so the labels don't crowd the cells. */
private const val MINI_RHYTHM_LABEL_GAP = 12

/** Square summary beat: the headline figure and its unit, in sp so they track the user's font scale like every other card text. */
private const val SUMMARY_FIGURE_FONT_SIZE = 40
private const val SUMMARY_COUNT_FONT_SIZE = 32
private const val SUMMARY_UNIT_FONT_SIZE = 15
private const val TREND_TRIANGLE_SIZE = 8
private const val TREND_TRIANGLE_DOWN_DEGREES = 180f
private const val TREND_TRIANGLE_FLAT_DEGREES = 90f
private const val QUIET_LABEL_DASH_ON = 4
private const val QUIET_LABEL_DASH_OFF = 3

/**
 * Spec §13's share card — one Compose tree reused for both the preview screen and the actual
 * export capture (`ComposeShareImageExporter`). Sections are mini-scale counterparts of the real
 * `InsightsTab.kt` composables (same [com.secondmonday.hodith.ui.common.toCellColor] shading, same
 * [MiniInsightsCard] chrome as [com.secondmonday.hodith.ui.casedetail.InsightsTab]'s `InsightsCard`)
 * rather than the real composables reused directly — the real ones are sized for an adaptive phone
 * screen, not this fixed-width/content-driven-height export canvas. [ShareCardData] already
 * decides which beat and which sections apply; this composable only renders what it's given.
 */
@Composable
fun ShareCardTemplate(
    data: ShareCardData,
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    val skin = LocalShareCardSkin.current
    // Square's floor applies to Insights cards only; a Log card is content-sized.
    val squareFloor =
        if ((data as? ShareCardData.Insights)?.format == ShareCardFormat.SQUARE) {
            Modifier.heightIn(min = SQUARE_MIN_HEIGHT)
        } else {
            Modifier
        }

    Column(
        modifier =
            modifier
                .width(SHARE_CARD_WIDTH)
                .then(squareFloor)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.background)
                .then(
                    if (skin == ShareCardSkin.INTENSE) {
                        Modifier.border(2.dp, MaterialTheme.colorScheme.onBackground, MaterialTheme.shapes.extraLarge)
                    } else {
                        Modifier
                    },
                ),
        // SpaceBetween redistributes slack only after this Column's content-driven height is settled —
        // the previous weight(1f) sized against remaining space beforehand, which could shrink content.
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box {
            Column {
                CaseHeaderBeat(data.caseIcon, data.caseName, skin)
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    if (skin == ShareCardSkin.PLAIN) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    when (data) {
                        is ShareCardData.Insights -> InsightsCardBody(data, voice, skin)
                        is ShareCardData.Log -> LogCardBody(data, voice, skin)
                    }
                }
            }
            when (skin) {
                ShareCardSkin.INTENSE -> IntenseStampBadge(voice)
                ShareCardSkin.BRIGHT -> Text("✨", modifier = Modifier.align(Alignment.TopEnd).padding(10.dp))
                ShareCardSkin.PLAIN -> Unit
            }
        }
        ShareCardFooter(data.generatedAtMillis, voice, skin)
    }
}

@Composable
private fun InsightsCardBody(
    data: ShareCardData.Insights,
    voice: Voice,
    skin: ShareCardSkin,
) {
    if (data.format == ShareCardFormat.SQUARE) {
        SquareInsightsBody(data, voice, skin)
        return
    }
    StoryInsightsBody(data, voice, skin)
}

/** Log Share's body: [BeatKicker] (reused as-is), the resolved range as a subtitle, then every row, then an optional truncation note. */
@Composable
private fun LogCardBody(
    data: ShareCardData.Log,
    voice: Voice,
    skin: ShareCardSkin,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BeatKicker(voice.shareLogCardKicker, skin)
        Text(
            text = data.rangeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
    if (data.rows.isEmpty()) {
        Text(text = voice.shareLogEmptyRangeMessage, style = MaterialTheme.typography.labelMedium)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            data.rows.forEach { row -> LogEntryRow(row, skin) }
        }
    }
    data.truncatedTotalCount?.let { totalCount ->
        Text(
            text = voice.shareLogTruncationNote(data.rows.size, totalCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LogEntryRow(
    row: LogCardRow,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        Text(
            text = if (skin == ShareCardSkin.INTENSE) row.timestamp.uppercase() else row.timestamp,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
        row.detail?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CaseHeaderBeat(
    caseIcon: String,
    caseName: String,
    skin: ShareCardSkin,
) {
    val row: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(caseIcon, style = MaterialTheme.typography.headlineSmall)
            Text(
                text = if (skin == ShareCardSkin.INTENSE) caseName.uppercase() else caseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (skin == ShareCardSkin.BRIGHT) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        ) {
            row()
        }
    } else {
        Box(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)) { row() }
    }
}

@Composable
private fun BoxScope.IntenseStampBadge(voice: Voice) {
    Box(
        modifier =
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 10.dp)
                .graphicsLayer(rotationZ = 9f)
                .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = voice.shareIntenseStampLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TopBeatContent(
    topBeat: ShareTopBeat,
    voice: Voice,
    skin: ShareCardSkin,
) {
    when (topBeat) {
        is ShareTopBeat.Summary -> SummaryBeat(topBeat, voice, skin)
    }
}

/**
 * The Square preset, top to bottom: the summary beat, Gaps always, then Duration / Intensity when
 * the Case tracks them (each already `null` otherwise) or the Rhythm grid when it tracks neither.
 * `squareInsights` in ShareCardState.kt decides which of these are present; this only lays them out.
 */
@Composable
private fun SquareInsightsBody(
    data: ShareCardData.Insights,
    voice: Voice,
    skin: ShareCardSkin,
) {
    TopBeatContent(data.topBeat, voice, skin)
    data.gaps?.let { GapsPanel(it, data.quietForDays, voice, skin) }
    data.duration?.let { DurationPanel(it, voice, skin) }
    data.intensity?.let { IntensityPanel(it, voice, skin) }
    data.rhythm?.let { MiniRhythmSection(it, voice, skin) }
}

/**
 * Story: the same summary beat, then the sections the user picked in the picker's order — Gaps,
 * Length, Start times, Intensity, Trends, Tags — each already `null` or empty in [data] when not
 * picked or not applicable. The panels are the Square ones, so both formats read alike.
 */
@Composable
private fun StoryInsightsBody(
    data: ShareCardData.Insights,
    voice: Voice,
    skin: ShareCardSkin,
) {
    TopBeatContent(data.topBeat, voice, skin)
    data.gaps?.let { GapsPanel(it, data.quietForDays, voice, skin) }
    data.duration?.let { DurationPanel(it, voice, skin) }
    data.rhythm?.let { MiniRhythmSection(it, voice, skin) }
    data.intensity?.let { IntensityPanel(it, voice, skin) }
    data.trends.takeIf { it.isNotEmpty() }?.let { MiniTrendsSection(it, voice, skin) }
    data.tags.takeIf { it.isNotEmpty() }?.let { MiniTagsSection(it, voice, skin) }
}

/**
 * Observed span and event count on top, then the headline figure. With a rate the figure is the
 * rate (and a trend pill when it moved); without one it is the event count itself. A [FlowRow]
 * lets the pill drop to its own line when the card is narrow or the text is large, rather than
 * squeezing or wrapping the figure.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryBeat(
    beat: ShareTopBeat.Summary,
    voice: Voice,
    skin: ShareCardSkin,
) {
    val rate = beat.rate

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        BeatKicker(
            text =
                if (rate != null) {
                    voice.shareSquareObservedLine(beat.observedDays, beat.eventCount)
                } else {
                    voice.shareSquareObservedDays(beat.observedDays)
                },
            skin = skin,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (rate != null) {
                SummaryFigure(
                    figure = rate.figureText(voice),
                    unit = rate.unitText(voice),
                    figureFontSize = SUMMARY_FIGURE_FONT_SIZE,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                rate.comparison?.let { comparison ->
                    TrendPill(comparison, voice, Modifier.align(Alignment.CenterVertically))
                }
            } else {
                SummaryFigure(
                    figure = beat.eventCount.toString(),
                    unit = " " + voice.shareSquareEventNoun(beat.eventCount),
                    figureFontSize = SUMMARY_COUNT_FONT_SIZE,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
    }
}

private fun HeroRate.figureText(voice: Voice): String = if (belowOnePerMonth) voice.shareRateBelowOneMarker else formatCompactDecimal(value)

private fun HeroRate.unitText(voice: Voice): String =
    when (unit) {
        RateUnit.DAY -> voice.shareRatePerDayUnit
        RateUnit.WEEK -> voice.shareRatePerWeekUnit
        RateUnit.MONTH -> voice.shareRatePerMonthUnit
    }

/** A big figure with its small unit hanging off the end, e.g. "2.1" + "/week". */
@Composable
private fun SummaryFigure(
    figure: String,
    unit: String,
    figureFontSize: Int,
    modifier: Modifier = Modifier,
) {
    val figureStyle = SpanStyle(fontSize = figureFontSize.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    val unitStyle =
        SpanStyle(
            fontSize = SUMMARY_UNIT_FONT_SIZE.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    Text(
        text =
            buildAnnotatedString {
                withStyle(figureStyle) { append(figure) }
                withStyle(unitStyle) { append(unit) }
            },
        style = MaterialTheme.typography.displaySmall,
        modifier = modifier,
    )
}

/** Which way the headline rate moved against the window before it, in the voice's own words; the triangle is drawn, not a font glyph, so every theme's font shows the same shape. */
@Composable
private fun TrendPill(
    comparison: HeroRateComparison,
    voice: Voice,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Row(
        modifier =
            modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        TrendTriangle(comparison.direction, contentColor)
        Text(
            text =
                if (comparison.direction == TrendDirection.FLAT) {
                    voice.shareSquareTrendSame
                } else {
                    voice.shareSquareTrendFrom(formatCompactDecimal(comparison.priorValue))
                },
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
        )
    }
}

/** One triangle shape, rotated per direction: up, down, or pointing right for "no change". */
@Composable
private fun TrendTriangle(
    direction: TrendDirection,
    color: Color,
) {
    val degrees =
        when (direction) {
            TrendDirection.UP -> 0f
            TrendDirection.DOWN -> TREND_TRIANGLE_DOWN_DEGREES
            TrendDirection.FLAT -> TREND_TRIANGLE_FLAT_DEGREES
        }

    Canvas(modifier = Modifier.size(TREND_TRIANGLE_SIZE.dp)) {
        val triangle =
            Path().apply {
                moveTo(size.width / 2f, size.height * 0.15f)
                lineTo(size.width * 0.9f, size.height * 0.85f)
                lineTo(size.width * 0.1f, size.height * 0.85f)
                close()
            }
        rotate(degrees) { drawPath(triangle, color) }
    }
}

@Composable
private fun GapsPanel(
    display: GapsDisplay,
    quietForDays: Long?,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        PanelHeaderRow {
            MiniSectionTitle(voice.shareGapsTitle, skin)
            quietForDays?.let { QuietLabel(voice.shareSquareQuietLabel(formatDaysCompact(it.toDouble()))) }
        }
        val shortest = display.shortestGapDays
        if (shortest == null) {
            Text(
                text = voice.shareSquareGapsNeedMoreEvents,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MinAvgMaxRow(
                voice = voice,
                min = formatDaysCompact(shortest.toDouble()),
                avg = formatDaysCompact(display.averageGapDays),
                max = formatDaysCompact(display.longestGapDays.toDouble()),
            )
        }
    }
}

/** A panel's title on the left and an optional note on the right. */
@Composable
private fun PanelHeaderRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A dashed pill: the Gaps panel's note that the Case has gone quiet (a live signal, so it is drawn lighter than a stat). */
@Composable
private fun QuietLabel(text: String) {
    val color = MaterialTheme.colorScheme.primary

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier =
            Modifier
                .drawBehind {
                    val dashes = floatArrayOf(QUIET_LABEL_DASH_ON.dp.toPx(), QUIET_LABEL_DASH_OFF.dp.toPx())
                    drawRoundRect(
                        color = color,
                        cornerRadius = CornerRadius(size.height / 2f),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(dashes)),
                    )
                }.padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun DurationPanel(
    display: DurationDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.shareDurationTitle, skin)
        MinAvgMaxRow(
            voice = voice,
            min = formatMinutesDuration(display.shortestMinutes),
            avg = formatMinutesDuration(display.averageMinutes.roundToInt().toLong()),
            max = formatMinutesDuration(display.longestMinutes),
        )
    }
}

@Composable
private fun IntensityPanel(
    display: IntensityDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        PanelHeaderRow {
            MiniSectionTitle(voice.insightsSectionLabelIntensity, skin)
            Text(
                text = voice.shareSquareIntensityAverage(formatIntensity(display.averageIntensity)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IntensityDistributionRow(display)
    }
}

/** Three equal columns, label over value: the shortest, average and longest of whatever the panel measures. */
@Composable
private fun MinAvgMaxRow(
    voice: Voice,
    min: String,
    avg: String,
    max: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(voice.shareStatMinLabel to min, voice.shareStatAvgLabel to avg, voice.shareStatMaxLabel to max).forEach { (label, value) ->
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BeatKicker(
    text: String,
    skin: ShareCardSkin,
) {
    Text(
        text = if (skin == ShareCardSkin.INTENSE) text.uppercase() else text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
    )
}

/** Mini-scale counterpart of `InsightsTab.kt`'s `InsightsCard` — same chrome, smaller padding/spacing for the card's fixed width. */
@Composable
private fun MiniInsightsCard(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        content()
    }
}

@Composable
private fun MiniSectionTitle(
    text: String,
    skin: ShareCardSkin,
) {
    Text(
        text = if (skin == ShareCardSkin.INTENSE) text.uppercase() else text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun MiniStatRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MiniRhythmSection(
    display: RhythmDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    val locale = LocalLocale.current.platformLocale

    MiniInsightsCard {
        MiniSectionTitle(voice.insightsSectionLabelRhythmStarts, skin)
        Row {
            Spacer(modifier = Modifier.width((MINI_RHYTHM_LABEL_WIDTH + MINI_RHYTHM_LABEL_GAP).dp))
            DayOfWeek.entries.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.width(MINI_RHYTHM_CELL_SIZE.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TimeOfDay.entries.forEach { timeOfDay ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeOfDayLabel(timeOfDay, voice),
                    modifier = Modifier.width((MINI_RHYTHM_LABEL_WIDTH + MINI_RHYTHM_LABEL_GAP).dp).padding(end = MINI_RHYTHM_LABEL_GAP.dp),
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
                                .size(MINI_RHYTHM_CELL_SIZE.dp)
                                .padding(1.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(level.toCellColor(tierCount = RHYTHM_TIER_COUNT)),
                    )
                }
            }
        }
    }
}

private fun timeOfDayLabel(
    timeOfDay: TimeOfDay,
    voice: Voice,
): String =
    when (timeOfDay) {
        TimeOfDay.MORNING -> voice.insightsTimeOfDayMorning
        TimeOfDay.AFTERNOON -> voice.insightsTimeOfDayAfternoon
        TimeOfDay.EVENING -> voice.insightsTimeOfDayEvening
        TimeOfDay.NIGHT -> voice.insightsTimeOfDayNight
    }

/**
 * Findings rendered as sentence text only — no reliability tag, no evidence line, unlike the
 * Insights tab's own [com.secondmonday.hodith.ui.casedetail.TrendFindingRow]/`TrendFindingPlank` —
 * a share card has no room for tap-revealed detail (spec §13). [findings] arrives already capped
 * by [com.secondmonday.hodith.ui.casedetail.trendsVisibleFindings].
 */
@Composable
private fun MiniTrendsSection(
    findings: List<TrendFinding>,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.insightsSectionLabelTrends, skin)
        findings.forEach { finding ->
            Text(
                text = trendFindingSentence(finding, voice),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** The Case's busiest tags, one row each: the tag's name and how many events carry it. [tags] arrives already capped and sorted busiest first. */
@Composable
private fun MiniTagsSection(
    tags: List<TagBreakdownEntry>,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.shareTopTagsTitle, skin)
        tags.forEach { tag -> MiniStatRow(tag.tagName, tag.count.toString()) }
    }
}

/** The 1..5 intensity squares, each shaded by how many events landed on that score. */
@Composable
private fun IntensityDistributionRow(display: IntensityDisplay) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (INTENSITY_MIN..INTENSITY_MAX).forEach { value ->
            val count = display.distribution[value] ?: 0
            val level = heatmapLevelFor(count, display.maxCount)
            Box(
                modifier =
                    Modifier
                        .size(INTENSITY_CELL_SIZE.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(level.toCellColor()),
                contentAlignment = Alignment.Center,
            ) {
                Text(value.toString(), style = MaterialTheme.typography.labelSmall, color = level.toTextColor())
            }
        }
    }
}

@Composable
private fun ShareCardFooter(
    generatedAtMillis: Long,
    voice: Voice,
    skin: ShareCardSkin,
) {
    if (skin == ShareCardSkin.INTENSE) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
    Text(
        text = voice.shareCardFooter(formatCardTimestamp(generatedAtMillis, LocalTimeFormat.current.is24Hour)),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
    )
}

// ---- Previews (design validation, not shipped UI) ----

private fun previewData(format: ShareCardFormat): ShareCardData =
    ShareCardData.Insights(
        format = format,
        caseIcon = "☕",
        caseName = "Perfect coffee",
        topBeat = ShareTopBeat.Summary(eventCount = 14, observedDays = 60, rate = null),
        rhythm =
            RhythmDisplay(
                cells =
                    DayOfWeek.entries.flatMap { day ->
                        TimeOfDay.entries.map { tod -> RhythmCellDisplay(day, tod, HeatmapLevel.entries.random(), count = 0) }
                    },
                plottedByStart = false,
            ),
        gaps = null,
        trends =
            listOf(
                TrendFinding(
                    kind = TrendFindingKind.FREQUENCY_SHIFT,
                    direction = ShiftDirection.UP,
                    reliability = TrendReliability.HINT,
                    sampleCount = 13,
                    priorValue = 5.0,
                    recentValue = 8.0,
                ),
            ),
        duration = null,
        intensity = null,
        tags = listOf(TagBreakdownEntry("espresso", 6), TagBreakdownEntry("oat milk", 4), TagBreakdownEntry("office", 3)),
        generatedAtMillis = System.currentTimeMillis(),
    )

/** The Square preset for a Case that tracks intensity and duration, mid-trend and gone quiet, so every panel and the pill show. */
private fun previewSquareData(): ShareCardData =
    ShareCardData.Insights(
        format = ShareCardFormat.SQUARE,
        caseIcon = "🤕",
        caseName = "Headaches",
        topBeat =
            ShareTopBeat.Summary(
                eventCount = 31,
                observedDays = 94,
                rate =
                    HeroRate(
                        value = 2.1,
                        unit = RateUnit.WEEK,
                        belowOnePerMonth = false,
                        comparison = HeroRateComparison(direction = TrendDirection.UP, priorValue = 1.4),
                    ),
            ),
        rhythm = null,
        gaps =
            GapsDisplay(
                longestGapDays = 9,
                currentGapDays = 2,
                averageGapDays = 3.1,
                isBursty = false,
                longestStreakDays = 0,
                averageStreakDays = 0.0,
                shortestGapDays = 1,
            ),
        trends = emptyList(),
        duration = DurationDisplay(averageMinutes = 130.0, longestMinutes = 400, totalMinutes = 4030, shortestMinutes = 25),
        intensity = IntensityDisplay(averageIntensity = 3.4, distribution = mapOf(1 to 2, 2 to 6, 3 to 11, 4 to 9, 5 to 3), maxCount = 11),
        quietForDays = 14,
        generatedAtMillis = System.currentTimeMillis(),
    )

@Preview(name = "Plain - Story", showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun ShareCardTemplatePlainPreview() {
    HodithTheme(theme = AppTheme.PLAIN) {
        ShareCardTemplate(previewData(ShareCardFormat.STORY), PlainVoice)
    }
}

/** The fixed Square preset in Plain — the Case-settings-driven card, not Story's picked sections. */
@Preview(name = "Plain - Square", showBackground = true, widthDp = 400, heightDp = 640)
@Composable
private fun ShareCardTemplatePlainSquarePreview() {
    HodithTheme(theme = AppTheme.PLAIN) {
        ShareCardTemplate(previewSquareData(), PlainVoice)
    }
}

@Preview(name = "Intense - Square", showBackground = true, widthDp = 400, heightDp = 640)
@Composable
private fun ShareCardTemplateIntenseSquarePreview() {
    CompositionLocalProvider(LocalShareCardSkin provides ShareCardSkin.INTENSE) {
        HodithTheme(theme = AppTheme.INTENSE) {
            ShareCardTemplate(previewSquareData(), IntenseVoice)
        }
    }
}

@Preview(name = "Intense", showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun ShareCardTemplateIntensePreview() {
    CompositionLocalProvider(LocalShareCardSkin provides ShareCardSkin.INTENSE) {
        HodithTheme(theme = AppTheme.INTENSE) {
            ShareCardTemplate(previewData(ShareCardFormat.STORY), IntenseVoice)
        }
    }
}

@Preview(name = "Bright", showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun ShareCardTemplateBrightPreview() {
    CompositionLocalProvider(LocalShareCardSkin provides ShareCardSkin.BRIGHT) {
        HodithTheme(theme = AppTheme.BRIGHT) {
            ShareCardTemplate(previewData(ShareCardFormat.STORY), BrightVoice)
        }
    }
}

@Preview(name = "Bright - Square", showBackground = true, widthDp = 400, heightDp = 640)
@Composable
private fun ShareCardTemplateBrightSquarePreview() {
    CompositionLocalProvider(LocalShareCardSkin provides ShareCardSkin.BRIGHT) {
        HodithTheme(theme = AppTheme.BRIGHT) {
            ShareCardTemplate(previewSquareData(), BrightVoice)
        }
    }
}
