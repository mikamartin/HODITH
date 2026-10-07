package com.secondmonday.hodith.data

import com.secondmonday.hodith.data.backup.BackupData
import kotlinx.coroutines.flow.Flow

interface HodithRepository {
    // Case
    fun observeActiveCases(): Flow<List<CaseEntity>>

    suspend fun getActiveCases(): List<CaseEntity>

    fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>>

    /** Count of archived Cases, for Home's archived-cases link (spec §14). */
    fun observeArchivedCaseCount(): Flow<Int>

    fun observeCase(caseId: Long): Flow<CaseEntity?>

    suspend fun getCase(caseId: Long): CaseEntity?

    suspend fun insertCase(case: CaseEntity): Long

    suspend fun updateCase(case: CaseEntity)

    suspend fun deleteCase(case: CaseEntity)

    suspend fun deleteAllArchivedCases()

    suspend fun deleteAllData()

    /** Deletes every Event with `occurredAt` strictly before [cutoff]; `event_tags` cascades, `tags` stay untouched (spec §14). */
    suspend fun deleteEventsOlderThan(cutoff: Long)

    // Event
    fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>>

    /**
     * Capped, sorted page of a Case's events for the History tab's row list only (spec §6) — at
     * most [limit] events in [order], plus whether more remain. Every other History-tab-adjacent
     * need (ongoing-event detection, Insights stats, the History tab's own summary line/day-span)
     * keeps reading the full history via [observeEventsWithTagsForCase]; this is additive, not a
     * replacement. [dateFrom]/[dateTo] narrow to `occurredAt` inclusively (either `null` = unbounded
     * on that side) — the History tab's Range filter.
     */
    fun observeHistoryEventsForCase(
        caseId: Long,
        order: HistorySortOrder,
        limit: Int,
        durationMode: DurationMode,
        dateFrom: Long?,
        dateTo: Long?,
    ): Flow<HistoryEventsPage>

    /** Lean per-event projection (timing + Case `durationMode`) for every active Case — Home / widget counts (spec §9/§14). */
    fun observeActiveCaseEventSpans(): Flow<List<CaseEventSpan>>

    /** Lean per-event projection (timing, intensity, note) for every active Case — the Big Picture grid (spec §9). */
    fun observeActiveCaseEventDetails(): Flow<List<CaseEventDetail>>

    /** Every open-ended event across all Cases, earliest first — Home / widget ongoing indicators (spec §6). */
    fun observeOpenEvents(): Flow<List<EventEntity>>

    /** Most recent moment any active Case's event was actually logged — feeds the Trends `WENT_QUIET` finding's cross-Case "still using the app" signal. */
    fun observeMostRecentLoggedAtAcrossActiveCases(): Flow<Long?>

    suspend fun getEvent(eventId: Long): EventEntity?

    suspend fun eventsInWindow(
        caseId: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<EventEntity>

    suspend fun getMostRecentEventForCase(caseId: Long): EventEntity?

    /** Latest moment any event on the Case ended (`endedAt`, or the start for a point/still-open event); null with no events. */
    suspend fun getLatestEventEndForCase(caseId: Long): Long?

    /** An event on the Case with no `endedAt`, if any. Only meaningful for a `START_STOP` Case (spec §6). */
    suspend fun getOngoingEvent(caseId: Long): EventEntity?

    suspend fun insertEvent(event: EventEntity): Long

    suspend fun updateEvent(event: EventEntity)

    suspend fun deleteEvent(event: EventEntity)

    suspend fun deleteEventById(eventId: Long)

    // Tag
    fun observeAllTags(): Flow<List<TagEntity>>

    fun observeTagsForCase(caseId: Long): Flow<List<TagEntity>>

    fun observeTagsForEvent(eventId: Long): Flow<List<TagEntity>>

    /** One row per tag attachment, for every active Case's events — the Big Picture grid's tag filter and detail rows (spec §9). */
    fun observeActiveCaseEventTagNames(): Flow<List<EventTagName>>

    suspend fun addTagToEvent(
        eventId: Long,
        tagName: String,
    )

    suspend fun removeTagFromEvent(
        eventId: Long,
        tagId: Long,
    )

    /** Event counts per tag across all Cases; tags with no attachments are absent. */
    fun observeTagEventCounts(): Flow<List<TagEventCount>>

    /** Case-insensitive lookup of a tag other than [excludeId], the same match [addTagToEvent] uses. */
    suspend fun findOtherTagByName(
        name: String,
        excludeId: Long,
    ): TagEntity?

    /** Events that carry both [sourceId] and [targetId]; these keep one attachment when the tags merge. */
    suspend fun countEventsWithBoth(
        sourceId: Long,
        targetId: Long,
    ): Int

    suspend fun renameTag(
        tagId: Long,
        name: String,
    )

    /** Re-points [sourceId]'s events at [targetId] and removes [sourceId], as one transaction. The two ids must differ. */
    suspend fun mergeTag(
        sourceId: Long,
        targetId: Long,
    )

    /** Removes the tag from every event; the events themselves are kept. */
    suspend fun deleteTag(tagId: Long)

    // Watch
    suspend fun getWatch(watchId: Long): WatchEntity?

    fun observeWatchesForCase(caseId: Long): Flow<List<WatchEntity>>

    suspend fun getWatchesForCase(caseId: Long): List<WatchEntity>

    suspend fun getEnabledWatches(): List<WatchEntity>

    suspend fun insertWatch(watch: WatchEntity): Long

    suspend fun updateWatch(watch: WatchEntity)

    suspend fun deleteWatch(watch: WatchEntity)

    // Backup (spec §16)
    suspend fun exportBackupData(): BackupData

    /** Full restore: replaces all existing data with [backup]'s, atomically. Not a merge. */
    suspend fun importBackupData(backup: BackupData)
}
