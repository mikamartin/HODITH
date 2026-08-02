package com.secondmonday.hodith.data

import androidx.room.withTransaction
import com.secondmonday.hodith.data.backup.BackupData
import com.secondmonday.hodith.notification.NotificationEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class RoomHodithRepository
    @Inject
    constructor(
        private val database: HodithDatabase,
        private val caseDao: CaseDao,
        private val eventDao: EventDao,
        private val tagDao: TagDao,
        private val hunchDao: HunchDao,
        private val triggerDao: TriggerDao,
        private val notificationEvaluator: Provider<NotificationEvaluator>,
        private val applicationScope: CoroutineScope,
    ) : HodithRepository {
        /**
         * Spec §11: Triggers/check-ins evaluate immediately on every event mutation, not just the
         * ~6h periodic job — launched fire-and-forget on [applicationScope] so quick-log/start-stop
         * stay instant rather than waiting on DB reads and notification posting.
         */
        private fun evaluateNotificationsForCase(caseId: Long) {
            applicationScope.launch { notificationEvaluator.get().evaluateCase(caseId) }
        }

        // Case
        override fun observeActiveCases(): Flow<List<CaseEntity>> = caseDao.observeActiveCases()

        override suspend fun getActiveCases(): List<CaseEntity> = caseDao.getActiveCases()

        override fun observeActiveCasesWithEvents(): Flow<List<CaseWithEvents>> = caseDao.observeActiveCasesWithEvents()

        override fun observeActiveCasesWithEventsAndTags(): Flow<List<CaseWithEventsAndTags>> =
            caseDao.observeActiveCasesWithEventsAndTags()

        override fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>> = caseDao.observeArchivedCasesWithEvents()

        override fun observeCase(caseId: Long): Flow<CaseEntity?> = caseDao.observeById(caseId)

        override suspend fun getCase(caseId: Long): CaseEntity? = caseDao.getById(caseId)

        override suspend fun insertCase(case: CaseEntity): Long = caseDao.insert(case)

        override suspend fun updateCase(case: CaseEntity) = caseDao.update(case)

        override suspend fun deleteCase(case: CaseEntity) = caseDao.delete(case)

        override suspend fun deleteAllData() {
            caseDao.deleteAll()
            tagDao.deleteAll()
        }

        // Event
        override fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>> = eventDao.observeEventsWithTagsForCase(caseId)

        override suspend fun getEvent(eventId: Long): EventEntity? = eventDao.getById(eventId)

        override suspend fun eventsInWindow(
            caseId: Long,
            windowStart: Long,
            windowEnd: Long,
        ): List<EventEntity> = eventDao.eventsInWindow(caseId, windowStart, windowEnd)

        override suspend fun getMostRecentEventForCase(caseId: Long): EventEntity? = eventDao.getMostRecentEventForCase(caseId)

        override suspend fun insertEvent(event: EventEntity): Long =
            eventDao.insert(event).also { evaluateNotificationsForCase(event.caseId) }

        override suspend fun updateEvent(event: EventEntity) {
            eventDao.update(event)
            evaluateNotificationsForCase(event.caseId)
        }

        override suspend fun deleteEvent(event: EventEntity) {
            eventDao.delete(event)
            evaluateNotificationsForCase(event.caseId)
        }

        override suspend fun deleteEventById(eventId: Long) {
            val caseId = eventDao.getById(eventId)?.caseId
            eventDao.deleteById(eventId)
            caseId?.let(::evaluateNotificationsForCase)
        }

        // Tag
        override fun observeAllTags(): Flow<List<TagEntity>> = tagDao.observeAllTags()

        override fun observeTagsForCase(caseId: Long): Flow<List<TagEntity>> = tagDao.observeTagsForCase(caseId)

        override fun observeTagsForEvent(eventId: Long): Flow<List<TagEntity>> = tagDao.observeTagsForEvent(eventId)

        override suspend fun addTagToEvent(
            eventId: Long,
            tagName: String,
        ) {
            val trimmedName = tagName.trim()
            val tagId = tagDao.getByName(trimmedName)?.id ?: tagDao.insert(TagEntity(name = trimmedName))
            tagDao.insertEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))
        }

        override suspend fun removeTagFromEvent(
            eventId: Long,
            tagId: Long,
        ) = tagDao.deleteEventTag(EventTagCrossRef(eventId = eventId, tagId = tagId))

        // Hunch
        override fun observeActiveHunch(caseId: Long): Flow<HunchEntity?> = hunchDao.observeActiveHunch(caseId)

        override suspend fun getActiveHunch(caseId: Long): HunchEntity? = hunchDao.getActiveHunch(caseId)

        override fun observeHunchHistory(caseId: Long): Flow<List<HunchEntity>> = hunchDao.observeHunchHistory(caseId)

        override suspend fun insertHunch(hunch: HunchEntity): Long = hunchDao.insert(hunch)

        override suspend fun updateHunch(hunch: HunchEntity) = hunchDao.update(hunch)

        override suspend fun deleteHunch(hunch: HunchEntity) = hunchDao.delete(hunch)

        // Trigger
        override suspend fun getTrigger(triggerId: Long): TriggerEntity? = triggerDao.getById(triggerId)

        override fun observeTriggersForCase(caseId: Long): Flow<List<TriggerEntity>> = triggerDao.observeTriggersForCase(caseId)

        override suspend fun getTriggersForCase(caseId: Long): List<TriggerEntity> = triggerDao.getTriggersForCase(caseId)

        override suspend fun getEnabledTriggers(): List<TriggerEntity> = triggerDao.getEnabledTriggers()

        override suspend fun insertTrigger(trigger: TriggerEntity): Long = triggerDao.insert(trigger)

        override suspend fun updateTrigger(trigger: TriggerEntity) = triggerDao.update(trigger)

        override suspend fun deleteTrigger(trigger: TriggerEntity) = triggerDao.delete(trigger)

        // Backup
        override suspend fun exportBackupData(): BackupData =
            BackupData(
                cases = caseDao.getAll(),
                tags = tagDao.getAll(),
                events = eventDao.getAll(),
                eventTags = tagDao.getAllEventTags(),
                hunches = hunchDao.getAll(),
                triggers = triggerDao.getAll(),
            )

        override suspend fun importBackupData(backup: BackupData) {
            database.withTransaction {
                deleteAllData()
                // FK-safe order: cases/tags before anything referencing them, events before event_tags.
                backup.cases.forEach { caseDao.insert(it) }
                backup.tags.forEach { tagDao.insert(it) }
                backup.events.forEach { eventDao.insert(it) }
                backup.eventTags.forEach { tagDao.insertEventTag(it) }
                backup.hunches.forEach { hunchDao.insert(it) }
                backup.triggers.forEach { triggerDao.insert(it) }
            }
        }
    }
