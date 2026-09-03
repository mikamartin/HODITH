package com.secondmonday.hodith.testsupport

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.LogFlow

/**
 * Full-literal builders for the two entities every JVM test needs. Namespaced in an object so a
 * test class can keep a thin `private fun testCase(...) = Fixtures.case(...)` forwarder (e.g. for
 * clock-derived defaults) without the name shadowing itself.
 */
object Fixtures {
    /**
     * A neutral [CaseEntity] — `NONE` duration, one-tap logging, active, check-ins on. Pass only
     * the fields a test exercises; everything else stays at the neutral default.
     */
    fun case(
        id: Long = 1L,
        name: String = "Test Case",
        description: String? = null,
        icon: String = "🐛",
        createdAt: Long = 0L,
        logFlow: LogFlow = LogFlow.ONE_TAP,
        durationMode: DurationMode = DurationMode.NONE,
        intensityEnabled: Boolean = false,
        hunchNudgeDismissed: Boolean = false,
        checkInsEnabled: Boolean = true,
        lastCheckInAt: Long? = null,
        sortOrder: Int = 0,
        archived: Boolean = false,
    ) = CaseEntity(
        id = id,
        name = name,
        description = description,
        icon = icon,
        createdAt = createdAt,
        logFlow = logFlow,
        durationMode = durationMode,
        intensityEnabled = intensityEnabled,
        hunchNudgeDismissed = hunchNudgeDismissed,
        checkInsEnabled = checkInsEnabled,
        lastCheckInAt = lastCheckInAt,
        sortOrder = sortOrder,
        archived = archived,
    )

    /**
     * A neutral [EventEntity] — a point event (`endedAt == null`) at [occurredAt], no
     * intensity/note, logged when it occurred. [loggedAt] defaults to [occurredAt].
     */
    fun event(
        id: Long = 0L,
        caseId: Long = 1L,
        occurredAt: Long = 0L,
        endedAt: Long? = null,
        intensity: Int? = null,
        note: String? = null,
        loggedAt: Long = occurredAt,
    ) = EventEntity(
        id = id,
        caseId = caseId,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = intensity,
        note = note,
        loggedAt = loggedAt,
    )
}

/** Shorthand for the neutral [CaseEntity] — see [Fixtures.case]. */
fun testCase(
    id: Long = 1L,
    name: String = "Test Case",
    description: String? = null,
    icon: String = "🐛",
    createdAt: Long = 0L,
    logFlow: LogFlow = LogFlow.ONE_TAP,
    durationMode: DurationMode = DurationMode.NONE,
    intensityEnabled: Boolean = false,
    hunchNudgeDismissed: Boolean = false,
    checkInsEnabled: Boolean = true,
    lastCheckInAt: Long? = null,
    sortOrder: Int = 0,
    archived: Boolean = false,
) = Fixtures.case(
    id,
    name,
    description,
    icon,
    createdAt,
    logFlow,
    durationMode,
    intensityEnabled,
    hunchNudgeDismissed,
    checkInsEnabled,
    lastCheckInAt,
    sortOrder,
    archived,
)

/** Shorthand for the neutral point [EventEntity] — [Fixtures.event] with the common args. */
fun testEvent(
    id: Long = 0L,
    caseId: Long = 1L,
    occurredAt: Long = 0L,
    endedAt: Long? = null,
    intensity: Int? = null,
    note: String? = null,
    loggedAt: Long = occurredAt,
) = Fixtures.event(id, caseId, occurredAt, endedAt, intensity, note, loggedAt)

/** A still-running `START_STOP` event: [occurredAt] set, `endedAt` null. */
fun runningEvent(
    id: Long = 0L,
    caseId: Long = 1L,
    occurredAt: Long = 0L,
) = Fixtures.event(id = id, caseId = caseId, occurredAt = occurredAt, endedAt = null)

/** A finished duration event spanning [occurredAt]..[endedAt]. */
fun finishedEvent(
    id: Long = 0L,
    caseId: Long = 1L,
    occurredAt: Long = 0L,
    endedAt: Long = 500_000L,
) = Fixtures.event(id = id, caseId = caseId, occurredAt = occurredAt, endedAt = endedAt)

/**
 * An [EventEntity] whose active span runs [startDay]..[endDay] (inclusive whole days, resolved in
 * [TEST_ZONE]); a null [endDay] leaves it open. Matches the domain tests' `durationEvent`.
 */
fun durationEvent(
    startDay: Long,
    endDay: Long?,
    caseId: Long = 1L,
) = Fixtures.event(
    caseId = caseId,
    occurredAt = millisAtDay(startDay),
    endedAt = endDay?.let { millisAtDay(it) },
    loggedAt = millisAtDay(startDay),
)

/** A point event on [epochDay] (`endedAt == null`). Matches the domain tests' `eventAtDay`. */
fun eventAtDay(epochDay: Long) = durationEvent(startDay = epochDay, endDay = null)

/**
 * A zero-length event on [epochDay] — `endedAt == occurredAt`, a genuine point that still carries
 * a non-null `endedAt`. Use where a test needs "this event is finished, not the running one"
 * without the `endedAt == null`-reads-as-ongoing ambiguity a bare [eventAtDay] carries on a
 * `START_STOP` Case.
 */
fun finishedPoint(
    epochDay: Long,
    caseId: Long = 1L,
) = durationEvent(startDay = epochDay, endDay = epochDay, caseId = caseId)

/** Wrap each event with no tags — the common shape for `insightsTabState` / stats inputs. */
fun List<EventEntity>.withoutTags(): List<EventWithTags> = map { EventWithTags(it, emptyList()) }
