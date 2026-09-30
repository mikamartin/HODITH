package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.ui.voice.Voice

internal val EXPECTED_COUNT_RANGE = 1..99
internal const val DEFAULT_EXPECTED_COUNT = 3

/** The period options offered for [metric] — days-active drops Day (nonsensical) and adds a quarter. */
internal fun periodOptionsFor(metric: VerdictMetric): List<ExpectedPer> =
    if (metric == VerdictMetric.DAYS_ACTIVE) {
        listOf(ExpectedPer.WEEK, ExpectedPer.MONTH, ExpectedPer.QUARTER)
    } else {
        listOf(ExpectedPer.DAY, ExpectedPer.WEEK, ExpectedPer.MONTH)
    }

/**
 * Keeps [current] if [metric] still offers it, otherwise the nearest period that survives the
 * metric flip: Day (occurrence-only) steps up to Week, a quarter (days-active-only) steps down to
 * Month.
 */
internal fun coerceExpectedPer(
    metric: VerdictMetric,
    current: ExpectedPer,
): ExpectedPer =
    when {
        current in periodOptionsFor(metric) -> current
        metric == VerdictMetric.DAYS_ACTIVE -> ExpectedPer.WEEK
        else -> ExpectedPer.MONTH
    }

internal fun expectedPerLabel(
    per: ExpectedPer,
    voice: Voice,
): String =
    when (per) {
        ExpectedPer.DAY -> voice.expectedPerDay
        ExpectedPer.WEEK -> voice.expectedPerWeek
        ExpectedPer.MONTH -> voice.expectedPerMonth
        ExpectedPer.QUARTER -> voice.expectedPerQuarter
    }

/** A `labelLarge` heading over its section content — shared structural shape for a picker sheet's sections. */
@Composable
internal fun LabelledSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

/**
 * Count + period picker: a [NumberStepper] for the count, a [SegmentedChoiceRow] for the period —
 * the pairing a stated expectation's frequency always needs. [metric] only decides which period
 * options and count suffix apply; it isn't rendered here.
 */
@Composable
internal fun FrequencyPicker(
    count: Int,
    onCountChange: (Int) -> Unit,
    per: ExpectedPer,
    onPerChange: (ExpectedPer) -> Unit,
    metric: VerdictMetric,
    voice: Voice,
) {
    NumberStepper(
        value = count,
        range = EXPECTED_COUNT_RANGE,
        suffix = if (metric == VerdictMetric.DAYS_ACTIVE) voice.frequencyCountSuffixDaysActive else voice.frequencyCountSuffix,
        decreaseDescription = voice.frequencyDecreaseCountDescription,
        increaseDescription = voice.frequencyIncreaseCountDescription,
        onChange = onCountChange,
    )
    SegmentedChoiceRow(
        options = periodOptionsFor(metric).map { it to expectedPerLabel(it, voice) },
        selected = per,
        onSelect = onPerChange,
    )
}
