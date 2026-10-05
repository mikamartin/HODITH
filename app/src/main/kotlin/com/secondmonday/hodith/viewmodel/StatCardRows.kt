package com.secondmonday.hodith.viewmodel

import kotlin.math.roundToInt

/** A measure's shortest, average and longest, each already formatted for a card. */
data class MinAvgMaxValues(
    val min: String,
    val avg: String,
    val max: String,
)

/**
 * The Gaps card's figures. Shared by the Insights tab's Gaps card and the Share card's Gaps panel,
 * so both surfaces format the same numbers the same way.
 */
data class GapsStatRows(
    /** `null` until there are two events, the first point a gap exists. */
    val minAvgMax: MinAvgMaxValues?,
    val currentGap: String,
    val longestStreak: String,
    val averageStreak: String,
    val isBursty: Boolean,
)

internal fun gapsStatRows(display: GapsDisplay): GapsStatRows =
    GapsStatRows(
        minAvgMax =
            display.shortestGapDays?.let { shortest ->
                MinAvgMaxValues(
                    min = formatDaysCompact(shortest.toDouble()),
                    avg = formatDaysCompact(display.averageGapDays),
                    max = formatDaysCompact(display.longestGapDays.toDouble()),
                )
            },
        currentGap = formatDaysCompact(display.currentGapDays.toDouble()),
        longestStreak = formatDaysCompact(display.longestStreakDays.toDouble()),
        averageStreak = formatDaysCompact(display.averageStreakDays),
        isBursty = display.isBursty,
    )

/** The Duration figures: shortest, average (rounded to whole minutes) and longest. Shared by the Insights Duration card and the Share Duration panel. */
internal fun durationMinAvgMax(display: DurationDisplay): MinAvgMaxValues =
    MinAvgMaxValues(
        min = formatMinutesDuration(display.shortestMinutes),
        avg = formatMinutesDuration(display.averageMinutes.roundToInt().toLong()),
        max = formatMinutesDuration(display.longestMinutes),
    )
