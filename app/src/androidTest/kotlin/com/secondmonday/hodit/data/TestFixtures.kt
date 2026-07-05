package com.secondmonday.hodit.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

fun createInMemoryDatabase(): HoditDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HoditDatabase::class.java).build()

fun testCase(
    name: String = "Test Case",
    icon: String = "🐛",
    createdAt: Long = 0L,
    logFlow: LogFlow = LogFlow.ONE_TAP,
    durationMode: DurationMode = DurationMode.NONE,
    intensityEnabled: Boolean = false,
    hunchNudgeDismissed: Boolean = false,
    pinned: Boolean = false,
    checkInDays: Int? = null,
    lastCheckInAt: Long? = null,
    sortOrder: Int = 0,
    archived: Boolean = false,
) = CaseEntity(
    name = name,
    icon = icon,
    createdAt = createdAt,
    logFlow = logFlow,
    durationMode = durationMode,
    intensityEnabled = intensityEnabled,
    hunchNudgeDismissed = hunchNudgeDismissed,
    pinned = pinned,
    checkInDays = checkInDays,
    lastCheckInAt = lastCheckInAt,
    sortOrder = sortOrder,
    archived = archived,
)

fun testEvent(
    caseId: Long,
    occurredAt: Long = 0L,
    endedAt: Long? = null,
    intensity: Int? = null,
    note: String? = null,
    loggedAt: Long = occurredAt,
) = EventEntity(
    caseId = caseId,
    occurredAt = occurredAt,
    endedAt = endedAt,
    intensity = intensity,
    note = note,
    loggedAt = loggedAt,
)

fun testHunch(
    caseId: Long,
    direction: HunchDirection = HunchDirection.JUST_CURIOUS,
    expectedCount: Int = 1,
    expectedPer: ExpectedPer = ExpectedPer.WEEK,
    createdAt: Long = 0L,
    resolvedAt: Long? = null,
) = HunchEntity(
    caseId = caseId,
    direction = direction,
    expectedCount = expectedCount,
    expectedPer = expectedPer,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
)

fun testTrigger(
    caseId: Long,
    kind: TriggerKind = TriggerKind.AT_LEAST,
    threshold: Int = 3,
    windowDays: Int? = 7,
    enabled: Boolean = true,
    lastFiredAt: Long? = null,
) = TriggerEntity(
    caseId = caseId,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    enabled = enabled,
    lastFiredAt = lastFiredAt,
)
