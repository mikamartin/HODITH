package com.secondmonday.hodith.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class EventWithTags(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(EventTagCrossRef::class, parentColumn = "eventId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
)
