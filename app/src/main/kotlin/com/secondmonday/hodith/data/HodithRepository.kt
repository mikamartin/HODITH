package com.secondmonday.hodith.data

import com.secondmonday.hodith.data.backup.BackupData
import kotlinx.coroutines.flow.Flow

interface HodithRepository {
    // Case
    fun observeActiveCases(): Flow<List<CaseEntity>>

    suspend fun getActiveCases(): List<CaseEntity>

    fun observeActiveCasesWithEvents(): Flow<List<CaseWithEvents>>

    fun observeActiveCasesWithEventsAndTags(): Flow<List<CaseWithEventsAndTags>>

    fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>>

    fun observeCase(caseId: Long): Flow<CaseEntity?>

    suspend fun getCase(caseId: Long): CaseEntity?

    suspend fun insertCase(case: CaseEntity): Long

    suspend fun updateCase(case: CaseEntity)

    suspend fun deleteCase(case: CaseEntity)

    suspend fun deleteAllArchivedCases()

    suspend fun deleteAllData()

    // Event
    fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>>

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

    suspend fun addTagToEvent(
        eventId: Long,
        tagName: String,
    )

    suspend fun removeTagFromEvent(
        eventId: Long,
        tagId: Long,
    )

    // Hunch
    fun observeActiveHunch(caseId: Long): Flow<HunchEntity?>

    suspend fun getActiveHunch(caseId: Long): HunchEntity?

    fun observeHunchHistory(caseId: Long): Flow<List<HunchEntity>>

    suspend fun insertHunch(hunch: HunchEntity): Long

    suspend fun updateHunch(hunch: HunchEntity)

    suspend fun deleteHunch(hunch: HunchEntity)

    // Trigger
    suspend fun getTrigger(triggerId: Long): TriggerEntity?

    fun observeTriggersForCase(caseId: Long): Flow<List<TriggerEntity>>

    suspend fun getTriggersForCase(caseId: Long): List<TriggerEntity>

    suspend fun getEnabledTriggers(): List<TriggerEntity>

    suspend fun insertTrigger(trigger: TriggerEntity): Long

    suspend fun updateTrigger(trigger: TriggerEntity)

    suspend fun deleteTrigger(trigger: TriggerEntity)

    // Backup (spec §16)
    suspend fun exportBackupData(): BackupData

    /** Full restore: replaces all existing data with [backup]'s, atomically. Not a merge. */
    suspend fun importBackupData(backup: BackupData)
}
