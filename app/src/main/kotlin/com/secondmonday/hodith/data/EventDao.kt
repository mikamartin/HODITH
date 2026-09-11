package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE caseId = :caseId ORDER BY occurredAt DESC")
    fun observeEventsForCase(caseId: Long): Flow<List<EventEntity>>

    @Transaction
    @Query("SELECT * FROM events WHERE caseId = :caseId ORDER BY occurredAt DESC")
    fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>>

    /**
     * Capped page of a Case's events, newest-started first (spec §6, "Started" order) — at most
     * [limit] rows, `id DESC` breaking ties on an identical `occurredAt`. Log-tab-only: ongoing-event
     * detection, Insights/Hunch stats, and the Log tab's own summary line all need the full history
     * and keep using [observeEventsWithTagsForCase]. Callers fetch `limit + 1` and trim to detect
     * whether more rows remain (see `RoomHodithRepository.observeLogEventsForCase`).
     */
    @Transaction
    @Query("SELECT * FROM events WHERE caseId = :caseId ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeEventsWithTagsForCasePagedByStart(
        caseId: Long,
        limit: Int,
    ): Flow<List<EventWithTags>>

    /**
     * Capped page of a Case's events ordered by when they *ended* (spec §6, "Ended" order): any
     * still-running event floats first — only meaningful when [isStartStopCase], since a
     * `MANUAL`/`NONE` Case's events are never "running" the way the Log tab's sort toggle means it —
     * then by `endedAt` (or `occurredAt` for an end-less `MANUAL` entry, the same
     * `IFNULL(endedAt, occurredAt)` [getLatestEventEndForCase] already reads), then `occurredAt`,
     * then `id`, all descending. Same `limit + 1` peek-ahead contract as
     * [observeEventsWithTagsForCasePagedByStart].
     */
    @Transaction
    @Query(
        "SELECT * FROM events WHERE caseId = :caseId " +
            "ORDER BY " +
            "CASE WHEN :isStartStopCase = 1 AND endedAt IS NULL THEN 1 ELSE 0 END DESC, " +
            "IFNULL(endedAt, occurredAt) DESC, occurredAt DESC, id DESC " +
            "LIMIT :limit",
    )
    fun observeEventsWithTagsForCasePagedByEnd(
        caseId: Long,
        isStartStopCase: Boolean,
        limit: Int,
    ): Flow<List<EventWithTags>>

    @Query(
        "SELECT * FROM events WHERE caseId = :caseId " +
            "AND occurredAt >= :windowStart AND occurredAt < :windowEnd ORDER BY occurredAt",
    )
    suspend fun eventsInWindow(
        caseId: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<EventEntity>

    @Query("SELECT * FROM events WHERE caseId = :caseId AND endedAt IS NULL LIMIT 1")
    suspend fun getOngoingEvent(caseId: Long): EventEntity?

    /**
     * Lean per-event projection for Home / the widgets' today / this-week counts — `caseId`,
     * timing, and the owning Case's `durationMode`, for every active Case's events. One flat JOIN,
     * no `@Relation`, no full-row hydration (see [CaseEventSpan]).
     */
    @Query(
        "SELECT e.caseId AS caseId, e.occurredAt AS occurredAt, e.endedAt AS endedAt, " +
            "c.durationMode AS durationMode " +
            "FROM events e JOIN cases c ON c.id = e.caseId WHERE c.archived = 0",
    )
    fun observeActiveCaseEventSpans(): Flow<List<CaseEventSpan>>

    /** Every open-ended event across all Cases, earliest first — Home / widget ongoing indicators. Small set (only running events). */
    @Query("SELECT * FROM events WHERE endedAt IS NULL ORDER BY occurredAt")
    fun observeOpenEvents(): Flow<List<EventEntity>>

    /**
     * Lean per-event projection for the Big Picture grid (see [CaseEventDetail]) — `id`, `caseId`,
     * timing, intensity, and note, for every active Case's events. One flat JOIN, no `@Relation`,
     * no tag junction.
     */
    @Query(
        "SELECT e.id AS id, e.caseId AS caseId, e.occurredAt AS occurredAt, e.endedAt AS endedAt, " +
            "e.intensity AS intensity, e.note AS note " +
            "FROM events e JOIN cases c ON c.id = e.caseId WHERE c.archived = 0",
    )
    fun observeActiveCaseEventDetails(): Flow<List<CaseEventDetail>>

    @Query("SELECT * FROM events WHERE caseId = :caseId ORDER BY occurredAt DESC LIMIT 1")
    suspend fun getMostRecentEventForCase(caseId: Long): EventEntity?

    /** Latest moment any event on the Case ended — its `endedAt`, or its start for a point/still-open event. Null when the Case has no events. */
    @Query("SELECT MAX(IFNULL(endedAt, occurredAt)) FROM events WHERE caseId = :caseId")
    suspend fun getLatestEventEndForCase(caseId: Long): Long?

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<EventEntity>
}
