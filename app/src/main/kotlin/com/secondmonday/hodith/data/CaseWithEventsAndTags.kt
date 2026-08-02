package com.secondmonday.hodith.data

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Like [CaseWithEvents], but each event carries its tags — Big Picture-only, since Home/Archived
 * don't need the extra join.
 */
data class CaseWithEventsAndTags(
    @Embedded val case: CaseEntity,
    @Relation(entity = EventEntity::class, parentColumn = "id", entityColumn = "caseId")
    val events: List<EventWithTags>,
)
