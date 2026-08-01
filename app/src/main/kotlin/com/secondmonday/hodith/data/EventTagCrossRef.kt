package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "event_tags",
    primaryKeys = ["eventId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("eventId"), Index("tagId")],
)
@JsonClass(generateAdapter = true)
data class EventTagCrossRef(
    val eventId: Long,
    val tagId: Long,
)
