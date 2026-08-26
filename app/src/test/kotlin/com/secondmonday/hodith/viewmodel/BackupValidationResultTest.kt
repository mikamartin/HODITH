package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
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
    hunchNudgeDismissed = false,
    checkInsEnabled = true,
    lastCheckInAt = null,
    sortOrder = 0,
    archived = false,
)

private fun testEvent(
    id: Long = 1L,
    caseId: Long = 1L,
    note: String? = null,
) = EventEntity(id = id, caseId = caseId, occurredAt = 0L, endedAt = null, intensity = null, note = note, loggedAt = 0L)

private fun testHunch(
    id: Long = 1L,
    caseId: Long = 1L,
    expectedCount: Int = 3,
) = HunchEntity(
    id = id,
    caseId = caseId,
    direction = HunchDirection.JUST_CURIOUS,
    expectedCount = expectedCount,
    expectedPer = ExpectedPer.WEEK,
    createdAt = 0L,
    resolvedAt = null,
)

private fun testTrigger(
    id: Long = 1L,
    caseId: Long = 1L,
    kind: TriggerKind = TriggerKind.AT_LEAST,
    threshold: Int = 3,
    windowDays: Int? = 7,
) = TriggerEntity(
    id = id,
    caseId = caseId,
    kind = kind,
    threshold = threshold,
    windowDays = windowDays,
    enabled = true,
    armed = true,
    lastFiredAt = null,
)

/** A minimal, self-referentially-consistent backup: one case, one tagged event, one hunch, one trigger. */
private fun validBackup() =
    BackupData(
        cases = listOf(testCase()),
        tags = listOf(TagEntity(id = 1L, name = "aura")),
        events = listOf(testEvent()),
        eventTags = listOf(EventTagCrossRef(eventId = 1L, tagId = 1L)),
        hunches = listOf(testHunch()),
        triggers = listOf(testTrigger()),
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
    fun `a hunch with a dangling caseId is rejected`() {
        val backup = validBackup().copy(hunches = listOf(testHunch(caseId = 999L)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a hunch expectedCount below the allowed range is rejected`() {
        val backup = validBackup().copy(hunches = listOf(testHunch(expectedCount = 0)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a hunch expectedCount above the allowed range is rejected`() {
        val backup = validBackup().copy(hunches = listOf(testHunch(expectedCount = 100)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a trigger with a dangling caseId is rejected`() {
        val backup = validBackup().copy(triggers = listOf(testTrigger(caseId = 999L)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a trigger threshold above the allowed range is rejected`() {
        val backup = validBackup().copy(triggers = listOf(testTrigger(threshold = 1000)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an AT_LEAST trigger with a null windowDays is rejected`() {
        val backup = validBackup().copy(triggers = listOf(testTrigger(kind = TriggerKind.AT_LEAST, windowDays = null)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `an AT_LEAST trigger with a zero windowDays is rejected`() {
        val backup = validBackup().copy(triggers = listOf(testTrigger(kind = TriggerKind.AT_LEAST, windowDays = 0)))
        assertTrue(!validateBackup(backup).isValid)
    }

    @Test
    fun `a SILENT_FOR trigger with a non-null windowDays is rejected`() {
        val backup = validBackup().copy(triggers = listOf(testTrigger(kind = TriggerKind.SILENT_FOR, windowDays = 7)))
        assertTrue(!validateBackup(backup).isValid)
    }
}
