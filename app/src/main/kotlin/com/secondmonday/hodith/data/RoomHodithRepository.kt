package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHodithRepository
    @Inject
    constructor(
        private val caseDao: CaseDao,
        private val eventDao: EventDao,
        private val tagDao: TagDao,
        private val hunchDao: HunchDao,
        private val triggerDao: TriggerDao,
    ) : HodithRepository {
        // Case
        override fun observeActiveCases(): Flow<List<CaseEntity>> = caseDao.observeActiveCases()

        override fun observeActiveCasesWithEvents(): Flow<List<CaseWithEvents>> = caseDao.observeActiveCasesWithEvents()

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

        override suspend fun insertEvent(event: EventEntity): Long = eventDao.insert(event)

        override suspend fun updateEvent(event: EventEntity) = eventDao.update(event)

        override suspend fun deleteEvent(event: EventEntity) = eventDao.delete(event)

        override suspend fun deleteEventById(eventId: Long) = eventDao.deleteById(eventId)

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
    }
