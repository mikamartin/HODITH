package com.secondmonday.hodith.data

import com.secondmonday.hodith.data.backup.BackupData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Hand-rolled in-memory test double for [HodithRepository] — no mocking library, same style as
 * [com.secondmonday.hodith.domain.FakeClock]. Each entity is a [MutableStateFlow] tests can seed
 * directly (e.g. `fake.cases.value = listOf(...)`), and every `observe*` query is recomputed
 * reactively from that state via `combine`/`map`, mirroring the sort order and join shape Room
 * produces for the real queries (see [CaseDao], [EventDao], [TagDao]).
 */
class FakeHodithRepository : HodithRepository {
    private var nextCaseId = 1L
    private var nextEventId = 1L
    private var nextTagId = 1L
    private var nextWatchId = 1L

    val cases = MutableStateFlow<List<CaseEntity>>(emptyList())
    val events = MutableStateFlow<List<EventEntity>>(emptyList())
    val tags = MutableStateFlow<List<TagEntity>>(emptyList())
    val eventTags = MutableStateFlow<List<EventTagCrossRef>>(emptyList())
    val watches = MutableStateFlow<List<WatchEntity>>(emptyList())

    // Case
    override fun observeActiveCases(): Flow<List<CaseEntity>> =
        cases.map { list -> list.filterNot { it.archived }.sortedBy { it.sortOrder } }

    override suspend fun getActiveCases(): List<CaseEntity> = cases.value.filterNot { it.archived }.sortedBy { it.sortOrder }

    override fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>> =
        combine(cases, events) { caseList, eventList ->
            caseList.filter { it.archived }.sortedBy { it.name.lowercase() }.map { case ->
                CaseWithEvents(case, eventList.filter { it.caseId == case.id })
            }
        }

    override fun observeArchivedCaseCount(): Flow<Int> = cases.map { list -> list.count { it.archived } }

    override fun observeCase(caseId: Long): Flow<CaseEntity?> = cases.map { list -> list.find { it.id == caseId } }

    override suspend fun getCase(caseId: Long): CaseEntity? = cases.value.find { it.id == caseId }

    override suspend fun insertCase(case: CaseEntity): Long {
        val id = if (case.id != 0L) case.id else nextCaseId++
        cases.update { it + case.copy(id = id) }
        return id
    }

    override suspend fun updateCase(case: CaseEntity) {
        cases.update { list -> list.map { if (it.id == case.id) case else it } }
    }

    override suspend fun deleteCase(case: CaseEntity) {
        cases.update { list -> list.filterNot { it.id == case.id } }
        events.update { list -> list.filterNot { it.caseId == case.id } }
        watches.update { list -> list.filterNot { it.caseId == case.id } }
    }

    override suspend fun deleteAllArchivedCases() {
        val archivedIds =
            cases.value
                .filter { it.archived }
                .map { it.id }
                .toSet()
        cases.update { list -> list.filterNot { it.id in archivedIds } }
        events.update { list -> list.filterNot { it.caseId in archivedIds } }
        watches.update { list -> list.filterNot { it.caseId in archivedIds } }
    }

    override suspend fun deleteAllData() {
        cases.value = emptyList()
        events.value = emptyList()
        tags.value = emptyList()
        eventTags.value = emptyList()
        watches.value = emptyList()
    }

    override suspend fun deleteEventsOlderThan(cutoff: Long) {
        val survivingIds =
            events.value
                .filter { it.occurredAt >= cutoff }
                .map { it.id }
                .toSet()
        events.update { list -> list.filter { it.id in survivingIds } }
        eventTags.update { list -> list.filter { it.eventId in survivingIds } }
    }

    // Event
    override fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>> =
        combine(events, tags, eventTags) { eventList, tagList, crossRefs ->
            eventList.filter { it.caseId == caseId }.sortedByDescending { it.occurredAt }.map { event ->
                val tagIds = crossRefs.filter { it.eventId == event.id }.map { it.tagId }.toSet()
                EventWithTags(event, tagList.filter { it.id in tagIds })
            }
        }

