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
 * Dates are fixed `Locale.US` (the app's display locale) except [formatFrequencyPeriodLabel],
 * whose chart-axis labels follow the platform locale as they did before.
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

/** Localized medium date — the Big Picture day/week detail dialog titles. */
internal fun formatMediumDate(date: LocalDate): String = date.format(MEDIUM_DATE_FORMATTER)

/** "EEE d" — the per-day header inside the Big Picture week detail dialog. */
internal fun formatWeekdayDayDate(date: LocalDate): String = date.format(WEEKDAY_DAY_FORMATTER)

/**
 * Frequency-chart axis label. Follows the platform [locale], unlike the fixed-US formatters above,
 * because the chart's Day/Week/Month scale is chrome rather than an event's own recorded time. The
 * weekly bucket's wrapper text comes from [weekOf] (a `Voice` key) so it isn't an inline UI string.
 */
internal fun formatFrequencyPeriodLabel(
    periodStart: LocalDate,
    granularity: FrequencyGranularity,
    locale: Locale,
    weekOf: (date: String) -> String,
): String =
    when (granularity) {
        FrequencyGranularity.DAY -> periodStart.format(DateTimeFormatter.ofPattern("MMM d", locale))
        FrequencyGranularity.WEEK -> weekOf(periodStart.format(DateTimeFormatter.ofPattern("MMM d", locale)))
        FrequencyGranularity.MONTH -> "${periodStart.month.getDisplayName(TextStyle.SHORT, locale)} ${periodStart.year}"
    }
