package com.secondmonday.hodith.data

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
    private var nextHunchId = 1L
    private var nextTriggerId = 1L

    val cases = MutableStateFlow<List<CaseEntity>>(emptyList())
    val events = MutableStateFlow<List<EventEntity>>(emptyList())
    val tags = MutableStateFlow<List<TagEntity>>(emptyList())
    val eventTags = MutableStateFlow<List<EventTagCrossRef>>(emptyList())
    val hunches = MutableStateFlow<List<HunchEntity>>(emptyList())
    val triggers = MutableStateFlow<List<TriggerEntity>>(emptyList())

    // Case
    override fun observeActiveCases(): Flow<List<CaseEntity>> =
        cases.map { list -> list.filterNot { it.archived }.sortedBy { it.sortOrder } }

    override fun observeActiveCasesWithEvents(): Flow<List<CaseWithEvents>> =
        combine(cases, events) { caseList, eventList ->
            caseList.filterNot { it.archived }.sortedBy { it.sortOrder }.map { case ->
                CaseWithEvents(case, eventList.filter { it.caseId == case.id })
            }
        }

    override fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>> =
        combine(cases, events) { caseList, eventList ->
            caseList.filter { it.archived }.sortedBy { it.name.lowercase() }.map { case ->
                CaseWithEvents(case, eventList.filter { it.caseId == case.id })
            }
        }

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
    }

    override suspend fun deleteAllData() {
        cases.value = emptyList()
        events.value = emptyList()
        tags.value = emptyList()
        eventTags.value = emptyList()
        hunches.value = emptyList()
        triggers.value = emptyList()
    }

    // Event
    override fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>> =
        combine(events, tags, eventTags) { eventList, tagList, crossRefs ->
            eventList.filter { it.caseId == caseId }.sortedByDescending { it.occurredAt }.map { event ->
                val tagIds = crossRefs.filter { it.eventId == event.id }.map { it.tagId }.toSet()
                EventWithTags(event, tagList.filter { it.id in tagIds })
            }
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

    override suspend fun addTagToEvent(
        eventId: Long,
        tagName: String,
    ) {
        val trimmedName = tagName.trim()
        val existing = tags.value.find { it.name == trimmedName }
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

    // Hunch
    override fun observeActiveHunch(caseId: Long): Flow<HunchEntity?> =
        hunches.map { list -> list.find { it.caseId == caseId && it.resolvedAt == null } }

    override fun observeHunchHistory(caseId: Long): Flow<List<HunchEntity>> =
        hunches.map { list -> list.filter { it.caseId == caseId }.sortedByDescending { it.createdAt } }

    override suspend fun insertHunch(hunch: HunchEntity): Long {
        val id = if (hunch.id != 0L) hunch.id else nextHunchId++
        hunches.update { it + hunch.copy(id = id) }
        return id
    }

    override suspend fun updateHunch(hunch: HunchEntity) {
        hunches.update { list -> list.map { if (it.id == hunch.id) hunch else it } }
    }

    override suspend fun deleteHunch(hunch: HunchEntity) {
        hunches.update { list -> list.filterNot { it.id == hunch.id } }
    }

    // Trigger
    override suspend fun getTrigger(triggerId: Long): TriggerEntity? = triggers.value.find { it.id == triggerId }

    override fun observeTriggersForCase(caseId: Long): Flow<List<TriggerEntity>> =
        triggers.map { list -> list.filter { it.caseId == caseId } }

    override suspend fun getTriggersForCase(caseId: Long): List<TriggerEntity> = triggers.value.filter { it.caseId == caseId }

    override suspend fun getEnabledTriggers(): List<TriggerEntity> = triggers.value.filter { it.enabled }

    override suspend fun insertTrigger(trigger: TriggerEntity): Long {
        val id = if (trigger.id != 0L) trigger.id else nextTriggerId++
        triggers.update { it + trigger.copy(id = id) }
        return id
    }

    override suspend fun updateTrigger(trigger: TriggerEntity) {
        triggers.update { list -> list.map { if (it.id == trigger.id) trigger else it } }
    }

    override suspend fun deleteTrigger(trigger: TriggerEntity) {
        triggers.update { list -> list.filterNot { it.id == trigger.id } }
    }
}