    override fun observeLogEventsForCase(
        caseId: Long,
        order: LogSortOrder,
        limit: Int,
        durationMode: DurationMode,
        dateFrom: Long?,
        dateTo: Long?,
    ): Flow<LogEventsPage> =
        combine(events, tags, eventTags) { eventList, tagList, crossRefs ->
            val forCase =
                eventList.filter {
                    it.caseId == caseId &&
                        (dateFrom == null || it.occurredAt >= dateFrom) &&
                        (dateTo == null || it.occurredAt <= dateTo)
                }
            val ordered =
                when (order) {
                    LogSortOrder.BY_START ->
                        forCase.sortedWith(compareByDescending<EventEntity> { it.occurredAt }.thenByDescending { it.id })
                    LogSortOrder.BY_END -> {
                        fun isRunning(e: EventEntity) = durationMode == DurationMode.START_STOP && e.endedAt == null
                        forCase.sortedWith(
                            compareByDescending<EventEntity> { isRunning(it) }
                                .thenByDescending { it.endedAt ?: it.occurredAt }
                                .thenByDescending { it.occurredAt }
                                .thenByDescending { it.id },
                        )
                    }
                }
            val page = ordered.take(limit + 1)
            LogEventsPage(
                events =
                    page.take(limit).map { event ->
                        val tagIds = crossRefs.filter { it.eventId == event.id }.map { it.tagId }.toSet()
                        EventWithTags(event, tagList.filter { it.id in tagIds })
                    },
                hasMore = page.size > limit,
            )
        }

    override fun observeActiveCaseEventSpans(): Flow<List<CaseEventSpan>> =
        combine(cases, events) { caseList, eventList ->
            val activeById = caseList.filterNot { it.archived }.associateBy { it.id }
            eventList.mapNotNull { event ->
                activeById[event.caseId]?.let { case ->
                    CaseEventSpan(event.caseId, event.occurredAt, event.endedAt, case.durationMode)
                }
            }
        }

    override fun observeActiveCaseEventDetails(): Flow<List<CaseEventDetail>> =
        combine(cases, events) { caseList, eventList ->
            val activeIds = caseList.filterNot { it.archived }.map { it.id }.toSet()
            eventList
                .filter { it.caseId in activeIds }
                .map { CaseEventDetail(it.id, it.caseId, it.occurredAt, it.endedAt, it.intensity, it.note, it.utcOffsetMinutes) }
        }

    override fun observeOpenEvents(): Flow<List<EventEntity>> =
        events.map { list -> list.filter { it.endedAt == null }.sortedBy { it.occurredAt } }

    override fun observeMostRecentLoggedAtAcrossActiveCases(): Flow<Long?> =
        combine(cases, events) { caseList, eventList ->
            val activeIds = caseList.filterNot { it.archived }.map { it.id }.toSet()
            eventList.filter { it.caseId in activeIds }.maxOfOrNull { it.loggedAt }
        }

    override suspend fun getEvent(eventId: Long): EventEntity? = events.value.find { it.id == eventId }

