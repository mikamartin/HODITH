package com.secondmonday.hodith.data

/**
 * The flat per-event projection the Big Picture grid needs (spec §9) — `id`, `caseId`, timing,
 * and the two extra fields the day/week detail rows show (`intensity`, `note`), for every active
 * Case's events. Case-level gating — whether `endedAt`/`intensity` actually render, per the
 * owning Case's `durationMode`/`intensityEnabled` — is applied in `bigPictureUiState` from the
 * same active-case list the mapper already holds for its `CalendarCase`/`earliestMonth` output,
 * rather than duplicated onto this row the way [CaseEventSpan] carries its
 * own `durationMode`: Big Picture's mapper groups by `caseId` against a case list it already has,
 * so joining the gating fields in here as well would just be the same value fetched twice.
 *
 * Deliberately not the full [EventEntity] / `CaseWithEventsAndTags` `@Relation` graph: that
 * hydrates every column, chunks through an `IN (...)` per Case, and nests tags per event — all
 * refetched on every write by Room's table-level invalidation. One flat `events JOIN cases`
 * instead, same rationale as [CaseEventSpan].
 */
data class CaseEventDetail(
    val id: Long,
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long?,
    val intensity: Int?,
    val note: String?,
)
