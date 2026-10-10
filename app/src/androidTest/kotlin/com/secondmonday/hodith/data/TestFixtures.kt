package com.secondmonday.hodith.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import javax.inject.Provider

fun createInMemoryDatabase(): HodithDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HodithDatabase::class.java).build()

/** A [NotificationEvalScheduler] whose evaluator is never meant to run, for repository tests that need one wired in but don't exercise it. */
fun unusedScheduler(reason: String) =
    NotificationEvalScheduler(
        scope = CoroutineScope(Dispatchers.Unconfined),
        evaluator = Provider { error(reason) },
    )

fun testCase(
    id: Long = 0L,
    name: String = "Test Case",
    description: String? = null,
    icon: String = "🐛",
    createdAt: Long = 0L,
    logFlow: LogFlow = LogFlow.ONE_TAP,
    durationMode: DurationMode = DurationMode.NONE,
    intensityEnabled: Boolean = false,
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
    checkInsEnabled = checkInsEnabled,
    lastCheckInAt = lastCheckInAt,
    sortOrder = sortOrder,
    archived = archived,
)

fun testEvent(
    caseId: Long,
    id: Long = 0L,
    occurredAt: Long = 0L,
    endedAt: Long? = null,
    intensity: Int? = null,
    note: String? = null,
    loggedAt: Long = occurredAt,
    utcOffsetMinutes: Int = ZoneId.systemDefault().offsetMinutesAt(occurredAt),
) = EventEntity(
    id = id,
    caseId = caseId,
    occurredAt = occurredAt,
    endedAt = endedAt,
    intensity = intensity,
    note = note,
    loggedAt = loggedAt,
    utcOffsetMinutes = utcOffsetMinutes,
)

fun testWatch(
    caseId: Long,
    kind: WatchKind = WatchKind.OFTEN,
    threshold: Int = 3,
    windowDays: Int? = 7,
    expectedPer: ExpectedPer = ExpectedPer.WEEK,
    metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
    minIntensity: Int? = null,
    enabled: Boolean = true,
    armed: Boolean = true,
    lastFiredAt: Long? = null,
) = WatchEntity(
    caseId = caseId,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    expectedPer = expectedPer,
    metric = metric,
    minIntensity = minIntensity,
    enabled = enabled,
    armed = armed,
    lastFiredAt = lastFiredAt,
)
