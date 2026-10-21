package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.domain.FrequencyGranularity
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
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
 * Every date/time string here is fixed English, including [formatFrequencyTickLabel]'s chart-axis
 * month label and any weekday name — `kotlinx-datetime` ships no arbitrary-locale name tables (see
 * PROGRESS.md's `java.time` → `kotlinx-datetime` migration item), so this converges with the rest
 * of the app's already Locale.US-fixed display rather than keeping that one spot alone tracking the
 * platform locale.
 *
 * Note: `h:mm a` renders a plain ASCII space before AM/PM, whereas the JDK's localized SHORT time
 * used a narrow no-break space — tests that assert on the 12-hour output match on substrings.
 */

private fun Month.englishAbbreviated(): String = MonthNames.ENGLISH_ABBREVIATED.names[number - 1]

internal fun Month.englishFull(): String = MonthNames.ENGLISH_FULL.names[number - 1]

internal fun DayOfWeek.englishFull(): String = DayOfWeekNames.ENGLISH_FULL.names[isoDayNumber - 1]

internal fun DayOfWeek.englishNarrow(): String = DayOfWeekNames.ENGLISH_ABBREVIATED.names[isoDayNumber - 1].take(1)

private val EVENT_TIME_NO_YEAR_12H =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        amPmHour(Padding.NONE)
        char(':')
        minute()
        char(' ')
        amPmMarker("AM", "PM")
        chars(", ")
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    }
private val EVENT_TIME_NO_YEAR_24H =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        hour()
        char(':')
        minute()
        chars(", ")
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    }
private val EVENT_TIME_WITH_YEAR_12H =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        year()
        chars(", ")
        amPmHour(Padding.NONE)
        char(':')
        minute()
        char(' ')
        amPmMarker("AM", "PM")
        chars(", ")
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    }
private val EVENT_TIME_WITH_YEAR_24H =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        year()
        chars(", ")
        hour()
        char(':')
        minute()
        chars(", ")
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
    }
private val TIME_ONLY_12H =
    LocalTime.Format {
        amPmHour(Padding.NONE)
        char(':')
        minute()
        char(' ')
        amPmMarker("AM", "PM")
    }
private val TIME_ONLY_24H =
    LocalTime.Format {
        hour()
        char(':')
        minute()
    }
private val SPAN_DATE_TIME_12H =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        amPmHour(Padding.NONE)
        char(':')
        minute()
        char(' ')
        amPmMarker("AM", "PM")
    }
private val SPAN_DATE_TIME_24H =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        hour()
        char(':')
        minute()
    }
private val SPAN_DATE_FORMATTER =
    LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
    }
private val WEEKDAY_DAY_FORMATTER =
    LocalDate.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
    }

// US medium date style ("Mar 7, 2026") — identical pattern to the PDF cell's own date line, so one
// format serves both.
private val MEDIUM_DATE_FORMATTER =
    LocalDate.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        day(Padding.NONE)
        chars(", ")
        year()
    }
private val PDF_CELL_DATE_FORMATTER = MEDIUM_DATE_FORMATTER
private val PDF_CELL_WEEKDAY_TIME_12H =
    LocalDateTime.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        char(' ')
        amPmHour(Padding.NONE)
        char(':')
        minute()
        char(' ')
        amPmMarker("AM", "PM")
    }
private val PDF_CELL_WEEKDAY_TIME_24H =
    LocalDateTime.Format {
        dayOfWeek(DayOfWeekNames.ENGLISH_ABBREVIATED)
        char(' ')
        hour()
        char(':')
        minute()
    }

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
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val eventDateTime = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone)
    val nowDateTime = Instant.fromEpochMilliseconds(now).toLocalDateTime(zone)
    val sameYear = eventDateTime.year == nowDateTime.year
    val formatter =
        when {
            sameYear && use24Hour -> EVENT_TIME_NO_YEAR_24H
            sameYear -> EVENT_TIME_NO_YEAR_12H
            use24Hour -> EVENT_TIME_WITH_YEAR_24H
            else -> EVENT_TIME_WITH_YEAR_12H
        }
    return formatter.format(eventDateTime)
}

