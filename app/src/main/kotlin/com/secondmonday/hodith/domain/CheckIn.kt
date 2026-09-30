package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.CaseEntity

/**
 * Spec §11's effective check-in interval for a Case: off if the toggle is off, otherwise the
 * Settings default.
 */
internal fun effectiveCheckInDays(
    checkInsEnabled: Boolean,
    settingsDefaultDays: Int?,
): Int? {
    if (!checkInsEnabled) return null
    return settingsDefaultDays
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
    settingsDefaultDays: Int?,
    mostRecentEventAt: Long?,
    now: Long,
): CheckInDecision {
    val effectiveDays =
        effectiveCheckInDays(case.checkInsEnabled, settingsDefaultDays)
            ?: return CheckInDecision(due = false, silentDays = 0)

    val anchor = maxOf(case.createdAt, case.lastCheckInAt ?: case.createdAt, mostRecentEventAt ?: case.createdAt)
    // anchor-vs-now: "now" has no captured offset by definition, so daysBetween's default
    // device-current-zone resolution is already correct here — see computeGapStats's doc comment
    // for the general per-event-vs-now rule this follows.
    val silentDays = daysBetween(anchor, now)
    return CheckInDecision(due = silentDays >= effectiveDays, silentDays = silentDays)
}
