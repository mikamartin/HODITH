package com.secondmonday.hodith.data

import androidx.room.withTransaction
import com.secondmonday.hodith.data.backup.BackupData
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomHodithRepository
    @Inject
    constructor(
        private val database: HodithDatabase,
        private val caseDao: CaseDao,
        private val eventDao: EventDao,
        private val tagDao: TagDao,
        private val watchDao: WatchDao,
        private val notificationEvalScheduler: NotificationEvalScheduler,
    ) : HodithRepository {
        /**
         * Spec §11: Watches/check-ins evaluate immediately on every event mutation, not just the
         * ~6h periodic job. [NotificationEvalScheduler] runs it fire-and-forget off the caller's
         * thread — so quick-log/start-stop stay instant — and debounces per Case so a rapid logging
         * burst collapses to one evaluation.
         */
        private fun evaluateNotificationsForCase(caseId: Long) {
            notificationEvalScheduler.schedule(caseId)
        }

        // Case
        override fun observeActiveCases(): Flow<List<CaseEntity>> = caseDao.observeActiveCases()

        override suspend fun getActiveCases(): List<CaseEntity> = caseDao.getActiveCases()

        override fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>> = caseDao.observeArchivedCasesWithEvents()

        override fun observeArchivedCaseCount(): Flow<Int> = caseDao.observeArchivedCaseCount()

        override fun observeCase(caseId: Long): Flow<CaseEntity?> = caseDao.observeById(caseId)

        override suspend fun getCase(caseId: Long): CaseEntity? = caseDao.getById(caseId)

        override suspend fun insertCase(case: CaseEntity): Long = caseDao.insert(case)

        override suspend fun updateCase(case: CaseEntity) = caseDao.update(case)

        override suspend fun deleteCase(case: CaseEntity) = caseDao.delete(case)

        override suspend fun deleteAllArchivedCases() = caseDao.deleteAllArchived()

        override suspend fun deleteAllData() {
            caseDao.deleteAll()
            tagDao.deleteAll()
        }

        override suspend fun deleteEventsOlderThan(cutoff: Long) {
            val affectedCaseIds = eventDao.getCaseIdsWithEventsOlderThan(cutoff)
            eventDao.deleteOlderThan(cutoff)
            affectedCaseIds.forEach(::evaluateNotificationsForCase)
        }

        // Event
        override fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>> = eventDao.observeEventsWithTagsForCase(caseId)

        override fun observeLogEventsForCase(
            caseId: Long,
            order: LogSortOrder,
            limit: Int,
            durationMode: DurationMode,
            dateFrom: Long?,
            dateTo: Long?,
        ): Flow<LogEventsPage> {
            val rows =
                when (order) {
                    LogSortOrder.BY_START ->
                        eventDao.observeEventsWithTagsForCasePagedByStart(caseId, dateFrom = dateFrom, dateTo = dateTo, limit = limit + 1)
                    LogSortOrder.BY_END ->
                        eventDao.observeEventsWithTagsForCasePagedByEnd(
                            caseId,
                            isStartStopCase = durationMode == DurationMode.START_STOP,
                            dateFrom = dateFrom,
                            dateTo = dateTo,
                            limit = limit + 1,
                        )
                }
            return rows.map { LogEventsPage(events = it.take(limit), hasMore = it.size > limit) }
        }

        override fun observeActiveCaseEventSpans(): Flow<List<CaseEventSpan>> = eventDao.observeActiveCaseEventSpans()

        override fun observeActiveCaseEventDetails(): Flow<List<CaseEventDetail>> = eventDao.observeActiveCaseEventDetails()

        override fun observeOpenEvents(): Flow<List<EventEntity>> = eventDao.observeOpenEvents()

        override fun observeMostRecentLoggedAtAcrossActiveCases(): Flow<Long?> = eventDao.observeMostRecentLoggedAtAcrossActiveCases()

        override suspend fun getEvent(eventId: Long): EventEntity? = eventDao.getById(eventId)

        override suspend fun eventsInWindow(
            caseId: Long,
            windowStart: Long,
            windowEnd: Long,
        ): List<EventEntity> = eventDao.eventsInWindow(caseId, windowStart, windowEnd)

        override suspend fun getMostRecentEventForCase(caseId: Long): EventEntity? = eventDao.getMostRecentEventForCase(caseId)

        override suspend fun getLatestEventEndForCase(caseId: Long): Long? = eventDao.getLatestEventEndForCase(caseId)

        override suspend fun getOngoingEvent(caseId: Long): EventEntity? = eventDao.getOngoingEvent(caseId)

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

        override fun observeActiveCaseEventTagNames(): Flow<List<EventTagName>> = tagDao.observeActiveCaseEventTagNames()

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

        override fun observeTagEventCounts(): Flow<List<TagEventCount>> = tagDao.observeTagEventCounts()

        override suspend fun findOtherTagByName(
            name: String,
            excludeId: Long,
        ): TagEntity? = tagDao.findOtherTagByName(name, excludeId)

        override suspend fun countEventsWithBoth(
            sourceId: Long,
            targetId: Long,
        ): Int = tagDao.countEventsWithBoth(sourceId, targetId)

        override suspend fun renameTag(
            tagId: Long,
            name: String,
        ) = tagDao.rename(tagId, name.trim())

        override suspend fun mergeTag(
            sourceId: Long,
            targetId: Long,
        ) {
            database.withTransaction {
                tagDao.reassignEventTags(sourceId = sourceId, targetId = targetId)
                // The source's own attachments go with it via the FK cascade on tags.
                tagDao.deleteById(sourceId)
            }
        }

        override suspend fun deleteTag(tagId: Long) = tagDao.deleteById(tagId)

        // Watch
        override suspend fun getWatch(watchId: Long): WatchEntity? = watchDao.getById(watchId)

        override fun observeWatchesForCase(caseId: Long): Flow<List<WatchEntity>> = watchDao.observeWatchesForCase(caseId)

        override suspend fun getWatchesForCase(caseId: Long): List<WatchEntity> = watchDao.getWatchesForCase(caseId)

        override suspend fun getEnabledWatches(): List<WatchEntity> = watchDao.getEnabledWatches()

        override suspend fun insertWatch(watch: WatchEntity): Long = watchDao.insert(watch)

        override suspend fun updateWatch(watch: WatchEntity) = watchDao.update(watch)

        override suspend fun deleteWatch(watch: WatchEntity) = watchDao.delete(watch)

        // Backup
        override suspend fun exportBackupData(): BackupData =
            BackupData(
                cases = caseDao.getAll(),
                tags = tagDao.getAll(),
                events = eventDao.getAll(),
                eventTags = tagDao.getAllEventTags(),
                watches = watchDao.getAll(),
            )

        override suspend fun importBackupData(backup: BackupData) {
            database.withTransaction {
                deleteAllData()
                // FK-safe order: cases/tags before anything referencing them, events before event_tags.
                backup.cases.forEach { caseDao.insert(it) }
                backup.tags.forEach { tagDao.insert(it) }
                backup.events.forEach { eventDao.insert(it) }
                backup.eventTags.forEach { tagDao.insertEventTag(it) }
                backup.watches.forEach { watchDao.insert(it) }
            }
        }
    }