/**
 * The History PDF table's two-line Start/End cell text (feature/pdf-export): the date on one
 * line, the weekday and time on the next - e.g. "Oct 3, 2025" / "Fri 6:07 PM". Always carries the
 * year, same reasoning as [formatPdfDateRangeBound]. [use24Hour] still follows the Settings time
 * format choice, same as everywhere else a clock time is shown.
 */
internal fun formatHistoryPdfCellTimestamp(
    occurredAt: Long,
    use24Hour: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val dateTime = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone)
    val timeFormatter = if (use24Hour) PDF_CELL_WEEKDAY_TIME_24H else PDF_CELL_WEEKDAY_TIME_12H
    return "${PDF_CELL_DATE_FORMATTER.format(dateTime.date)}\n${timeFormatter.format(dateTime)}"
}

/** Date-only counterpart of [formatEventTime], for the log sheet's separate date/time buttons. */
internal fun formatEventDate(
    occurredAt: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = MEDIUM_DATE_FORMATTER.format(Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone).date)

/** A share card's footer timestamp: the generation date (with its year, since a card outlives the day it was made) and the time of day, e.g. "Oct 1, 2026, 2:32 PM". */
internal fun formatCardTimestamp(
    generatedAt: Long,
    use24Hour: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = "${formatEventDate(generatedAt, zone)}, ${formatEventTimeOfDay(generatedAt, use24Hour, zone)}"

/**
 * A history date-range bound: "MMM d" when [occurredAt] falls in the same calendar year as [now],
 * "MMM d, yyyy" otherwise — the same same-year rationale as [formatEventTime], applied to a bare
 * date. Used by the History Share card's range subtitle and the History tab/History Share range
 * note/button, so all three format bounds identically before handing them to a Voice range-note
 * function.
 */
internal fun formatDateRangeBound(
    occurredAt: Long,
    now: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val date = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone).date
    val sameYear =
        date.year ==
            Instant
                .fromEpochMilliseconds(now)
                .toLocalDateTime(zone)
                .date.year
    return (if (sameYear) SPAN_DATE_FORMATTER else MEDIUM_DATE_FORMATTER).format(date)
}

/**
 * The two bounds a History range shows, formatted. An unset [dateFrom] shows the Case's [createdAt] and an
 * unset [dateTo] shows [now], so the default range reads as the Case's actual span. Filtering keeps the
 * raw nullable bounds: events can be dated before [createdAt], so it must never become a filter bound.
 */
