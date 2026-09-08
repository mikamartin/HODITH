package com.secondmonday.hodith.data

/**
 * The period a Hunch's expected frequency is stated against. [QUARTER] ("3 months") exists for
 * the [VerdictMetric.DAYS_ACTIVE] metric, where "days active per day" is nonsensical and a
 * day-count only reads meaningfully over a longer span — the Hunch-creation sheet offers
 * Day/Week/Month for occurrence count and Week/Month/Quarter for days-active.
 */
enum class ExpectedPer {
    DAY,
    WEEK,
    MONTH,
    QUARTER,
}
