package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class LogDetailViewModelTest {
    private val utc = ZoneId.of("UTC")
    // --- draftFrom ---

    @Test
    fun `draftFrom with no event defaults to now with everything empty`() {
        val draft = draftFrom(event = null, now = 1_000L)

        assertEquals(1_000L, draft.occurredAt)
        assertNull(draft.intensity)
        assertEquals("", draft.durationMinutes)
        assertEquals("", draft.note)
        assertTrue(draft.tags.isEmpty())
    }

    @Test
    fun `draftFrom an existing event without a duration leaves durationMinutes blank`() {
        val event = testEvent(occurredAt = 5_000L, endedAt = null)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("", draft.durationMinutes)
    }

    @Test
    fun `draftFrom an existing event computes duration minutes from endedAt`() {
        val event = testEvent(occurredAt = 0L, endedAt = 10 * 60_000L)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("10", draft.durationMinutes)
    }

    @Test
    fun `draftFrom carries over intensity and note`() {
        val event = testEvent(intensity = 3, note = "Ouch")

        val draft = draftFrom(event, now = 0L)

        assertEquals(3, draft.intensity)
        assertEquals("Ouch", draft.note)
    }

    @Test
    fun `draftFrom maps existing tags to their names`() {
        val event = testEvent()

        val draft = draftFrom(event, now = 0L, tags = listOf(TagEntity(id = 1, name = "work"), TagEntity(id = 2, name = "morning")))

        assertEquals(listOf("work", "morning"), draft.tags)
    }

    @Test
    fun `draftFrom carries over the event's existingEndedAt verbatim`() {
        val event = testEvent(occurredAt = 0L, endedAt = 12_345L)

        val draft = draftFrom(event, now = 0L)

        assertEquals(12_345L, draft.existingEndedAt)
    }

    @Test
    fun `draftFrom with no event has a null existingEndedAt`() {
        val draft = draftFrom(event = null, now = 0L)

        assertNull(draft.existingEndedAt)
    }

    // --- computeEndedAt ---

    @Test
    fun `computeEndedAt passes existingEndedAt through unchanged when duration mode is NONE`() {
        val endedAt =
            computeEndedAt(occurredAt = 0L, durationMode = DurationMode.NONE, durationMinutesInput = "30", existingEndedAt = 999L)

        assertEquals(999L, endedAt)
    }

    @Test
    fun `computeEndedAt is null for NONE mode when there is no existingEndedAt`() {
        assertNull(
            computeEndedAt(occurredAt = 0L, durationMode = DurationMode.NONE, durationMinutesInput = "30", existingEndedAt = null),
        )
    }

    @Test
    fun `computeEndedAt passes existingEndedAt through unchanged when duration mode is START_STOP`() {
        val endedAt =
            computeEndedAt(
                occurredAt = 0L,
                durationMode = DurationMode.START_STOP,
                durationMinutesInput = "30",
                existingEndedAt = 777L,
            )

        assertEquals(777L, endedAt)
    }

    @Test
    fun `computeEndedAt adds parsed minutes to occurredAt for MANUAL mode`() {
        val endedAt =
            computeEndedAt(occurredAt = 1_000L, durationMode = DurationMode.MANUAL, durationMinutesInput = "5", existingEndedAt = null)

        assertEquals(1_000L + 5 * 60_000L, endedAt)
    }

    @Test
    fun `computeEndedAt for MANUAL mode ignores existingEndedAt and recomputes from the input`() {
        val endedAt =
            computeEndedAt(occurredAt = 1_000L, durationMode = DurationMode.MANUAL, durationMinutesInput = "5", existingEndedAt = 999L)

        assertEquals(1_000L + 5 * 60_000L, endedAt)
    }

    @Test
    fun `computeEndedAt is null for MANUAL mode with blank input, even with an existingEndedAt`() {
        assertNull(
            computeEndedAt(occurredAt = 1_000L, durationMode = DurationMode.MANUAL, durationMinutesInput = "", existingEndedAt = 999L),
        )
    }

    @Test
    fun `computeEndedAt is null for MANUAL mode with zero or negative minutes`() {
        assertNull(
            computeEndedAt(occurredAt = 1_000L, durationMode = DurationMode.MANUAL, durationMinutesInput = "0", existingEndedAt = null),
        )
        assertNull(
            computeEndedAt(occurredAt = 1_000L, durationMode = DurationMode.MANUAL, durationMinutesInput = "-5", existingEndedAt = null),
        )
    }

    @Test
    fun `computeEndedAt is null for MANUAL mode with non-numeric input`() {
        assertNull(
            computeEndedAt(occurredAt = 1_000L, durationMode = DurationMode.MANUAL, durationMinutesInput = "abc", existingEndedAt = null),
        )
    }

    // --- LogDraft#toEventEntity ---

    // Far beyond any occurredAt used below, so it never clamps unless a test is specifically
    // exercising the clamp.
    private val farFuture = 1_000_000_000_000L

    @Test
    fun `toEventEntity trims blank note to null`() {
        val draft = testDraft(note = "   ")

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.NONE, now = farFuture)

        assertNull(entity.note)
    }

    @Test
    fun `toEventEntity trims surrounding whitespace from a non-blank note`() {
        val draft = testDraft(note = "  Ouch  ")

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.NONE, now = farFuture)

        assertEquals("Ouch", entity.note)
    }

    @Test
    fun `toEventEntity carries caseId, existingId and loggedAt through unchanged`() {
        val draft = testDraft(occurredAt = 42L, intensity = 2)

        val entity =
            draft.toEventEntity(caseId = 7L, existingId = 99L, loggedAt = 123L, durationMode = DurationMode.NONE, now = farFuture)

        assertEquals(7L, entity.caseId)
        assertEquals(99L, entity.id)
        assertEquals(123L, entity.loggedAt)
        assertEquals(42L, entity.occurredAt)
        assertEquals(2, entity.intensity)
    }

    @Test
    fun `toEventEntity computes endedAt from duration mode and minutes`() {
        val draft = testDraft(durationMinutes = "15")

        val entity =
            draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.MANUAL, now = farFuture)

        assertEquals(15 * 60_000L, entity.endedAt)
    }

    @Test
    fun `toEventEntity preserves an existing duration when editing under NONE or START_STOP mode`() {
        val draft = testDraft(existingEndedAt = 55_000L)

        val underNone =
            draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, durationMode = DurationMode.NONE, now = farFuture)
        val underStartStop =
            draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, durationMode = DurationMode.START_STOP, now = farFuture)

        assertEquals(55_000L, underNone.endedAt)
        assertEquals(55_000L, underStartStop.endedAt)
    }

    @Test
    fun `toEventEntity clamps a future occurredAt to now`() {
        val now = 10_000L
        val draft = testDraft(occurredAt = now + 60_000L)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.NONE, now = now)

        assertEquals(now, entity.occurredAt)
    }

    @Test
    fun `toEventEntity does not clamp a past or present occurredAt`() {
        val now = 10_000L
        val draft = testDraft(occurredAt = now - 60_000L)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.NONE, now = now)

        assertEquals(now - 60_000L, entity.occurredAt)
    }

    @Test
    fun `toEventEntity clamps occurredAt but computes endedAt from the clamped value, not the original`() {
        val now = 10_000L
        // occurredAt is in the future and gets clamped to now; MANUAL's 30-minute duration is
        // then applied on top of the *clamped* start, not the original future one.
        val draft = testDraft(occurredAt = now + 60_000L, durationMinutes = "30")

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.MANUAL, now = now)

        assertEquals(now, entity.occurredAt)
        assertEquals(now + 30 * 60_000L, entity.endedAt)
    }

    @Test
    fun `toEventEntity allows a MANUAL duration to project endedAt past now`() {
        // Started right now; a stated/expected 2-hour duration is a legitimate thing to log
        // up front, not something that requires waiting around to confirm.
        val now = 10_000L
        val draft = testDraft(occurredAt = now, durationMinutes = "120")

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.MANUAL, now = now)

        assertEquals(now + 120 * 60_000L, entity.endedAt)
    }

    // --- planSaveEvent ---

    @Test
    fun `planSaveEvent for a new event is an insert with loggedAt set to now`() {
        val draft = testDraft(occurredAt = 500L)

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = null, originalTags = emptyList(), DurationMode.NONE, now = 1_000L)

        assertTrue(!plan.isUpdate)
        assertEquals(1_000L, plan.entity.loggedAt)
        assertEquals(1L, plan.entity.caseId)
    }

    @Test
    fun `planSaveEvent for an existing event is an update that preserves the original loggedAt`() {
        val existing = testEvent(occurredAt = 500L, endedAt = null).copy(id = 42L, loggedAt = 111L)
        val draft = testDraft(occurredAt = 500L)

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = existing, originalTags = emptyList(), DurationMode.NONE, now = 1_000L)

        assertTrue(plan.isUpdate)
        assertEquals(111L, plan.entity.loggedAt)
        assertEquals(42L, plan.entity.id)
    }

    @Test
    fun `planSaveEvent computes the tag diff between originalTags and the draft's tags`() {
        val work = TagEntity(id = 1, name = "work")
        val draft = testDraft(tags = listOf("home"))

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = null, originalTags = listOf(work), DurationMode.NONE, now = 0L)

        assertEquals(setOf("home"), plan.tagDiff.toAdd)
        assertEquals(setOf(work), plan.tagDiff.toRemove)
    }

    @Test
    fun `planSaveEvent threads durationMode through to the built entity`() {
        val draft = testDraft(occurredAt = 0L, durationMinutes = "10")

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = null, originalTags = emptyList(), DurationMode.MANUAL, now = 0L)

        assertEquals(10 * 60_000L, plan.entity.endedAt)
    }

    // --- tagDiff ---

    @Test
    fun `tagDiff is empty when selection matches the original tags exactly`() {
        val original = listOf(TagEntity(id = 1, name = "work"))

        val diff = tagDiff(original, listOf("work"))

        assertTrue(diff.toAdd.isEmpty())
        assertTrue(diff.toRemove.isEmpty())
    }

    @Test
    fun `tagDiff adds names not present in the original tags`() {
        val diff = tagDiff(originalTags = emptyList(), selectedNames = listOf("work", "morning"))

        assertEquals(setOf("work", "morning"), diff.toAdd)
        assertTrue(diff.toRemove.isEmpty())
    }

    @Test
    fun `tagDiff removes original tags no longer selected`() {
        val work = TagEntity(id = 1, name = "work")
        val morning = TagEntity(id = 2, name = "morning")

        val diff = tagDiff(originalTags = listOf(work, morning), selectedNames = listOf("morning"))

        assertTrue(diff.toAdd.isEmpty())
        assertEquals(setOf(work), diff.toRemove)
    }

    @Test
    fun `tagDiff ignores blank entries in the selected names`() {
        val diff = tagDiff(originalTags = emptyList(), selectedNames = listOf("work", "  ", ""))

        assertEquals(setOf("work"), diff.toAdd)
    }

    @Test
    fun `tagDiff handles simultaneous add and remove`() {
        val work = TagEntity(id = 1, name = "work")

        val diff = tagDiff(originalTags = listOf(work), selectedNames = listOf("evening"))

        assertEquals(setOf("evening"), diff.toAdd)
        assertEquals(setOf(work), diff.toRemove)
    }

    // --- applyPickedDate ---

    @Test
    fun `applyPickedDate keeps the existing time-of-day and swaps only the date`() {
        val occurredAt = ZonedDateTime.of(2026, 1, 1, 15, 30, 0, 0, utc).toInstant().toEpochMilli()
        val pickedDateUtcMillis = ZonedDateTime.of(2026, 7, 9, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        val result = applyPickedDate(occurredAt, pickedDateUtcMillis, utc)

        val resultZoned = Instant.ofEpochMilli(result).atZone(utc)
        assertEquals(2026, resultZoned.year)
        assertEquals(7, resultZoned.monthValue)
        assertEquals(9, resultZoned.dayOfMonth)
        assertEquals(15, resultZoned.hour)
        assertEquals(30, resultZoned.minute)
    }

    // --- applyPickedTime ---

    @Test
    fun `applyPickedTime keeps the existing date and swaps only the time`() {
        val occurredAt = ZonedDateTime.of(2026, 7, 9, 8, 0, 0, 0, utc).toInstant().toEpochMilli()

        val result = applyPickedTime(occurredAt, hour = 21, minute = 45, zone = utc)

        val resultZoned = Instant.ofEpochMilli(result).atZone(utc)
        assertEquals(9, resultZoned.dayOfMonth)
        assertEquals(21, resultZoned.hour)
        assertEquals(45, resultZoned.minute)
    }

    // --- formatEventDate / formatEventTimeOfDay ---

    @Test
    fun `formatEventDate renders only the date`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()

        val formatted = formatEventDate(instant.toEpochMilli(), utc)

        assertEquals("Jul 9, 2026", formatted)
    }

    @Test
    fun `formatEventTimeOfDay renders only the time`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()

        val formatted = formatEventTimeOfDay(instant.toEpochMilli(), utc)

        assertTrue(formatted.contains("3:30"))
        assertTrue(formatted.contains("PM"))
    }

    private fun testEvent(
        occurredAt: Long = 0L,
        endedAt: Long? = null,
        intensity: Int? = null,
        note: String? = null,
    ) = EventEntity(
        caseId = 1L,
        occurredAt = occurredAt,
        endedAt = endedAt,
        intensity = intensity,
        note = note,
        loggedAt = occurredAt,
    )

    private fun testDraft(
        occurredAt: Long = 0L,
        intensity: Int? = null,
        durationMinutes: String = "",
        note: String = "",
        tags: List<String> = emptyList(),
        existingEndedAt: Long? = null,
    ) = LogDraft(
        occurredAt = occurredAt,
        intensity = intensity,
        durationMinutes = durationMinutes,
        note = note,
        tags = tags,
        existingEndedAt = existingEndedAt,
    )
}
