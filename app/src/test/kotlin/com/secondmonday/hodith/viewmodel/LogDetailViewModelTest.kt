package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.testsupport.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
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
        assertEquals("", draft.durationAmount)
        assertEquals(DurationUnit.MINUTES, draft.durationUnit)
        assertEquals("", draft.note)
        assertTrue(draft.tags.isEmpty())
    }

    @Test
    fun `draftFrom an existing event without a duration leaves the amount blank on minutes`() {
        val event = testEvent(occurredAt = 5_000L, endedAt = null)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("", draft.durationAmount)
        assertEquals(DurationUnit.MINUTES, draft.durationUnit)
    }

    @Test
    fun `draftFrom loads a plain-minutes duration on the minutes unit`() {
        val event = testEvent(occurredAt = 0L, endedAt = 90 * 60_000L)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("90", draft.durationAmount)
        assertEquals(DurationUnit.MINUTES, draft.durationUnit)
    }

    @Test
    fun `draftFrom loads an exact-hours duration on the hours unit`() {
        val event = testEvent(occurredAt = 0L, endedAt = 2 * MILLIS_PER_HOUR)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("2", draft.durationAmount)
        assertEquals(DurationUnit.HOURS, draft.durationUnit)
    }

    @Test
    fun `draftFrom loads an exact-days duration on the days unit`() {
        val event = testEvent(occurredAt = 0L, endedAt = 3 * MILLIS_PER_DAY)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("3", draft.durationAmount)
        assertEquals(DurationUnit.DAYS, draft.durationUnit)
    }

    @Test
    fun `draftFrom falls back to minutes when the duration is not a whole number of hours`() {
        val event = testEvent(occurredAt = 0L, endedAt = 150 * 60_000L)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("150", draft.durationAmount)
        assertEquals(DurationUnit.MINUTES, draft.durationUnit)
    }

    @Test
    fun `draftFrom leaves the amount blank for a zero-length event`() {
        val event = testEvent(occurredAt = 10_000L, endedAt = 10_000L)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("", draft.durationAmount)
        assertEquals(DurationUnit.MINUTES, draft.durationUnit)
    }

    @Test
    fun `draftFrom leaves the amount blank for a reversed stored endedAt`() {
        val event = testEvent(occurredAt = 60_000L, endedAt = 0L)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals("", draft.durationAmount)
    }

    @Test
    fun `durationUnitFor is minutes for a zero or negative span`() {
        assertEquals(DurationUnit.MINUTES, durationUnitFor(0L))
        assertEquals(DurationUnit.MINUTES, durationUnitFor(-MILLIS_PER_HOUR))
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
    fun `draftFrom carries over the event's existingEndedAt and endedAt verbatim`() {
        val event = testEvent(occurredAt = 0L, endedAt = 12_345L)

        val draft = draftFrom(event, now = 0L)

        assertEquals(12_345L, draft.existingEndedAt)
        assertEquals(12_345L, draft.endedAt)
    }

    @Test
    fun `draftFrom with no event has a null existingEndedAt and endedAt`() {
        val draft = draftFrom(event = null, now = 0L)

        assertNull(draft.existingEndedAt)
        assertNull(draft.endedAt)
    }

    // --- computeEndedAt ---

    @Test
    fun `computeEndedAt passes existingEndedAt through unchanged when duration mode is NONE`() {
        val endedAt =
            computeEndedAt(0L, DurationMode.NONE, "30", DurationUnit.MINUTES, endedAt = null, existingEndedAt = 999L, now = farFuture)

        assertEquals(999L, endedAt)
    }

    @Test
    fun `computeEndedAt is null for NONE mode when there is no existingEndedAt`() {
        assertNull(
            computeEndedAt(0L, DurationMode.NONE, "30", DurationUnit.MINUTES, endedAt = null, existingEndedAt = null, now = farFuture),
        )
    }

    @Test
    fun `computeEndedAt is null for START_STOP mode when the draft's endedAt is null (still ongoing)`() {
        assertNull(
            computeEndedAt(
                0L,
                DurationMode.START_STOP,
                "30",
                DurationUnit.MINUTES,
                endedAt = null,
                existingEndedAt = 777L,
                now = farFuture,
            ),
        )
    }

    @Test
    fun `computeEndedAt uses the draft's endedAt for START_STOP mode when set`() {
        val endedAt =
            computeEndedAt(0L, DurationMode.START_STOP, "", DurationUnit.MINUTES, endedAt = 5_000L, existingEndedAt = null, now = farFuture)

        assertEquals(5_000L, endedAt)
    }

    @Test
    fun `computeEndedAt clamps a START_STOP endedAt that precedes occurredAt`() {
        val endedAt =
            computeEndedAt(
                occurredAt = 10_000L,
                DurationMode.START_STOP,
                "",
                DurationUnit.MINUTES,
                endedAt = 1_000L,
                existingEndedAt = null,
                now = farFuture,
            )

        assertEquals(10_000L, endedAt)
    }

    @Test
    fun `computeEndedAt clamps a START_STOP endedAt that is in the future`() {
        val now = 10_000L
        val endedAt =
            computeEndedAt(
                occurredAt = 0L,
                DurationMode.START_STOP,
                "",
                DurationUnit.MINUTES,
                endedAt = now + 60_000L,
                existingEndedAt = null,
                now = now,
            )

        assertEquals(now, endedAt)
    }

    @Test
    fun `computeEndedAt adds the parsed amount in minutes to occurredAt for MANUAL mode`() {
        val endedAt =
            computeEndedAt(1_000L, DurationMode.MANUAL, "5", DurationUnit.MINUTES, endedAt = null, existingEndedAt = null, now = farFuture)

        assertEquals(1_000L + 5 * 60_000L, endedAt)
    }

    @Test
    fun `computeEndedAt scales the parsed amount by the hours unit for MANUAL mode`() {
        val endedAt =
            computeEndedAt(1_000L, DurationMode.MANUAL, "2", DurationUnit.HOURS, endedAt = null, existingEndedAt = null, now = farFuture)

        assertEquals(1_000L + 2 * MILLIS_PER_HOUR, endedAt)
    }

    @Test
    fun `computeEndedAt scales the parsed amount by the days unit for MANUAL mode`() {
        val endedAt =
            computeEndedAt(1_000L, DurationMode.MANUAL, "6", DurationUnit.DAYS, endedAt = null, existingEndedAt = null, now = farFuture)

        assertEquals(1_000L + 6 * MILLIS_PER_DAY, endedAt)
    }

    @Test
    fun `computeEndedAt for MANUAL mode ignores existingEndedAt and recomputes from the input`() {
        val endedAt =
            computeEndedAt(1_000L, DurationMode.MANUAL, "5", DurationUnit.MINUTES, endedAt = null, existingEndedAt = 999L, now = farFuture)

        assertEquals(1_000L + 5 * 60_000L, endedAt)
    }

    @Test
    fun `computeEndedAt is null for MANUAL mode with blank input, even with an existingEndedAt`() {
        assertNull(
            computeEndedAt(1_000L, DurationMode.MANUAL, "", DurationUnit.MINUTES, endedAt = null, existingEndedAt = 999L, now = farFuture),
        )
    }

    @Test
    fun `computeEndedAt is null for MANUAL mode with zero or negative amounts`() {
        assertNull(
            computeEndedAt(1_000L, DurationMode.MANUAL, "0", DurationUnit.HOURS, endedAt = null, existingEndedAt = null, now = farFuture),
        )
        assertNull(
            computeEndedAt(1_000L, DurationMode.MANUAL, "-5", DurationUnit.HOURS, endedAt = null, existingEndedAt = null, now = farFuture),
        )
    }

    @Test
    fun `computeEndedAt is null for MANUAL mode with non-numeric input`() {
        assertNull(
            computeEndedAt(
                1_000L,
                DurationMode.MANUAL,
                "abc",
                DurationUnit.MINUTES,
                endedAt = null,
                existingEndedAt = null,
                now = farFuture,
            ),
        )
    }

    @Test
    fun `computeEndedAt correctly computes the longest input the duration field's digit cap allows`() {
        // The sheet caps typed digits at DURATION_AMOUNT_MAX_DIGITS (5), so "99999" is the largest
        // string computeEndedAt can ever actually receive. On the days unit that is also the biggest
        // millis product; confirms the Int * Long math stays clear of overflowing to null.
        val endedAt =
            computeEndedAt(1_000L, DurationMode.MANUAL, "99999", DurationUnit.DAYS, endedAt = null, existingEndedAt = null, now = farFuture)

        assertEquals(1_000L + 99_999 * MILLIS_PER_DAY, endedAt)
    }

    @Test
    fun `computeEndedAt MANUAL days scaling is fixed 24h chunks, so the wall clock shifts across a DST spring-forward`() {
        val newYork = ZoneId.of("America/New_York")
        // Noon on 2026-03-07, two days before the 2026-03-08 spring-forward (a 23-hour local day).
        val occurredAt =
            LocalDate
                .of(2026, 3, 7)
                .atTime(12, 0)
                .atZone(newYork)
                .toInstant()
                .toEpochMilli()

        val endedAt =
            computeEndedAt(occurredAt, DurationMode.MANUAL, "6", DurationUnit.DAYS, endedAt = null, existingEndedAt = null, now = farFuture)

        // The stored span is exactly six fixed 24-hour chunks of millis — no calendar-aware scaling.
        assertEquals(occurredAt + 6 * MILLIS_PER_DAY, endedAt)
        // The spring-forward skipped an hour of wall time inside that window, so six 24h chunks
        // land the wall clock at 13:00, not noon, on the 13th. By design (storage is millis) —
        // this test pins the distortion rather than changing it.
        assertEquals(
            LocalDateTime.of(2026, 3, 13, 13, 0),
            Instant.ofEpochMilli(endedAt!!).atZone(newYork).toLocalDateTime(),
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
    fun `toEventEntity truncates a note beyond the max length`() {
        val draft = testDraft(note = "a".repeat(EVENT_NOTE_MAX_LENGTH + 10))

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.NONE, now = farFuture)

        assertEquals(EVENT_NOTE_MAX_LENGTH, entity.note?.length)
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
    fun `toEventEntity computes endedAt from the duration amount and its unit`() {
        val draft = testDraft(durationAmount = "15", durationUnit = DurationUnit.MINUTES)

        val entity =
            draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.MANUAL, now = farFuture)

        assertEquals(15 * 60_000L, entity.endedAt)
    }

    @Test
    fun `toEventEntity scales the duration amount by a non-minutes unit`() {
        val draft = testDraft(durationAmount = "3", durationUnit = DurationUnit.DAYS)

        val entity =
            draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.MANUAL, now = farFuture)

        assertEquals(3 * MILLIS_PER_DAY, entity.endedAt)
    }

    @Test
    fun `toEventEntity preserves an existing duration when editing under NONE mode`() {
        val draft = testDraft(existingEndedAt = 55_000L)

        val underNone = draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, durationMode = DurationMode.NONE, now = farFuture)

        assertEquals(55_000L, underNone.endedAt)
    }

    @Test
    fun `toEventEntity is still ongoing for START_STOP mode when the draft's endedAt is null`() {
        val draft = testDraft(endedAt = null)

        val entity =
            draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, durationMode = DurationMode.START_STOP, now = farFuture)

        assertNull(entity.endedAt)
    }

    @Test
    fun `toEventEntity uses the draft's endedAt for START_STOP mode when set`() {
        val draft = testDraft(occurredAt = 1_000L, endedAt = 55_000L)

        val entity =
            draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, durationMode = DurationMode.START_STOP, now = farFuture)

        assertEquals(55_000L, entity.endedAt)
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
        val draft = testDraft(occurredAt = now + 60_000L, durationAmount = "30", durationUnit = DurationUnit.MINUTES)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, durationMode = DurationMode.MANUAL, now = now)

        assertEquals(now, entity.occurredAt)
        assertEquals(now + 30 * 60_000L, entity.endedAt)
    }

    @Test
    fun `toEventEntity allows a MANUAL duration to project endedAt past now`() {
        // Started right now; a stated/expected 2-hour duration is a legitimate thing to log
        // up front, not something that requires waiting around to confirm.
        val now = 10_000L
        val draft = testDraft(occurredAt = now, durationAmount = "120", durationUnit = DurationUnit.MINUTES)

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
    fun `planSaveEvent reopening a stopped event clears endedAt`() {
        val existing = testEvent(occurredAt = 500L, endedAt = 800L).copy(id = 42L)
        val draft = testDraft(occurredAt = 500L, endedAt = null, existingEndedAt = 800L)

        val plan =
            planSaveEvent(caseId = 1L, draft, existingEvent = existing, originalTags = emptyList(), DurationMode.START_STOP, now = 9_000L)

        assertNull(plan.entity.endedAt)
    }

    @Test
    fun `planSaveEvent editing a stopped event keeps the new endedAt`() {
        val existing = testEvent(occurredAt = 500L, endedAt = 800L).copy(id = 42L)
        val draft = testDraft(occurredAt = 500L, endedAt = 850L, existingEndedAt = 800L)

        val plan =
            planSaveEvent(caseId = 1L, draft, existingEvent = existing, originalTags = emptyList(), DurationMode.START_STOP, now = 9_000L)

        assertEquals(850L, plan.entity.endedAt)
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
        val draft = testDraft(occurredAt = 0L, durationAmount = "10", durationUnit = DurationUnit.MINUTES)

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

    @Test
    fun `tagDiff treats a selection differing only in casing from an original tag as unchanged`() {
        val coffee = TagEntity(id = 1, name = "coffee")

        val diff = tagDiff(originalTags = listOf(coffee), selectedNames = listOf("Coffee"))

        assertTrue(diff.toAdd.isEmpty())
        assertTrue(diff.toRemove.isEmpty())
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

        val formatted = formatEventTimeOfDay(instant.toEpochMilli(), use24Hour = false, zone = utc)

        assertTrue(formatted.contains("3:30"))
        assertTrue(formatted.contains("PM"))
    }

    @Test
    fun `formatEventTimeOfDay renders 24-hour time when use24Hour is set`() {
        val instant = ZonedDateTime.of(2026, 7, 9, 15, 30, 0, 0, utc).toInstant()

        val formatted = formatEventTimeOfDay(instant.toEpochMilli(), use24Hour = true, zone = utc)

        assertEquals("15:30", formatted)
    }

    private fun testDraft(
        occurredAt: Long = 0L,
        intensity: Int? = null,
        durationAmount: String = "",
        durationUnit: DurationUnit = DurationUnit.MINUTES,
        note: String = "",
        tags: List<String> = emptyList(),
        endedAt: Long? = null,
        existingEndedAt: Long? = null,
    ) = LogDraft(
        occurredAt = occurredAt,
        intensity = intensity,
        durationAmount = durationAmount,
        durationUnit = durationUnit,
        note = note,
        tags = tags,
        endedAt = endedAt,
        existingEndedAt = existingEndedAt,
    )
}