    override suspend fun eventsInWindow(
        caseId: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<EventEntity> =
        events.value
            .filter { it.caseId == caseId && it.occurredAt >= windowStart && it.occurredAt < windowEnd }
            .sortedBy { it.occurredAt }

    override suspend fun getMostRecentEventForCase(caseId: Long): EventEntity? =
        events.value.filter { it.caseId == caseId }.maxByOrNull { it.occurredAt }

    override suspend fun getLatestEventEndForCase(caseId: Long): Long? =
        events.value.filter { it.caseId == caseId }.maxOfOrNull { it.endedAt ?: it.occurredAt }

    override suspend fun getOngoingEvent(caseId: Long): EventEntity? =
        events.value.firstOrNull { it.caseId == caseId && it.endedAt == null }

    override suspend fun insertEvent(event: EventEntity): Long {
        val id = if (event.id != 0L) event.id else nextEventId++
        events.update { it + event.copy(id = id) }
        return id
    }

    override suspend fun updateEvent(event: EventEntity) {
        events.update { list -> list.map { if (it.id == event.id) event else it } }
    }

    override suspend fun deleteEvent(event: EventEntity) {
        events.update { list -> list.filterNot { it.id == event.id } }
    }

    override suspend fun deleteEventById(eventId: Long) {
        events.update { list -> list.filterNot { it.id == eventId } }
    }

    // Tag
    override fun observeAllTags(): Flow<List<TagEntity>> = tags.map { list -> list.sortedBy { it.name } }

    override fun observeTagsForCase(caseId: Long): Flow<List<TagEntity>> =
        combine(events, tags, eventTags) { eventList, tagList, crossRefs ->
            val eventIds = eventList.filter { it.caseId == caseId }.map { it.id }.toSet()
            val tagIds = crossRefs.filter { it.eventId in eventIds }.map { it.tagId }.toSet()
            tagList.filter { it.id in tagIds }.sortedBy { it.name }
        }

    override fun observeTagsForEvent(eventId: Long): Flow<List<TagEntity>> =
        combine(tags, eventTags) { tagList, crossRefs ->
            val tagIds = crossRefs.filter { it.eventId == eventId }.map { it.tagId }.toSet()
            tagList.filter { it.id in tagIds }
        }

    override fun observeActiveCaseEventTagNames(): Flow<List<EventTagName>> =
        combine(cases, events, tags, eventTags) { caseList, eventList, tagList, crossRefs ->
            val activeIds = caseList.filterNot { it.archived }.map { it.id }.toSet()
            val activeEventIds = eventList.filter { it.caseId in activeIds }.map { it.id }.toSet()
            val tagsById = tagList.associateBy { it.id }
            crossRefs.mapNotNull { xref ->
                if (xref.eventId !in activeEventIds) return@mapNotNull null
                tagsById[xref.tagId]?.let { EventTagName(xref.eventId, it.name) }
            }
        }

    override suspend fun addTagToEvent(
        eventId: Long,
        tagName: String,
    ) {
        val trimmedName = tagName.trim()
        // Case-insensitive, as the Room lookup is (getByName is COLLATE NOCASE).
        val existing = tags.value.find { it.name.equals(trimmedName, ignoreCase = true) }
        val tagId =
            existing?.id ?: run {
                val id = nextTagId++
                tags.update { it + TagEntity(id = id, name = trimmedName) }
                id
            }
        val crossRef = EventTagCrossRef(eventId = eventId, tagId = tagId)
        eventTags.update { if (crossRef in it) it else it + crossRef }
    }

    override suspend fun removeTagFromEvent(
        eventId: Long,
        tagId: Long,
    ) {
        eventTags.update { list -> list.filterNot { it.eventId == eventId && it.tagId == tagId } }
    }

    override fun observeTagEventCounts(): Flow<List<TagEventCount>> =
        eventTags.map { list -> list.groupingBy { it.tagId }.eachCount().map { (tagId, count) -> TagEventCount(tagId, count) } }

    // Matches Room's COLLATE NOCASE lookup, so fake-based tests see the same collisions the app does.
    override suspend fun findOtherTagByName(
        name: String,
        excludeId: Long,
    ): TagEntity? =
        tags.value
            .filter { it.id != excludeId && it.name.equals(name, ignoreCase = true) }
            .sortedByDescending { it.name == name }
            .firstOrNull()

    override suspend fun countEventsWithBoth(
        sourceId: Long,
        targetId: Long,
    ): Int {
        val sourceEvents =
            eventTags.value
                .filter { it.tagId == sourceId }
                .map { it.eventId }
                .toSet()
        return eventTags.value.count { it.tagId == targetId && it.eventId in sourceEvents }
    }

    override suspend fun renameTag(
        tagId: Long,
        name: String,
    ) {
        tags.update { list -> list.map { if (it.id == tagId) it.copy(name = name.trim()) else it } }
    }

    override suspend fun mergeTag(
        sourceId: Long,
        targetId: Long,
    ) {
        val sourceEvents = eventTags.value.filter { it.tagId == sourceId }.map { it.eventId }
        eventTags.update { list ->
            val kept = list.filterNot { it.tagId == sourceId }
            val targetEvents = kept.filter { it.tagId == targetId }.map { it.eventId }.toSet()
            kept + sourceEvents.filterNot { it in targetEvents }.map { EventTagCrossRef(eventId = it, tagId = targetId) }
        }
        tags.update { list -> list.filterNot { it.id == sourceId } }
    }

    override suspend fun deleteTag(tagId: Long) {
        tags.update { list -> list.filterNot { it.id == tagId } }
        eventTags.update { list -> list.filterNot { it.tagId == tagId } }
    }

    // Watch
    override suspend fun getWatch(watchId: Long): WatchEntity? = watches.value.find { it.id == watchId }

    override fun observeWatchesForCase(caseId: Long): Flow<List<WatchEntity>> = watches.map { list -> list.filter { it.caseId == caseId } }

    override suspend fun getWatchesForCase(caseId: Long): List<WatchEntity> = watches.value.filter { it.caseId == caseId }

    override suspend fun getEnabledWatches(): List<WatchEntity> = watches.value.filter { it.enabled }

    override suspend fun insertWatch(watch: WatchEntity): Long {
        val id = if (watch.id != 0L) watch.id else nextWatchId++
        watches.update { it + watch.copy(id = id) }
        return id
    }

    override suspend fun updateWatch(watch: WatchEntity) {
        watches.update { list -> list.map { if (it.id == watch.id) watch else it } }
    }

    override suspend fun deleteWatch(watch: WatchEntity) {
        watches.update { list -> list.filterNot { it.id == watch.id } }
    }

    // Backup
    override suspend fun exportBackupData(): BackupData =
        BackupData(
            cases = cases.value,
            tags = tags.value,
            events = events.value,
            eventTags = eventTags.value,
            watches = watches.value,
        )

    override suspend fun importBackupData(backup: BackupData) {
        cases.value = backup.cases
        tags.value = backup.tags
        events.value = backup.events
        eventTags.value = backup.eventTags
        watches.value = backup.watches
    }
}
