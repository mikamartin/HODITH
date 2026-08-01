package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchEntity
import kotlin.math.roundToInt

/** Spec §11 clamp bounds for a Hunch-derived check-in interval. */
internal const val HUNCH_CHECK_IN_MIN_DAYS = 3
internal const val HUNCH_CHECK_IN_MAX_DAYS = 30

private const val HUNCH_CHECK_IN_GAP_MULTIPLIER = 2

/**
 * Spec §11's effective check-in interval for a Case: off if the toggle is off, otherwise a Hunch
 * takes priority over the Settings default. [hunch] should be the Case's active (unresolved)
 * Hunch, if any — callers are responsible for filtering out resolved ones.
 */
internal fun effectiveCheckInDays(
    checkInsEnabled: Boolean,
    hunch: HunchEntity?,
    settingsDefaultDays: Int?,
): Int? {
    if (!checkInsEnabled) return null
    return hunch?.let(::hunchCheckInDays) ?: settingsDefaultDays
}

/** 2× the Hunch's expected gap between events, clamped to spec §11's 3–30 day bounds. */
internal fun hunchCheckInDays(hunch: HunchEntity): Int {
    val periodDays =
        when (hunch.expectedPer) {
            ExpectedPer.DAY -> 1.0
            ExpectedPer.WEEK -> DAYS_PER_WEEK
            ExpectedPer.MONTH -> DAYS_PER_MONTH
        }
    val expectedGapDays = periodDays / hunch.expectedCount
    return (expectedGapDays * HUNCH_CHECK_IN_GAP_MULTIPLIER)
        .roundToInt()
        .coerceIn(HUNCH_CHECK_IN_MIN_DAYS, HUNCH_CHECK_IN_MAX_DAYS)
}

/** Result of [evaluateCheckIn]. [silentDays] is reported even when not [due], for notification/UI copy. */
data class CheckInDecision(
    val due: Boolean,
    val silentDays: Long,
)

/**
 * Spec §11: a check-in fires when a Case has had zero events for its effective interval — counted
 * from the latest of last event, last check-in, or case creation. That "latest of" is what makes a
 * created-but-never-logged Case eventually check in too, without a special case.
 */
fun evaluateCheckIn(
    case: CaseEntity,
    hunch: HunchEntity?,
    settingsDefaultDays: Int?,
    mostRecentEventAt: Long?,
    now: Long,
): CheckInDecision {
    val effectiveDays =
        effectiveCheckInDays(case.checkInsEnabled, hunch, settingsDefaultDays)
            ?: return CheckInDecision(due = false, silentDays = 0)

    val anchor = maxOf(case.createdAt, case.lastCheckInAt ?: case.createdAt, mostRecentEventAt ?: case.createdAt)
    val silentDays = daysBetween(anchor, now)
    return CheckInDecision(due = silentDays >= effectiveDays, silentDays = silentDays)
}
