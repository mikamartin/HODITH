package com.secondmonday.hodith.data

/**
 * The minimal per-event projection Home and the widgets need to count a Case's today / this-week
 * events (spec §9/§14) — `caseId`, the timing fields, and the owning Case's `durationMode` so the
 * active-span rule (`com.secondmonday.hodith.domain.activeSpanEnd`) can be applied per row.
 *
 * Deliberately not the full [EventEntity] / `CaseWithEvents` `@Relation` graph: those hydrate every
 * column and run a sub-query per Case, which — refetched on every insert by Room's table-level
 * invalidation — is the cost this projection removes. One flat `events JOIN cases` instead.
 */
data class CaseEventSpan(
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long?,
    val durationMode: DurationMode,
)
