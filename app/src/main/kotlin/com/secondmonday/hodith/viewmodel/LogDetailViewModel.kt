package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.offsetMinutesAt
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

internal const val EVENT_NOTE_MAX_LENGTH = 280

/**
 * Decomposes an elapsed duration into whole days/hours/minutes — no decimals anywhere: "1.5 days"
 * is just "1" day + "12" hours (spec §6 Story U2's Days/Hours/Minutes duration control).
 */
internal fun decomposeDuration(durationMillis: Long): Triple<Long, Long, Long> {
    val positive = durationMillis.coerceAtLeast(0L)
    val days = positive / MILLIS_PER_DAY
    val remainderAfterDays = positive % MILLIS_PER_DAY
    val hours = remainderAfterDays / MILLIS_PER_HOUR
    val minutes = (remainderAfterDays % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE
    return Triple(days, hours, minutes)
}

/** Inverse of [decomposeDuration]. */
internal fun composeDuration(
    days: Long,
    hours: Long,
    minutes: Long,
): Long = days * MILLIS_PER_DAY + hours * MILLIS_PER_HOUR + minutes * MILLIS_PER_MINUTE

/**
 * Draft state for [com.secondmonday.hodith.ui.logsheet.LogDetailSheet], the log detail sheet
 * shared by new-event, retro-log, and edit-event entry points (spec §6). Kept as a plain data
 * class (not a `@HiltViewModel`) since the sheet is opened from more than one nav destination
 * (Case Detail now, Home in a later slice) rather than owning its own back-stack entry — each
 * caller holds its own draft state and delegates the actual save to its own ViewModel, reusing
 * these pure functions so the logic itself isn't duplicated.
 */
data class LogDraft(
    val occurredAt: Long,
    val intensity: Int?,
    val note: String,
    val tags: List<String>,
    /**
     * Ongoing/ended state (spec §6 Story U2): `null` means open-ended (still ongoing, no stated
     * end); a stated instant may already be in the past (Ended) or still ahead of `now` (a
     * planned/projected duration, still Ongoing) — [isStillRunning][com.secondmonday.hodith.domain.isStillRunning]
     * is the single rule for telling those apart. Never auto-filled with a live-elapsed guess —
     * only [applyEndedAt]/[withDurationInput] (typing a value, picking an end date/time, or
     * tapping Stop) ever set it.
     */
    val endedAt: Long?,
    /** [endedAt] decomposed into Days/Hours/Minutes text fields, kept in sync with it by [applyEndedAt]/[withDurationInput] — blank together exactly when [endedAt] is null. */
    val daysInput: String,
    val hoursInput: String,
    val minutesInput: String,
)

internal data class TagDiff(
    val toAdd: Set<String>,
    val toRemove: Set<TagEntity>,
)

/** Builds a fresh draft: [event] null means "new event", non-null means "editing". */
internal fun draftFrom(
    event: EventEntity?,
    now: Long,
    tags: List<TagEntity> = emptyList(),
): LogDraft {
    if (event == null) {
        return LogDraft(
            occurredAt = now,
            intensity = null,
            note = "",
            tags = emptyList(),
            endedAt = null,
            daysInput = "",
            hoursInput = "",
            minutesInput = "",
        )
    }
    val fields = event.endedAt?.let { decomposeDuration(it - event.occurredAt) }
    return LogDraft(
        occurredAt = event.occurredAt,
        intensity = event.intensity,
        note = event.note.orEmpty(),
        tags = tags.map { it.name },
        endedAt = event.endedAt,
        daysInput = fields?.first?.toString().orEmpty(),
        hoursInput = fields?.second?.toString().orEmpty(),
        minutesInput = fields?.third?.toString().orEmpty(),
    )
}

/**
 * Commits [at] as the ongoing/ended instant, re-deriving the Days/Hours/Minutes fields from it —
 * `null` blanks all three (open-ended); a non-null instant decomposes the elapsed span since
 * [LogDraft.occurredAt]. The single place both the end date/time pickers and Stop/Start-again
 * funnel through, so the fields and [LogDraft.endedAt] can never drift apart.
 */
internal fun LogDraft.applyEndedAt(at: Long?): LogDraft =
    if (at == null) {
        copy(endedAt = null, daysInput = "", hoursInput = "", minutesInput = "")
    } else {
        val (days, hours, minutes) = decomposeDuration(at - occurredAt)
        copy(endedAt = at, daysInput = days.toString(), hoursInput = hours.toString(), minutesInput = minutes.toString())
    }

/** "Stop now" (spec §6 Story U2): commits the real elapsed duration as of [now], never before [LogDraft.occurredAt]. */
internal fun LogDraft.stoppedNow(now: Long): LogDraft = applyEndedAt(now.coerceAtLeast(occurredAt))

/** "Start again" (spec §6 Story U2): reopens an Ended draft back to open-ended. */
internal fun LogDraft.startedAgain(): LogDraft = applyEndedAt(null)

/**
 * Re-derives [LogDraft.endedAt] from the typed Days/Hours/Minutes fields (spec §6 Story U2) —
 * `null` (open-ended) when all three are blank, otherwise [LogDraft.occurredAt] plus
 * [composeDuration] of the three, each parsed permissively (a non-blank, non-numeric value reads
 * as 0 rather than rejecting the keystroke). This is how a planned/expected duration gets stated —
 * the resulting `endedAt` may land in the future, which keeps the draft Ongoing rather than
 * marking it Ended.
 */
internal fun LogDraft.withDurationInput(
    days: String = daysInput,
    hours: String = hoursInput,
    minutes: String = minutesInput,
): LogDraft {
    val endedAt =
        if (days.isBlank() && hours.isBlank() && minutes.isBlank()) {
            null
        } else {
            occurredAt + composeDuration(days.toLongOrNull() ?: 0L, hours.toLongOrNull() ?: 0L, minutes.toLongOrNull() ?: 0L)
        }
    return copy(daysInput = days, hoursInput = hours, minutesInput = minutes, endedAt = endedAt)
}

/**
 * [now] is a hard floor on [LogDraft.occurredAt] only, not just a UI nicety: an event can never be
 * persisted as having *started* in the future, regardless of what the date/time pickers allowed
 * through (defense in depth — the pickers also restrict this, but this is the authoritative
 * guard). [LogDraft.endedAt] is passed through exactly as the draft holds it — unlike `occurredAt`,
 * it's deliberately not clamped to `now`: a stated/expected duration ("started now, this kind of
 * thing runs about 2 hours") is allowed to project past `now`, same as an explicitly-stopped real
 * timestamp is always already `<= now` by construction ([LogDraft.stoppedNow]) or validated by the
 * end-time picker's own rejection notice before Save is even enabled.
 */
internal fun LogDraft.toEventEntity(
    caseId: Long,
    existingId: Long,
    loggedAt: Long,
    now: Long,
): EventEntity {
    val clampedOccurredAt = occurredAt.coerceAtMost(now)
    return EventEntity(
        id = existingId,
        caseId = caseId,
        occurredAt = clampedOccurredAt,
        endedAt = endedAt,
        intensity = intensity,
        note = note.trim().take(EVENT_NOTE_MAX_LENGTH).takeIf { it.isNotEmpty() },
        loggedAt = loggedAt,
        // Captured at the chosen occurredAt, not save-time now, so a retro-logged entry gets the
        // DST-correct historical offset for its actual date rather than today's.
        utcOffsetMinutes = TimeZone.currentSystemDefault().offsetMinutesAt(clampedOccurredAt),
    )
}

/**
 * Why a start/end field's picked value is invalid. The sheet's date/time pickers never clamp to
 * a nearby boundary, and no longer discard a rejected pick either — the field always shows
 * exactly what was picked, alongside this as an inline warning, with Save disabled until it's
 * resolved (either by picking again, or by editing the other field so the pair is valid together).
 */
internal enum class TimeEditRejection {
    /** The candidate is after `now` — nothing can be logged as happening in the future. */
    FUTURE,

    /** A start-field candidate lands after the current end time. */
    AFTER_END,

    /** An end-field candidate lands before the current start time. */
    BEFORE_START,
}

/**
 * Whether a start-field pick of [candidate] should be applied, given the current [endedAt]
 * (null while still ongoing) and [now]. Checked in this order since a future candidate is
 * rejected on that basis regardless of where the end time sits.
 */
internal fun validateStartEdit(
    candidate: Long,
    endedAt: Long?,
    now: Long,
): TimeEditRejection? =
    when {
        candidate > now -> TimeEditRejection.FUTURE
        endedAt != null && candidate > endedAt -> TimeEditRejection.AFTER_END
        else -> null
    }

/** Whether an end-field pick of [candidate] should be applied, given the current [occurredAt]. Unlike [validateStartEdit], a future candidate is valid — it's how a planned/expected duration gets stated (spec §6 Story U2). */
internal fun validateEndEdit(
    candidate: Long,
    occurredAt: Long,
): TimeEditRejection? = if (candidate < occurredAt) TimeEditRejection.BEFORE_START else null

/**
 * Applies a date picked from Material3's `DatePicker` (whose `selectedDateMillis` is always
 * UTC-midnight of the chosen date, regardless of device zone) to [occurredAt], keeping its
 * existing time-of-day in [zone].
 */
internal fun applyPickedDate(
    occurredAt: Long,
    pickedDateUtcMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val currentTime = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone).time
    val pickedDate = Instant.fromEpochMilliseconds(pickedDateUtcMillis).toLocalDateTime(TimeZone.UTC).date
    return LocalDateTime(pickedDate, currentTime).toInstant(zone).toEpochMilliseconds()
}

