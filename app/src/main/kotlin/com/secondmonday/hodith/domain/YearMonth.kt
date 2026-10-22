package com.secondmonday.hodith.domain

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlin.time.ExperimentalTime
import kotlin.time.Clock as KotlinxClock

/**
 * `kotlinx-datetime` has no `java.time.YearMonth` equivalent — hand-rolled rather than reusing
 * `LocalDate` with an implied day-of-month convention, so callers keep the type-level guarantee
 * that this is a month, not a day (`BigPictureFilterState`, `InsightsTabState.HeatmapMonth`,
 * `CalendarGrid.weeksInGrid` all rely on it).
 */
data class YearMonth(
    val year: Int,
    val month: Month,
) : Comparable<YearMonth> {
    fun atDay(dayOfMonth: Int): LocalDate = LocalDate(year, month, dayOfMonth)

    fun atEndOfMonth(): LocalDate = atDay(1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

    fun lengthOfMonth(): Int = atEndOfMonth().day

    fun plusMonths(n: Int): YearMonth = atDay(1).plus(n, DateTimeUnit.MONTH).let { YearMonth(it.year, it.month) }

    fun minusMonths(n: Int): YearMonth = plusMonths(-n)

    fun isAfter(other: YearMonth): Boolean = this > other

    fun isBefore(other: YearMonth): Boolean = this < other

    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.month.number })

    companion object {
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.month)

        @OptIn(ExperimentalTime::class)
        fun now(zone: TimeZone = TimeZone.currentSystemDefault()): YearMonth = from(KotlinxClock.System.todayIn(zone))
    }
}
