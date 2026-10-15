package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
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

    // Far beyond any occurredAt used below, so it never clamps unless a test is specifically
    // exercising the clamp.
    private val farFuture = 1_000_000_000_000L

    // --- decomposeDuration / composeDuration ---

    @Test
    fun `decomposeDuration returns all zeros for a zero span`() {
        assertEquals(Triple(0L, 0L, 0L), decomposeDuration(0L))
    }

    @Test
    fun `decomposeDuration clamps a negative span to zero`() {
        assertEquals(Triple(0L, 0L, 0L), decomposeDuration(-MILLIS_PER_HOUR))
    }

    @Test
    fun `decomposeDuration splits a mixed span into whole days, hours and minutes`() {
        val span = 2 * MILLIS_PER_DAY + 3 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE

        assertEquals(Triple(2L, 3L, 15L), decomposeDuration(span))
    }

    @Test
    fun `decomposeDuration drops seconds-level remainder rather than rounding`() {
        val span = 90 * MILLIS_PER_MINUTE + 30_000L

        assertEquals(Triple(0L, 1L, 30L), decomposeDuration(span))
    }

    @Test
    fun `composeDuration is the inverse of decomposeDuration`() {
        val span = 5 * MILLIS_PER_DAY + 7 * MILLIS_PER_HOUR + 42 * MILLIS_PER_MINUTE
        val (days, hours, minutes) = decomposeDuration(span)

        assertEquals(span, composeDuration(days, hours, minutes))
    }

    @Test
    fun `composeDuration with all zeros is zero`() {
        assertEquals(0L, composeDuration(0L, 0L, 0L))
    }

    @Test
    fun `composeDuration days scaling is fixed 24h chunks, so the wall clock shifts across a DST spring-forward`() {
        val newYork = ZoneId.of("America/New_York")
        // Noon on 2026-03-07, two days before the 2026-03-08 spring-forward (a 23-hour local day).
        val occurredAt =
            LocalDate
                .of(2026, 3, 7)
                .atTime(12, 0)
                .atZone(newYork)
                .toInstant()
                .toEpochMilli()

        val endedAt = occurredAt + composeDuration(6L, 0L, 0L)

        // The stored span is exactly six fixed 24-hour chunks of millis — no calendar-aware scaling.
        assertEquals(occurredAt + 6 * MILLIS_PER_DAY, endedAt)
        // The spring-forward skipped an hour of wall time inside that window, so six 24h chunks
        // land the wall clock at 13:00, not noon, on the 13th. By design (storage is millis) —
        // this test pins the distortion rather than changing it.
        assertEquals(
            LocalDateTime.of(2026, 3, 13, 13, 0),
            Instant.ofEpochMilli(endedAt).atZone(newYork).toLocalDateTime(),
        )
    }

    // --- draftFrom ---

    @Test
    fun `draftFrom with no event defaults to now with everything empty`() {
        val draft = draftFrom(event = null, now = 1_000L)

        assertEquals(1_000L, draft.occurredAt)
        assertNull(draft.intensity)
        assertNull(draft.endedAt)
        assertEquals("", draft.daysInput)
        assertEquals("", draft.hoursInput)
        assertEquals("", draft.minutesInput)
        assertEquals("", draft.note)
        assertTrue(draft.tags.isEmpty())
    }

    @Test
    fun `draftFrom an open-ended event leaves endedAt null and the duration fields blank`() {
        val event = testEvent(occurredAt = 5_000L, endedAt = null)

        val draft = draftFrom(event, now = 99_999L)

        assertNull(draft.endedAt)
        assertEquals("", draft.daysInput)
        assertEquals("", draft.hoursInput)
        assertEquals("", draft.minutesInput)
    }

    @Test
    fun `draftFrom a finished event decomposes the stored span into days, hours and minutes`() {
        val event = testEvent(occurredAt = 0L, endedAt = 2 * MILLIS_PER_DAY + 3 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals(2 * MILLIS_PER_DAY + 3 * MILLIS_PER_HOUR + 15 * MILLIS_PER_MINUTE, draft.endedAt)
        assertEquals("2", draft.daysInput)
        assertEquals("3", draft.hoursInput)
        assertEquals("15", draft.minutesInput)
    }

    @Test
    fun `draftFrom a zero-length event decomposes to all zeros, not blank`() {
        val event = testEvent(occurredAt = 10_000L, endedAt = 10_000L)

        val draft = draftFrom(event, now = 99_999L)

        assertEquals(10_000L, draft.endedAt)
        assertEquals("0", draft.daysInput)
        assertEquals("0", draft.hoursInput)
        assertEquals("0", draft.minutesInput)
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

    // --- LogDraft#applyEndedAt / stoppedNow / startedAgain ---

    @Test
    fun `applyEndedAt with null clears endedAt and blanks every duration field`() {
        val draft = testDraft(occurredAt = 0L, endedAt = 500L, daysInput = "0", hoursInput = "0", minutesInput = "8")

        val result = draft.applyEndedAt(null)

        assertNull(result.endedAt)
        assertEquals("", result.daysInput)
        assertEquals("", result.hoursInput)
        assertEquals("", result.minutesInput)
    }

    @Test
    fun `applyEndedAt with an instant decomposes the elapsed span since occurredAt`() {
        val draft = testDraft(occurredAt = 1_000L)

        val result = draft.applyEndedAt(1_000L + 90 * MILLIS_PER_MINUTE)

        assertEquals(1_000L + 90 * MILLIS_PER_MINUTE, result.endedAt)
        assertEquals("0", result.daysInput)
        assertEquals("1", result.hoursInput)
        assertEquals("30", result.minutesInput)
    }

    @Test
    fun `stoppedNow commits now as endedAt when now is after occurredAt`() {
        val draft = testDraft(occurredAt = 1_000L)

        val result = draft.stoppedNow(now = 9_000L)

        assertEquals(9_000L, result.endedAt)
    }

    @Test
    fun `stoppedNow never commits an endedAt before occurredAt`() {
        val draft = testDraft(occurredAt = 9_000L)

        val result = draft.stoppedNow(now = 1_000L)

        assertEquals(9_000L, result.endedAt)
    }

    @Test
    fun `startedAgain reopens an ended draft back to open-ended`() {
        val draft = testDraft(occurredAt = 0L, endedAt = 500L, daysInput = "0", hoursInput = "0", minutesInput = "8")

        val result = draft.startedAgain()

        assertNull(result.endedAt)
        assertEquals("", result.daysInput)
        assertEquals("", result.hoursInput)
        assertEquals("", result.minutesInput)
    }

    // --- LogDraft#withDurationInput ---

    @Test
    fun `withDurationInput with every field blank clears endedAt`() {
        val draft = testDraft(occurredAt = 1_000L, endedAt = 500L)

        val result = draft.withDurationInput(days = "", hours = "", minutes = "")

        assertNull(result.endedAt)
    }

    @Test
    fun `withDurationInput composes endedAt from the typed days, hours and minutes`() {
        val draft = testDraft(occurredAt = 1_000L)

        val result = draft.withDurationInput(days = "1", hours = "2", minutes = "30")

        assertEquals(1_000L + composeDuration(1L, 2L, 30L), result.endedAt)
    }

    @Test
    fun `withDurationInput treats a non-numeric field as zero rather than rejecting it`() {
        val draft = testDraft(occurredAt = 1_000L)

        val result = draft.withDurationInput(days = "", hours = "abc", minutes = "15")

        assertEquals(1_000L + 15 * MILLIS_PER_MINUTE, result.endedAt)
    }

    @Test
    fun `withDurationInput allows the composed endedAt to project past now`() {
        // A stated/expected duration is a legitimate thing to log up front (spec section 6 Story
        // U2) -- withDurationInput itself has no now parameter and never clamps against one.
        val draft = testDraft(occurredAt = 1_000L)

        val result = draft.withDurationInput(days = "0", hours = "0", minutes = "120")

        assertEquals(1_000L + 120 * MILLIS_PER_MINUTE, result.endedAt)
    }

    @Test
    fun `withDurationInput keeps the typed field text even when a field is non-numeric`() {
        val draft = testDraft(occurredAt = 1_000L)

        val result = draft.withDurationInput(days = "", hours = "abc", minutes = "15")

        assertEquals("", result.daysInput)
        assertEquals("abc", result.hoursInput)
        assertEquals("15", result.minutesInput)
    }

    // --- LogDraft#toEventEntity ---

    @Test
    fun `toEventEntity trims blank note to null`() {
        val draft = testDraft(note = "   ")

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, now = farFuture)

        assertNull(entity.note)
    }

    @Test
    fun `toEventEntity trims surrounding whitespace from a non-blank note`() {
        val draft = testDraft(note = "  Ouch  ")

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, now = farFuture)

        assertEquals("Ouch", entity.note)
    }

    @Test
    fun `toEventEntity truncates a note beyond the max length`() {
        val draft = testDraft(note = "a".repeat(EVENT_NOTE_MAX_LENGTH + 10))

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, now = farFuture)

        assertEquals(EVENT_NOTE_MAX_LENGTH, entity.note?.length)
    }

    @Test
    fun `toEventEntity carries caseId, existingId, loggedAt and intensity through unchanged`() {
        val draft = testDraft(occurredAt = 42L, intensity = 2)

        val entity = draft.toEventEntity(caseId = 7L, existingId = 99L, loggedAt = 123L, now = farFuture)

        assertEquals(7L, entity.caseId)
        assertEquals(99L, entity.id)
        assertEquals(123L, entity.loggedAt)
        assertEquals(42L, entity.occurredAt)
        assertEquals(2, entity.intensity)
    }

    @Test
    fun `toEventEntity passes a null endedAt through as still open-ended`() {
        val draft = testDraft(endedAt = null)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, now = farFuture)

        assertNull(entity.endedAt)
    }

    @Test
    fun `toEventEntity passes a stated endedAt through verbatim`() {
        val draft = testDraft(occurredAt = 1_000L, endedAt = 55_000L)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 5L, loggedAt = 0L, now = farFuture)

        assertEquals(55_000L, entity.endedAt)
    }

    @Test
    fun `toEventEntity clamps a future occurredAt to now`() {
        val now = 10_000L
        val draft = testDraft(occurredAt = now + 60_000L)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, now = now)

        assertEquals(now, entity.occurredAt)
    }

    @Test
    fun `toEventEntity does not clamp a past or present occurredAt`() {
        val now = 10_000L
        val draft = testDraft(occurredAt = now - 60_000L)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, now = now)

        assertEquals(now - 60_000L, entity.occurredAt)
    }

    @Test
    fun `toEventEntity never clamps endedAt even when it projects past now`() {
        val now = 10_000L
        val draft = testDraft(occurredAt = now, endedAt = now + 2 * MILLIS_PER_HOUR)

        val entity = draft.toEventEntity(caseId = 1L, existingId = 0L, loggedAt = 0L, now = now)

        assertEquals(now + 2 * MILLIS_PER_HOUR, entity.endedAt)
    }

    // --- planSaveEvent ---

    @Test
    fun `planSaveEvent for a new event is an insert with loggedAt set to now`() {
        val draft = testDraft(occurredAt = 500L)

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = null, originalTags = emptyList(), now = 1_000L)

        assertTrue(!plan.isUpdate)
        assertEquals(1_000L, plan.entity.loggedAt)
        assertEquals(1L, plan.entity.caseId)
    }

    @Test
    fun `planSaveEvent for an existing event is an update that preserves the original loggedAt`() {
        val existing = testEvent(occurredAt = 500L, endedAt = null).copy(id = 42L, loggedAt = 111L)
        val draft = testDraft(occurredAt = 500L)

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = existing, originalTags = emptyList(), now = 1_000L)

        assertTrue(plan.isUpdate)
        assertEquals(111L, plan.entity.loggedAt)
        assertEquals(42L, plan.entity.id)
    }

    @Test
    fun `planSaveEvent reopening a stopped event clears endedAt`() {
        val existing = testEvent(occurredAt = 500L, endedAt = 800L).copy(id = 42L)
        val draft = testDraft(occurredAt = 500L, endedAt = null)

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = existing, originalTags = emptyList(), now = 9_000L)

        assertNull(plan.entity.endedAt)
    }

    @Test
    fun `planSaveEvent editing a stopped event keeps the new endedAt`() {
        val existing = testEvent(occurredAt = 500L, endedAt = 800L).copy(id = 42L)
        val draft = testDraft(occurredAt = 500L, endedAt = 850L)

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = existing, originalTags = emptyList(), now = 9_000L)

        assertEquals(850L, plan.entity.endedAt)
    }

    @Test
    fun `planSaveEvent computes the tag diff between originalTags and the draft's tags`() {
        val work = TagEntity(id = 1, name = "work")
        val draft = testDraft(tags = listOf("home"))

        val plan = planSaveEvent(caseId = 1L, draft, existingEvent = null, originalTags = listOf(work), now = 0L)

        assertEquals(setOf("home"), plan.tagDiff.toAdd)
        assertEquals(setOf(work), plan.tagDiff.toRemove)
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

    // --- validateStartEdit / validateEndEdit ---

    @Test
    fun `validateStartEdit rejects a future candidate regardless of the end time`() {
        assertEquals(TimeEditRejection.FUTURE, validateStartEdit(candidate = 10_001L, endedAt = null, now = 10_000L))
        // Also after the end time, but the future violation takes priority.
        assertEquals(TimeEditRejection.FUTURE, validateStartEdit(candidate = 10_001L, endedAt = 5_000L, now = 10_000L))
    }

    @Test
    fun `validateStartEdit rejects a non-future candidate after the end time`() {
        assertEquals(TimeEditRejection.AFTER_END, validateStartEdit(candidate = 5_001L, endedAt = 5_000L, now = 10_000L))
    }

    @Test
    fun `validateStartEdit accepts a candidate at or before now and at or before the end time`() {
        assertNull(validateStartEdit(candidate = 10_000L, endedAt = null, now = 10_000L))
        assertNull(validateStartEdit(candidate = 9_999L, endedAt = null, now = 10_000L))
        assertNull(validateStartEdit(candidate = 5_000L, endedAt = 5_000L, now = 10_000L))
        assertNull(validateStartEdit(candidate = 4_999L, endedAt = 5_000L, now = 10_000L))
    }

    @Test
    fun `validateEndEdit rejects a candidate before the start time`() {
        assertEquals(TimeEditRejection.BEFORE_START, validateEndEdit(candidate = 4_999L, occurredAt = 5_000L))
    }

    @Test
    fun `validateEndEdit accepts a candidate at or after the start time, even projecting past now`() {
        // A future end time is how a planned/expected duration gets stated (spec section 6 Story
        // U2) -- validateEndEdit takes no now parameter and never rejects on that basis.
        assertNull(validateEndEdit(candidate = 5_000L, occurredAt = 5_000L))
        assertNull(validateEndEdit(candidate = 5_001L, occurredAt = 5_000L))
        assertNull(validateEndEdit(candidate = 1_000_000_000L, occurredAt = 5_000L))
    }

    // --- toDatePickerUtcMillis / datePickerDateAtLocalStartOfDay ---

    @Test
    fun `toDatePickerUtcMillis maps a local instant to UTC-midnight of its calendar date`() {
        // 2026-07-09 23:30 in New York is still the 9th locally; the picker wants 2026-07-09 00:00 UTC.
        val newYork = ZoneId.of("America/New_York")
        val localMillis = ZonedDateTime.of(2026, 7, 9, 23, 30, 0, 0, newYork).toInstant().toEpochMilli()

        val result = toDatePickerUtcMillis(localMillis, newYork)

        assertEquals(ZonedDateTime.of(2026, 7, 9, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), result)
    }

    @Test
    fun `datePickerDateAtLocalStartOfDay maps a picked UTC-midnight date to local start-of-day`() {
        val newYork = ZoneId.of("America/New_York")
        val pickedUtcMillis = ZonedDateTime.of(2026, 7, 9, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        val result = datePickerDateAtLocalStartOfDay(pickedUtcMillis, newYork)

        val resultZoned = Instant.ofEpochMilli(result).atZone(newYork)
        assertEquals(9, resultZoned.dayOfMonth)
        assertEquals(0, resultZoned.hour)
        assertEquals(0, resultZoned.minute)
    }

    @Test
    fun `the two picker helpers round-trip a date`() {
        val zone = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 3, 15, 0, 0, 0, 0, zone).toInstant().toEpochMilli()

        val roundTripped = datePickerDateAtLocalStartOfDay(toDatePickerUtcMillis(start, zone), zone)

        assertEquals(start, roundTripped)
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
        note: String = "",
        tags: List<String> = emptyList(),
        endedAt: Long? = null,
        daysInput: String = "",
        hoursInput: String = "",
        minutesInput: String = "",
    ) = LogDraft(
        occurredAt = occurredAt,
        intensity = intensity,
        note = note,
        tags = tags,
        endedAt = endedAt,
        daysInput = daysInput,
        hoursInput = hoursInput,
        minutesInput = minutesInput,
    )
}