/** Applies a picked hour/minute to [occurredAt], keeping its existing date in [zone]. */
internal fun applyPickedTime(
    occurredAt: Long,
    hour: Int,
    minute: Int,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long {
    val currentDate = Instant.fromEpochMilliseconds(occurredAt).toLocalDateTime(zone).date
    return currentDate.atTime(hour, minute).toInstant(zone).toEpochMilliseconds()
}

/**
 * The UTC-midnight millis Material3's `DatePicker` expects for the calendar date [localMillis]
 * falls on in [zone] — the input direction for `initialSelectedDateMillis` and `SelectableDates`
 * bounds, both of which the picker reads in UTC. The inverse of [applyPickedDate] /
 * [datePickerDateAtLocalStartOfDay].
 */
internal fun toDatePickerUtcMillis(
    localMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long =
    Instant
        .fromEpochMilliseconds(localMillis)
        .toLocalDateTime(zone)
        .date
        .atStartOfDayIn(TimeZone.UTC)
        .toEpochMilliseconds()

/**
 * The local start-of-day millis in [zone] for the date picked in Material3's `DatePicker` (a bare
 * calendar date at UTC-midnight). Unlike [applyPickedDate] this carries no time-of-day — for a
 * value that is a date, not a datetime.
 */
internal fun datePickerDateAtLocalStartOfDay(
    pickedDateUtcMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
): Long =
    Instant
        .fromEpochMilliseconds(pickedDateUtcMillis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .atStartOfDayIn(zone)
        .toEpochMilliseconds()

/**
 * Which tags to add/remove so [originalTags] ends up matching [selectedNames]. Matching is
 * case-insensitive (mirrors Case Edit's own duplicate-name check and `ui.logsheet.tagToAdd`), so
 * a selection of "Coffee" against an original tag named "coffee" is a no-op, not an add+remove.
 */
internal fun tagDiff(
    originalTags: List<TagEntity>,
    selectedNames: List<String>,
): TagDiff {
    val selected = selectedNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val selectedLower = selected.map { it.lowercase() }.toSet()
    val originalLowerNames = originalTags.map { it.name.lowercase() }.toSet()
    val toAdd = selected.filter { it.lowercase() !in originalLowerNames }.toSet()
    val toRemove = originalTags.filter { it.name.lowercase() !in selectedLower }.toSet()
    return TagDiff(toAdd = toAdd, toRemove = toRemove)
}

/**
 * What saving [draft] should do: the [EventEntity] to persist, whether that's an insert or an
 * update, and the tag changes to apply. [CaseDetailViewModel.saveEvent] just executes this
 * plan — every actual decision (which `loggedAt` to keep, insert vs. update, what changed in the
 * tags) lives here instead, so it's unit-testable without a repository or Hilt.
 */
internal data class SaveEventPlan(
    val entity: EventEntity,
    val isUpdate: Boolean,
    val tagDiff: TagDiff,
)

internal fun planSaveEvent(
    caseId: Long,
    draft: LogDraft,
    existingEvent: EventEntity?,
    originalTags: List<TagEntity>,
    now: Long,
): SaveEventPlan {
    val loggedAt = existingEvent?.loggedAt ?: now
    val entity =
        draft.toEventEntity(
            caseId = caseId,
            existingId = existingEvent?.id ?: 0L,
            loggedAt = loggedAt,
            now = now,
        )
    return SaveEventPlan(
        entity = entity,
        isUpdate = existingEvent != null,
        tagDiff = tagDiff(originalTags, draft.tags),
    )
}
