package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.NotificationKind
import com.secondmonday.hodith.data.backup.BackupData
import com.secondmonday.hodith.ui.logsheet.TAG_NAME_MAX_LENGTH

/** Real-world UTC offsets run from UTC-12:00 to UTC+14:00 — a little headroom past the extremes. */
internal val VALID_UTC_OFFSET_MINUTES_RANGE = -720..840

/**
 * Pure, so it's unit-testable on the JVM without a repository or Hilt — same pattern as
 * [CaseEditValidation]. Uses the same length/range constants the in-app editors enforce while
 * typing. [violations] is diagnostic detail; the UI shows one generic message regardless of
 * which rule failed (spec §16).
 */
data class BackupValidationResult(
    val isValid: Boolean,
    val violations: List<String>,
)

/**
 * Checks every field rule and every cross-entity reference, collecting the full violation list
 * rather than stopping at the first problem. [com.secondmonday.hodith.data.RoomHodithRepository.importBackupData]
 * expects this to have already run.
 */
fun validateBackup(backup: BackupData): BackupValidationResult {
    val violations = mutableListOf<String>()
    val caseIds = backup.cases.map { it.id }.toSet()
    val eventIds = backup.events.map { it.id }.toSet()
    val tagIds = backup.tags.map { it.id }.toSet()

    // Two rows sharing a non-zero id in the same list crash on insert (PK conflict), same as a
    // dangling reference or a duplicate tag name. Id 0 is exempt: Room autogenerates a fresh id
    // for it.
    duplicateNonZeroIds(backup.cases) { it.id }.forEach { violations += "Case: duplicate id $it" }
    duplicateNonZeroIds(backup.events) { it.id }.forEach { violations += "Event: duplicate id $it" }
    duplicateNonZeroIds(backup.tags) { it.id }.forEach { violations += "Tag: duplicate id $it" }
    duplicateNonZeroIds(backup.notifications) { it.id }.forEach { violations += "Notification: duplicate id $it" }

    backup.cases.forEach { case ->
        if (case.name.isBlank()) violations += "Case ${case.id}: blank name"
        if (case.name.length > CASE_NAME_MAX_LENGTH) violations += "Case ${case.id}: name exceeds $CASE_NAME_MAX_LENGTH chars"
        if (case.icon.isBlank()) violations += "Case ${case.id}: blank icon"
        val description = case.description
        if (description != null && description.length > CASE_DESCRIPTION_MAX_LENGTH) {
            violations += "Case ${case.id}: description exceeds $CASE_DESCRIPTION_MAX_LENGTH chars"
        }
    }

    val seenTagNames = mutableSetOf<String>()
    backup.tags.forEach { tag ->
        if (tag.name.isBlank()) violations += "Tag ${tag.id}: blank name"
        if (tag.name.length > TAG_NAME_MAX_LENGTH) violations += "Tag ${tag.id}: name exceeds $TAG_NAME_MAX_LENGTH chars"
        if (!seenTagNames.add(tag.name)) violations += "Tag ${tag.id}: duplicate name '${tag.name}'"
    }

    backup.events.forEach { event ->
        if (event.caseId !in caseIds) violations += "Event ${event.id}: caseId ${event.caseId} not present in backup"
        val note = event.note
        if (note != null && note.length > EVENT_NOTE_MAX_LENGTH) {
            violations += "Event ${event.id}: note exceeds $EVENT_NOTE_MAX_LENGTH chars"
        }
        if (event.utcOffsetMinutes !in VALID_UTC_OFFSET_MINUTES_RANGE) {
            violations += "Event ${event.id}: utcOffsetMinutes out of range"
        }
    }

    backup.eventTags.forEach { crossRef ->
        if (crossRef.eventId !in eventIds) violations += "EventTag: eventId ${crossRef.eventId} not present in backup"
        if (crossRef.tagId !in tagIds) violations += "EventTag: tagId ${crossRef.tagId} not present in backup"
    }

    backup.notifications.forEach { notification ->
        if (notification.caseId !in caseIds) {
            violations += "Notification ${notification.id}: caseId ${notification.caseId} not present in backup"
        }
        if (notification.threshold !in THRESHOLD_RANGE) violations += "Notification ${notification.id}: threshold out of range"
        when (notification.kind) {
            NotificationKind.OFTEN ->
                if (notification.windowDays == null || notification.windowDays <= 0) {
                    violations += "Notification ${notification.id}: OFTEN requires a positive windowDays"
                }
            NotificationKind.QUIET ->
                if (notification.windowDays != null) {
                    violations += "Notification ${notification.id}: QUIET must not set windowDays"
                }
        }
    }

    return BackupValidationResult(isValid = violations.isEmpty(), violations = violations)
}

private fun <T> duplicateNonZeroIds(
    entities: List<T>,
    idOf: (T) -> Long,
): Set<Long> {
    val seen = mutableSetOf<Long>()
    val duplicates = mutableSetOf<Long>()
    entities.map(idOf).filter { it != 0L }.forEach { id -> if (!seen.add(id)) duplicates += id }
    return duplicates
}
