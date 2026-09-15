package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.domain.FrequencyGranularity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The single place an event's stored epoch-millis becomes the strings the UI shows — every
 * clock time, span date and frequency-axis label in the app. Consolidated here (spec §6/§14,
 * PROGRESS.md "Satellite · 12h/24h time format") from four separate `DateTimeFormatter.ofPattern`
 * copies that had drifted apart across [CaseDetailViewModel], `BigPictureGrid` and `InsightsTab`.
 *
 * Pure and Compose-free so it stays JVM-unit-testable, the same rationale as [homeCaseRows] and
 * [formatMinutesDuration]. Compose passes `use24Hour` down from `LocalTimeFormat`; anything
 * genuinely inside a ViewModel would read it from an injected `SettingsRepository` instead.
 *
 * Dates are fixed `Locale.US` (the app's display locale) except [formatFrequencyTickLabel], whose
 * chart-axis labels follow the platform locale as they did before.
 *
 * Note: `h:mm a` renders a plain ASCII space before AM/PM, whereas the JDK's localized SHORT time
 * uses a narrow no-break space — tests that assert on the 12-hour output match on substrings.
 */

private val EVENT_TIME_NO_YEAR_12H = DateTimeFormatter.ofPattern("MMM d, h:mm a, EEE", Locale.US)
private val EVENT_TIME_NO_YEAR_24H = DateTimeFormatter.ofPattern("MMM d, HH:mm, EEE", Locale.US)
private val EVENT_TIME_WITH_YEAR_12H = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a, EEE", Locale.US)
private val EVENT_TIME_WITH_YEAR_24H = DateTimeFormatter.ofPattern("MMM d, yyyy, HH:mm, EEE", Locale.US)
private val TIME_ONLY_12H = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val TIME_ONLY_24H = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
private val SPAN_DATE_TIME_12H = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
private val SPAN_DATE_TIME_24H = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)
private val SPAN_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val WEEKDAY_DAY_FORMATTER = DateTimeFormatter.ofPattern("EEE d", Locale.US)
private val MEDIUM_DATE_FORMATTER = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)

/**
 * The event-list row: weekday, date and time. The year shows only when [occurredAt] falls in a
 * different calendar year than [now] — most logged events are recent and read better without the
 * year's visual noise, while older records still need it to stay unambiguous. [zone] defaults to
 * the device zone but is overridable so tests don't depend on the machine running them.
 */
internal fun formatEventTime(
    occurredAt: Long,
    now: Long,
    use24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val eventZoned = Instant.ofEpochMilli(occurredAt).atZone(zone)
    val nowZoned = Instant.ofEpochMilli(now).atZone(zone)
    val sameYear = eventZoned.year == nowZoned.year
    val formatter =
        when {
            sameYear && use24Hour -> EVENT_TIME_NO_YEAR_24H
            sameYear -> EVENT_TIME_NO_YEAR_12H
            use24Hour -> EVENT_TIME_WITH_YEAR_24H
            else -> EVENT_TIME_WITH_YEAR_12H
        }
    return eventZoned.format(formatter)
}

/** Date-only counterpart of [formatEventTime], for the log sheet's separate date/time buttons. */
internal fun formatEventDate(
    occurredAt: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(occurredAt).atZone(zone).format(MEDIUM_DATE_FORMATTER)

/** Time-only counterpart of [formatEventTime], for the log sheet's separate date/time buttons. */
internal fun formatEventTimeOfDay(
    occurredAt: Long,
    use24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val time = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
    return time.format(if (use24Hour) TIME_ONLY_24H else TIME_ONLY_12H)
}

/** Wall-clock time of a [LocalTime] already resolved to a zone — the Big Picture event-detail row. */
internal fun formatClockTime(
    time: LocalTime,
    use24Hour: Boolean,
): String = time.format(if (use24Hour) TIME_ONLY_24H else TIME_ONLY_12H)

/** "MMM d" — a span endpoint on the Big Picture grid. */
internal fun formatSpanDate(date: LocalDate): String = date.format(SPAN_DATE_FORMATTER)

/**
 * "MMM d, h:mm a" — a Big Picture ongoing/span endpoint that fell on a different day than the row
 * it's shown on, so the day alone would drop the actual start time and the time alone would read
 * as belonging to the row's day. No year (the dialog is always within recent history).
 */
internal fun formatSpanDateTime(
    occurredAt: Long,
    use24Hour: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String = Instant.ofEpochMilli(occurredAt).atZone(zone).format(if (use24Hour) SPAN_DATE_TIME_24H else SPAN_DATE_TIME_12H)

/** Localized medium date — the Big Picture day/week detail dialog titles. */
internal fun formatMediumDate(date: LocalDate): String = date.format(MEDIUM_DATE_FORMATTER)

/** "EEE d" — the per-day header inside the Big Picture week detail dialog. */
internal fun formatWeekdayDayDate(date: LocalDate): String = date.format(WEEKDAY_DAY_FORMATTER)

/**
 * Frequency-chart tick label — deliberately numeric/short rather than authored copy (spec S9: the
 * old "Week of …" wrapper text crowded the chart once more than two ticks were shown), so unlike
 * every other formatter in this file it needs no `Voice` input. Follows the platform [locale] (the
 * chart's Day/Week/Month scale is chrome, not an event's own recorded time), same as before.
 */
internal fun formatFrequencyTickLabel(
    periodStart: LocalDate,
    granularity: FrequencyGranularity,
    locale: Locale,
): String =
    when (granularity) {
        FrequencyGranularity.DAY -> periodStart.format(DateTimeFormatter.ofPattern("d", locale))
        FrequencyGranularity.WEEK -> periodStart.format(DateTimeFormatter.ofPattern("M/dd", locale))
        FrequencyGranularity.MONTH -> periodStart.month.getDisplayName(TextStyle.SHORT, locale)
    }

/**
 * [tickCount] indices spread evenly across [barCount] bars, each centered in its own equal-width
 * slice — neither bar 0 nor the last bar is pinned. A stride walk anchored to one end only reaches
 * the other by coincidence, which is what crowded two ticks together before (spec S9); centering
 * each tick in its slice spreads any rounding remainder across every gap instead of concentrating
 * it at one edge. Whether an end bar ends up tagged is itself just a coincidence of [tickCount]'s
 * parity, not a guarantee: a tick count equal to [barCount] always covers both trivially, but
 * 12 bars / 6 ticks lands on bar 11 and not bar 0, and 12 bars / 4 ticks lands on neither.
 */
internal fun frequencyTickIndices(
    barCount: Int,
    tickCount: Int,
): List<Int> {
    val width = barCount.toDouble() / tickCount
    return (0 until tickCount).map { k -> ((k + 0.5) * width - 0.5).roundToInt() }.distinct()
}

// How many of the 12 bars carry a tick label, per granularity (spec S9). Day's numeric label is
// narrow enough to show on every bar; Week's "M/dd" and Month's short name need more room.
internal const val FREQUENCY_TICK_COUNT_DAY = 12
internal const val FREQUENCY_TICK_COUNT_WEEK = 6
internal const val FREQUENCY_TICK_COUNT_MONTH = 6

internal fun frequencyTickCount(granularity: FrequencyGranularity): Int =
    when (granularity) {
        FrequencyGranularity.DAY -> FREQUENCY_TICK_COUNT_DAY
        FrequencyGranularity.WEEK -> FREQUENCY_TICK_COUNT_WEEK
        FrequencyGranularity.MONTH -> FREQUENCY_TICK_COUNT_MONTH
    }
