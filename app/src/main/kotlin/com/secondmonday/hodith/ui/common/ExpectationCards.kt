package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.domain.Expectation
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_DAYS
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_EVENTS
import com.secondmonday.hodith.domain.VerdictResult
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.formatExpectedFrequency
import com.secondmonday.hodith.viewmodel.formatRate

/**
 * Shared shell for every expectation card — full-width [Card] with a padded, vertically-spaced
 * [Column]. Kept from the former Hunch tab for N2's Notifications to reuse.
 */
@Composable
internal fun ExpectationCard(
    spacing: Dp = 8.dp,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}

/** An [Expectation] with too little observation yet for a verdict — a progress bar toward the preliminary bar. */
@Composable
internal fun ExpectationEarlyCard(
    expectation: Expectation,
    result: VerdictResult,
    voice: Voice,
) {
    val daysActive = expectation.metric == VerdictMetric.DAYS_ACTIVE
    val observationCount = if (daysActive) result.activeDayCount else result.eventCount
    val progressUnit = if (daysActive) voice.expectationProgressUnitDaysActive else voice.expectationProgressUnitEvents
    ExpectationCard {
        Text(voice.expectationEarlyBadgeLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            voice.expectationChipLabel(formatExpectedFrequency(expectation.count, expectation.per, expectation.metric)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(voice.expectationEarlyHeadline, style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { expectationProgressFraction(observationCount, result.windowDays) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            voice.expectationProgressLabel(observationCount, progressUnit, result.windowDays),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** An [Expectation] with a preliminary or confident verdict — the comparison against reality. */
@Composable
internal fun ExpectationVerdictCard(
    expectation: Expectation,
    result: VerdictResult,
    voice: Voice,
) {
    // Guaranteed non-null: only reached once computeVerdict yields a comparisonBand.
    val band = checkNotNull(result.comparisonBand) { "Verdict state must carry a resolved comparison band" }
    val daysActive = expectation.metric == VerdictMetric.DAYS_ACTIVE
    val observedRateLabel = formatRate(result.observedRate, expectation.per, expectation.metric)

    ExpectationCard {
        Text(
            voice.expectationTierBadgeLabel(result.tier),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            voice.expectationChipLabel(formatExpectedFrequency(expectation.count, expectation.per, expectation.metric)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            if (daysActive) voice.verdictHeadlineDaysActive(band, observedRateLabel) else voice.verdictHeadline(band, observedRateLabel),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (daysActive) {
                voice.verdictMetaDaysActive(result.tier, result.activeDayCount, result.windowDays)
            } else {
                voice.verdictMeta(result.tier, result.eventCount, result.windowDays)
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * How far toward the Preliminary bar an early-days [Expectation]'s progress bar sits — whichever
 * of the observation-count or window-length requirement is furthest behind, since both must clear
 * together (spec §8). [observationCount] is the event count for an occurrence-count expectation
 * and the active-day count for a days-active one.
 */
internal fun expectationProgressFraction(
    observationCount: Int,
    windowDays: Long,
): Float {
    val countFraction = observationCount.toFloat() / PRELIMINARY_MIN_EVENTS
    val dayFraction = windowDays.toFloat() / PRELIMINARY_MIN_DAYS
    return minOf(countFraction, dayFraction).coerceIn(0f, 1f)
}
