package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.data.backup.BackupData
import com.secondmonday.hodith.ui.casedetail.EXPECTED_COUNT_RANGE
import com.secondmonday.hodith.ui.logsheet.TAG_NAME_MAX_LENGTH
import com.secondmonday.hodith.ui.triggers.THRESHOLD_RANGE

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
    duplicateNonZeroIds(backup.hunches) { it.id }.forEach { violations += "Hunch: duplicate id $it" }
    duplicateNonZeroIds(backup.triggers) { it.id }.forEach { violations += "Trigger: duplicate id $it" }

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
    }

    backup.eventTags.forEach { crossRef ->
        if (crossRef.eventId !in eventIds) violations += "EventTag: eventId ${crossRef.eventId} not present in backup"
        if (crossRef.tagId !in tagIds) violations += "EventTag: tagId ${crossRef.tagId} not present in backup"
    }

    backup.hunches.forEach { hunch ->
        if (hunch.caseId !in caseIds) violations += "Hunch ${hunch.id}: caseId ${hunch.caseId} not present in backup"
        if (hunch.expectedCount !in EXPECTED_COUNT_RANGE) violations += "Hunch ${hunch.id}: expectedCount out of range"
    }

    backup.triggers.forEach { trigger ->
        if (trigger.caseId !in caseIds) violations += "Trigger ${trigger.id}: caseId ${trigger.caseId} not present in backup"
        if (trigger.threshold !in THRESHOLD_RANGE) violations += "Trigger ${trigger.id}: threshold out of range"
        when (trigger.kind) {
            TriggerKind.AT_LEAST ->
                if (trigger.windowDays == null || trigger.windowDays <= 0) {
                    violations += "Trigger ${trigger.id}: AT_LEAST requires a positive windowDays"
                }
            TriggerKind.SILENT_FOR ->
                if (trigger.windowDays != null) {
                    violations += "Trigger ${trigger.id}: SILENT_FOR must not set windowDays"
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
