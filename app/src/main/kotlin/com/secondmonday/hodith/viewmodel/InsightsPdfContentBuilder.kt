package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.ui.common.shareMinAvgMaxLabels
import com.secondmonday.hodith.ui.voice.Voice

/**
 * Flattens [cardData]'s panels into plain text lines for the Insights PDF (feature/pdf-export) -
 * the same values the Insight share card already shows (spec §13), just as "Label: value" lines
 * instead of mini-card panels, so the PDF never states a different number than the card does.
 * Rhythm and Trends are left out: both read as a grid or a narrative finding on screen, not a
 * line of figures, so there's no plain-text form of either that wouldn't just restate the chart.
 */
internal fun insightsPdfStatLines(
    cardData: ShareCardData.Insights,
    voice: Voice,
): List<String> {
    val labels = voice.shareMinAvgMaxLabels()
    val lines = mutableListOf<String>()

    val beat = cardData.topBeat as ShareTopBeat.Summary
    lines +=
        if (beat.rate != null) {
            voice.shareSquareObservedLine(beat.observedDays, beat.eventCount)
        } else {
            voice.shareSquareObservedDays(beat.observedDays)
        }

    cardData.gaps?.let { gaps ->
        gapsStatRows(gaps).minAvgMax?.let { values ->
            lines += "${voice.shareGapsTitle}: ${labels.min} ${values.min}, ${labels.avg} ${values.avg}, ${labels.max} ${values.max}"
        }
    }
    cardData.streaks?.let { streaks ->
        lines +=
            "${voice.shareStreaksTitle}: ${voice.insightsStreakLongestLabel} ${formatDaysCompact(streaks.longestStreakDays.toDouble())}, " +
            "${voice.insightsStreakAverageLabel} ${formatDaysCompact(streaks.averageStreakDays)}"
    }
    cardData.duration?.let { duration ->
        val values = durationMinAvgMax(duration)
        lines += "${voice.shareDurationTitle}: ${labels.min} ${values.min}, ${labels.avg} ${values.avg}, ${labels.max} ${values.max}"
    }
    cardData.intensity?.let { intensity ->
        lines += "${voice.insightsSectionLabelIntensity}: ${voice.shareSquareIntensityAverage(formatIntensity(intensity.averageIntensity))}"
    }
    if (cardData.tags.isNotEmpty()) {
        lines += "${voice.shareTopTagsTitle}: " + cardData.tags.joinToString(", ") { "${it.tagName} (${it.count})" }
    }

    return lines
}
