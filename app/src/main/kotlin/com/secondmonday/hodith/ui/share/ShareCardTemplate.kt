package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HeatmapLevel
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.domain.INTENSITY_MIN
import com.secondmonday.hodith.domain.RHYTHM_TIER_COUNT
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TimeOfDay
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import com.secondmonday.hodith.domain.heatmapLevelFor
import com.secondmonday.hodith.ui.casedetail.formatDays
import com.secondmonday.hodith.ui.casedetail.formatIntensity
import com.secondmonday.hodith.ui.casedetail.trendFindingSentence
import com.secondmonday.hodith.ui.common.toCellColor
import com.secondmonday.hodith.ui.common.toTextColor
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalShareCardSkin
import com.secondmonday.hodith.ui.theme.ShareCardSkin
import com.secondmonday.hodith.ui.voice.BrightVoice
import com.secondmonday.hodith.ui.voice.IntenseVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.DurationDisplay
import com.secondmonday.hodith.viewmodel.FrequencyBar
import com.secondmonday.hodith.viewmodel.FrequencyDisplay
import com.secondmonday.hodith.viewmodel.GapsDisplay
import com.secondmonday.hodith.viewmodel.IntensityDisplay
import com.secondmonday.hodith.viewmodel.LogCardRow
import com.secondmonday.hodith.viewmodel.RhythmCellDisplay
import com.secondmonday.hodith.viewmodel.RhythmDisplay
import com.secondmonday.hodith.viewmodel.ShareCardData
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareTopBeat
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatMinutesDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** Matches the render-pipeline spike's fixed capture width — see PROGRESS.md's Phase 10 share-cards width decision. */
private val SHARE_CARD_WIDTH = 360.dp

/** Square's 1:1 floor at [SHARE_CARD_WIDTH] — kept because chat/feed shares render whatever aspect ratio they're given, unlike Story's destination apps. */
private val SQUARE_MIN_HEIGHT = SHARE_CARD_WIDTH
private const val MINI_RHYTHM_CELL_SIZE = 16

/** Wide enough for "Afternoon" — the longest time-of-day label — to fit on one line in every theme's display font, Baloo2 Bold (Bright) included. */
private const val MINI_RHYTHM_LABEL_WIDTH = 88
private const val MINI_FREQUENCY_CHART_HEIGHT = 40
private const val MINI_FREQUENCY_BAR_MAX_HEIGHT_FRACTION = 0.75f
private const val MINI_FREQUENCY_MIN_BAR_HEIGHT_FRACTION = 0.04f

