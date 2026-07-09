package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.TagEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

private const val MILLIS_PER_MINUTE = 60_000L

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
    val durationMinutes: String,
    val note: String,
    val tags: List<String>,
    val existingEndedAt: Long?,
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
            durationMinutes = "",
            note = "",
            tags = emptyList(),
            existingEndedAt = null,
        )
    }
    val durationMinutes =
        event.endedAt
            ?.let { (it - event.occurredAt) / MILLIS_PER_MINUTE }
            ?.toString()
            .orEmpty()
    return LogDraft(
        occurredAt = event.occurredAt,
        intensity = event.intensity,
        durationMinutes = durationMinutes,
        note = event.note.orEmpty(),
        tags = tags.map { it.name },
        existingEndedAt = event.endedAt,
    )
}

/**
 * Parses the duration field into an `endedAt`. MANUAL is the only mode with an editable
 * duration control in the sheet so far (Start/Stop's own control lands in a later slice) — for
 * every other mode, [existingEndedAt] is passed through unchanged rather than nulled out, so
 * editing an event's note/tags can never silently destroy a real duration it already has (e.g.
 * one logged while the case was still in `START_STOP` mode, before its mode was changed).
 */
internal fun computeEndedAt(
    occurredAt: Long,
    durationMode: DurationMode,
    durationMinutesInput: String,
    existingEndedAt: Long?,
): Long? =
    when (durationMode) {
        DurationMode.MANUAL ->
            durationMinutesInput.toIntOrNull()?.takeIf { it > 0 }?.let { occurredAt + it * MILLIS_PER_MINUTE }
        DurationMode.NONE, DurationMode.START_STOP -> existingEndedAt
    }

/**
 * [now] is a hard floor on [occurredAt] only, not just a UI nicety: an event can never be
 * persisted as having *started* in the future, regardless of what the date/time pickers allowed
 * through (defense in depth — the pickers also restrict this, but this is the authoritative
 * guard). `endedAt` is deliberately NOT clamped: a MANUAL duration entered at logging time is
 * often a stated/expected length ("started now, this kind of thing runs about 2 hours") rather
 * than a fact only knowable in hindsight, so it's allowed to project past `now`.
 */
internal fun LogDraft.toEventEntity(
    caseId: Long,
    existingId: Long,
    loggedAt: Long,
    durationMode: DurationMode,
    now: Long,
): EventEntity {
    val clampedOccurredAt = occurredAt.coerceAtMost(now)
    return EventEntity(
        id = existingId,
        caseId = caseId,
        occurredAt = clampedOccurredAt,
        endedAt = computeEndedAt(clampedOccurredAt, durationMode, durationMinutes, existingEndedAt),
        intensity = intensity,
        note = note.trim().takeIf { it.isNotEmpty() },
        loggedAt = loggedAt,
    )
}

/**
 * Applies a date picked from Material3's `DatePicker` (whose `selectedDateMillis` is always
 * UTC-midnight of the chosen date, regardless of device zone) to [occurredAt], keeping its
 * existing time-of-day in [zone].
 */
internal fun applyPickedDate(
    occurredAt: Long,
    pickedDateUtcMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val currentTime = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
    val pickedDate = Instant.ofEpochMilli(pickedDateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return pickedDate
        .atTime(currentTime)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

/** Applies a picked hour/minute to [occurredAt], keeping its existing date in [zone]. */
internal fun applyPickedTime(
    occurredAt: Long,
    hour: Int,
    minute: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Long {
    val currentDate = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalDate()
    return currentDate
        .atTime(hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

/** Which tags to add/remove so [originalTags] ends up matching [selectedNames]. */
internal fun tagDiff(
    originalTags: List<TagEntity>,
    selectedNames: List<String>,
): TagDiff {
    val selected = selectedNames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    val originalByName = originalTags.associateBy { it.name }
    val toAdd = selected - originalByName.keys
    val toRemove = originalByName.filterKeys { it !in selected }.values.toSet()
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
    durationMode: DurationMode,
    now: Long,
): SaveEventPlan {
    val loggedAt = existingEvent?.loggedAt ?: now
    val entity =
        draft.toEventEntity(
            caseId = caseId,
            existingId = existingEvent?.id ?: 0L,
            loggedAt = loggedAt,
            durationMode = durationMode,
            now = now,
        )
    return SaveEventPlan(
        entity = entity,
        isUpdate = existingEvent != null,
        tagDiff = tagDiff(originalTags, draft.tags),
    )
}