internal fun historyRangeBounds(
    createdAt: Long,
    dateFrom: Long?,
    dateTo: Long?,
    now: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Pair<String, String> = formatDateRangeBound(dateFrom ?: createdAt, now, zone) to formatDateRangeBound(dateTo ?: now, now, zone)

/**
 * A PDF's date-range bound (feature/pdf-export): always carries the year, unlike
 * [formatDateRangeBound]'s same-year omission - a PDF has no "today" of its own to compare
 * against once it's out of the app, so a date from the current calendar year would otherwise
 * read as ambiguous the moment the year turns over.
 */
internal fun formatPdfDateRangeBound(
    occurredAt: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String = MEDIUM_DATE_FORMATTER.format(Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone).date)

/** [pdfDateRangeBounds]'s counterpart of [historyRangeBounds] - same fallback rules, year-always bounds. */
internal fun pdfDateRangeBounds(
    createdAt: Long,
    dateFrom: Long?,
    dateTo: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Pair<String, String> = formatPdfDateRangeBound(dateFrom ?: createdAt, zone) to formatPdfDateRangeBound(dateTo, zone)

/** Time-only counterpart of [formatEventTime], for the log sheet's separate date/time buttons. */
internal fun formatEventTimeOfDay(
    occurredAt: Long,
    use24Hour: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val time = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone).time
    return (if (use24Hour) TIME_ONLY_24H else TIME_ONLY_12H).format(time)
}

/** Wall-clock time of a [LocalTime] already resolved to a zone — the Big Picture event-detail row. */
internal fun formatClockTime(
    time: LocalTime,
    use24Hour: Boolean,
): String = (if (use24Hour) TIME_ONLY_24H else TIME_ONLY_12H).format(time)

/**
 * "MMM d, h:mm a" — a Big Picture ongoing/span endpoint that fell on a different day than the row
 * it's shown on, so the day alone would drop the actual start time and the time alone would read
 * as belonging to the row's day. No year (the dialog is always within recent history).
 */
internal fun formatSpanDateTime(
    occurredAt: Long,
    use24Hour: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String =
    (if (use24Hour) SPAN_DATE_TIME_24H else SPAN_DATE_TIME_12H).format(Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone))

/** Localized medium date — the Big Picture day/week detail dialog titles. */
internal fun formatMediumDate(date: LocalDate): String = MEDIUM_DATE_FORMATTER.format(date)

/**
 * A still-running event's projected/expected `endedAt` (spec §6), for the ongoing-indicator's trailing
 * text (History/Insights rows, Home, widgets): bare time-of-day ([formatEventTimeOfDay]) when it
 * falls on the same calendar day as [now], else the fuller [formatSpanDateTime] — the same
 * same-day-vs-not rule Big Picture's own span endpoints use, and shared here so the ongoing
 * indicator's trailing text doesn't run noticeably longer than its sibling
 * [formatElapsedDuration]'s compact "2h 15m" in the common same-day case. A plain function (not a
 * `@Composable`) so both the Compose ongoing indicator and the Glance widgets' own subtitle can
 * call it.
 */
internal fun formatExpectedEndTime(
    expectedEndAt: Long,
    now: Long,
    use24Hour: Boolean,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): String {
    val sameDay =
        Instant.fromEpochMilliseconds(expectedEndAt).toLocalDateTime(zone).date ==
            Instant.fromEpochMilliseconds(now).toLocalDateTime(zone).date
    return if (sameDay) formatEventTimeOfDay(expectedEndAt, use24Hour, zone) else formatSpanDateTime(expectedEndAt, use24Hour, zone)
}

/** "EEE d" — the per-day header inside the Big Picture week detail dialog. */
internal fun formatWeekdayDayDate(date: LocalDate): String = WEEKDAY_DAY_FORMATTER.format(date)

/**
 * Frequency-chart tick label — deliberately numeric/short rather than authored copy (spec S9: the
 * old "Week of …" wrapper text crowded the chart once more than two ticks were shown), so unlike
 * every other formatter in this file it needs no `Voice` input. Fixed English, like every other
 * formatter here (see this file's own doc comment) — no longer locale-parameterized.
 */
internal fun formatFrequencyTickLabel(
    periodStart: LocalDate,
    granularity: FrequencyGranularity,
): String =
    when (granularity) {
        FrequencyGranularity.DAY -> periodStart.day.toString()
        FrequencyGranularity.WEEK -> "${periodStart.month.number}/${periodStart.day}"
        FrequencyGranularity.MONTH -> periodStart.month.englishAbbreviated()
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
// narrow enough to show on every bar; Week's "M/d" and Month's short name need more room.
internal const val FREQUENCY_TICK_COUNT_DAY = 12
internal const val FREQUENCY_TICK_COUNT_WEEK = 6
internal const val FREQUENCY_TICK_COUNT_MONTH = 6

internal fun frequencyTickCount(granularity: FrequencyGranularity): Int =
    when (granularity) {
        FrequencyGranularity.DAY -> FREQUENCY_TICK_COUNT_DAY
        FrequencyGranularity.WEEK -> FREQUENCY_TICK_COUNT_WEEK
        FrequencyGranularity.MONTH -> FREQUENCY_TICK_COUNT_MONTH
    }