/**
 * Spec §13's share card — one Compose tree reused for both the preview screen and the actual
 * export capture (`ComposeShareImageExporter`). Sections are faithful mini-copies of the real
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

    Column(
        modifier =
            modifier
                .width(SHARE_CARD_WIDTH)
                .then(if (data.format == ShareCardFormat.SQUARE) Modifier.heightIn(min = SQUARE_MIN_HEIGHT) else Modifier)
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
    TopBeatContent(data.topBeat, voice)
    data.frequency?.let { MiniFrequencySection(it, voice, skin) }
    data.rhythm?.let { MiniRhythmSection(it, voice, skin) }
    data.gaps?.let { MiniGapsSection(it, voice, skin) }
    data.trends.takeIf { it.isNotEmpty() }?.let { MiniTrendsSection(it, voice, skin) }
    data.duration?.let { MiniDurationSection(it, voice, skin) }
    data.intensity?.let { MiniIntensitySection(it, voice, skin) }
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
) {
    when (topBeat) {
        is ShareTopBeat.Reality -> RealityBeat(topBeat, voice)
    }
}

@Composable
private fun RealityBeat(
    beat: ShareTopBeat.Reality,
    voice: Voice,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(beat.eventCount.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                voice.shareRealityEventsLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(beat.observedDays.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                voice.shareRealityDaysObservedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                .padding(horizontal = 10.dp, vertical = 9.dp),
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
private fun MiniFrequencySection(
    display: FrequencyDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.shareFrequencyTitle(display.granularity), skin)
        Row(modifier = Modifier.fillMaxWidth().height(MINI_FREQUENCY_CHART_HEIGHT.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            display.bars.forEach { bar ->
                val barHeight =
                    MINI_FREQUENCY_CHART_HEIGHT.dp *
                        bar.heightFraction.coerceAtLeast(MINI_FREQUENCY_MIN_BAR_HEIGHT_FRACTION) *
                        MINI_FREQUENCY_BAR_MAX_HEIGHT_FRACTION
                Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (bar.count > 0) {
                            Text(bar.count.toString(), style = MaterialTheme.typography.labelSmall)
                        }
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(barHeight)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }
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
        MiniSectionTitle(
            if (display.plottedByStart) voice.insightsSectionLabelRhythmStarts else voice.insightsSectionLabelRhythm,
            skin,
        )
        Row {
            Spacer(modifier = Modifier.width(MINI_RHYTHM_LABEL_WIDTH.dp))
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
                    modifier = Modifier.width(MINI_RHYTHM_LABEL_WIDTH.dp),
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

@Composable
private fun MiniGapsSection(
    display: GapsDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.insightsSectionLabelGaps, skin)
        MiniStatRow(voice.insightsGapsLongestLabel, formatDays(display.longestGapDays.toDouble()))
        MiniStatRow(voice.insightsGapsCurrentLabel, formatDays(display.currentGapDays.toDouble()))
        MiniStatRow(voice.insightsGapsAverageLabel, formatDays(display.averageGapDays))
    }
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

@Composable
private fun MiniDurationSection(
    display: DurationDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.insightsSectionLabelDuration, skin)
        MiniStatRow(voice.insightsDurationAverageLabel, formatMinutesDuration(display.averageMinutes.roundToInt().toLong()))
        MiniStatRow(voice.insightsDurationLongestLabel, formatMinutesDuration(display.longestMinutes))
        MiniStatRow(voice.insightsDurationTotalLabel, formatMinutesDuration(display.totalMinutes))
    }
}

@Composable
private fun MiniIntensitySection(
    display: IntensityDisplay,
    voice: Voice,
    skin: ShareCardSkin,
) {
    MiniInsightsCard {
        MiniSectionTitle(voice.insightsSectionLabelIntensity, skin)
        MiniStatRow(voice.insightsIntensityAverageLabel, formatIntensity(display.averageIntensity))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (INTENSITY_MIN..INTENSITY_MAX).forEach { value ->
                val count = display.distribution[value] ?: 0
                val level = heatmapLevelFor(count, display.maxCount)
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(level.toCellColor()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(value.toString(), style = MaterialTheme.typography.labelSmall, color = level.toTextColor())
                }
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
        text = voice.shareCardFooter(formatEventDate(generatedAtMillis)),
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
        topBeat = ShareTopBeat.Reality(eventCount = 14, observedDays = 60),
        frequency =
            FrequencyDisplay(
                granularity = FrequencyGranularity.WEEK,
                bars = listOf(3, 5, 2, 7, 4, 9).map { FrequencyBar(LocalDate.now(), it, it / 9f) },
            ),
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
        generatedAtMillis = System.currentTimeMillis(),
    )

@Preview(name = "Plain - Story", showBackground = true, widthDp = 400, heightDp = 700)
@Composable
private fun ShareCardTemplatePlainPreview() {
    HodithTheme(theme = AppTheme.PLAIN) {
        ShareCardTemplate(previewData(ShareCardFormat.STORY), PlainVoice)
    }
}

/** Same skin/content as [ShareCardTemplatePlainPreview], Square format — compare the two side by side to see the shape difference. */
@Preview(name = "Plain - Square", showBackground = true, widthDp = 400, heightDp = 500)
@Composable
private fun ShareCardTemplatePlainSquarePreview() {
    HodithTheme(theme = AppTheme.PLAIN) {
        ShareCardTemplate(previewData(ShareCardFormat.SQUARE), PlainVoice)
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
            ShareCardTemplate(previewData(ShareCardFormat.SQUARE), BrightVoice)
        }
    }
}
