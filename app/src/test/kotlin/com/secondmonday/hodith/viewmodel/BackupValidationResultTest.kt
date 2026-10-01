package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.data.WatchKind
import com.secondmonday.hodith.data.backup.BackupData
import com.secondmonday.hodith.ui.logsheet.TAG_NAME_MAX_LENGTH
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixture builders mirror `androidTest`'s `TestFixtures.kt` (not reachable from this JVM-only
 * source set, same rationale as `FakeHodithRepositoryTest`'s local `testCase`/`testEvent`).
 */
private fun testCase(
    id: Long = 1L,
    name: String = "Migraines",
    description: String? = null,
    icon: String = "🧠",
) = CaseEntity(
    id = id,
    name = name,
    description = description,
    icon = icon,
    createdAt = 0L,
    logFlow = LogFlow.ONE_TAP,
    durationMode = DurationMode.NONE,
    intensityEnabled = false,
    checkInsEnabled = true,
    lastCheckInAt = null,
    sortOrder = 0,
    archived = false,
)

private fun testEvent(
    id: Long = 1L,
    caseId: Long = 1L,
    note: String? = null,
    utcOffsetMinutes: Int = 0,
) = EventEntity(
    id = id,
    caseId = caseId,
    occurredAt = 0L,
    endedAt = null,
    intensity = null,
    note = note,
    loggedAt = 0L,
    utcOffsetMinutes = utcOffsetMinutes,
)

private fun testWatch(
    id: Long = 1L,
    caseId: Long = 1L,
    kind: WatchKind = WatchKind.OFTEN,
    threshold: Int = 3,
    windowDays: Int? = 7,
) = WatchEntity(
    id = id,
    caseId = caseId,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    expectedPer = ExpectedPer.WEEK,
    metric = VerdictMetric.OCCURRENCE_COUNT,
    minIntensity = null,
    enabled = true,
    armed = true,
    lastFiredAt = null,
)

/** A minimal, self-referentially-consistent backup: one case, one tagged event, one watch. */
private fun validBackup() =
    BackupData(
        cases = listOf(testCase()),
        tags = listOf(TagEntity(id = 1L, name = "aura")),
        events = listOf(testEvent()),
        eventTags = listOf(EventTagCrossRef(eventId = 1L, tagId = 1L)),
        watches = listOf(testWatch()),
    )

class BackupValidationResultTest {
    @Test
    fun `a well-formed backup is valid`() {
        assertTrue(validateBackup(validBackup()).isValid)
    }

    @Test
    fun `a blank case name is rejected`() {
        val backup = validBackup().copy(cases = listOf(testCase(name = "   ")))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a case name over the length cap is rejected`() {
        val backup = validBackup().copy(cases = listOf(testCase(name = "a".repeat(CASE_NAME_MAX_LENGTH + 1))))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a case description over the length cap is rejected`() {
        val backup = validBackup().copy(cases = listOf(testCase(description = "a".repeat(CASE_DESCRIPTION_MAX_LENGTH + 1))))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a blank case icon is rejected`() {
        val backup = validBackup().copy(cases = listOf(testCase(icon = "")))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a blank tag name is rejected`() {
        val backup = validBackup().copy(tags = listOf(TagEntity(id = 1L, name = " ")))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a tag name over the length cap is rejected`() {
        val backup = validBackup().copy(tags = listOf(TagEntity(id = 1L, name = "a".repeat(TAG_NAME_MAX_LENGTH + 1))))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `duplicate tag names are rejected`() {
        val backup =
            validBackup().copy(
                tags = listOf(TagEntity(id = 1L, name = "aura"), TagEntity(id = 2L, name = "aura")),
                eventTags = emptyList(),
            )
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an event note over the length cap is rejected`() {
        val backup = validBackup().copy(events = listOf(testEvent(note = "a".repeat(EVENT_NOTE_MAX_LENGTH + 1))))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `two cases sharing a non-zero id are rejected`() {
        val backup = validBackup().copy(cases = listOf(testCase(id = 1L, name = "First"), testCase(id = 1L, name = "Second")))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an event with a dangling caseId is rejected`() {
        val backup = validBackup().copy(events = listOf(testEvent(caseId = 999L)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an event with a utcOffsetMinutes within real-world bounds is accepted`() {
        val backup = validBackup().copy(events = listOf(testEvent(utcOffsetMinutes = -420)))
        assertTrue(validateBackup(backup).isValid)
    }

    @Test
    fun `an event with a utcOffsetMinutes outside real-world bounds is rejected`() {
        val backup = validBackup().copy(events = listOf(testEvent(utcOffsetMinutes = 900)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an event-tag cross-ref with a dangling eventId is rejected`() {
        val backup = validBackup().copy(eventTags = listOf(EventTagCrossRef(eventId = 999L, tagId = 1L)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an event-tag cross-ref with a dangling tagId is rejected`() {
        val backup = validBackup().copy(eventTags = listOf(EventTagCrossRef(eventId = 1L, tagId = 999L)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a watch with a dangling caseId is rejected`() {
        val backup = validBackup().copy(watches = listOf(testWatch(caseId = 999L)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a watch threshold above the allowed range is rejected`() {
        val backup = validBackup().copy(watches = listOf(testWatch(threshold = 1000)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an OFTEN watch with a null windowDays is rejected`() {
        val backup = validBackup().copy(watches = listOf(testWatch(kind = WatchKind.OFTEN, windowDays = null)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an OFTEN watch with a zero windowDays is rejected`() {
        val backup = validBackup().copy(watches = listOf(testWatch(kind = WatchKind.OFTEN, windowDays = 0)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a QUIET watch with a non-null windowDays is rejected`() {
        val backup = validBackup().copy(watches = listOf(testWatch(kind = WatchKind.QUIET, windowDays = 7)))
        assertTrue(!validateBackup(backup).isValid)
    }
}
