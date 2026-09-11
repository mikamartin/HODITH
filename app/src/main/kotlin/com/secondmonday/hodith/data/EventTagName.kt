package com.secondmonday.hodith.data

/**
 * One tag attached to one event, for every active Case's events — the Big Picture grid's tag
 * filter and the day/week detail rows' tag pills (spec §9). One row per attachment, flat: no
 * per-event junction nesting the way [EventWithTags] hydrates a `List<TagEntity>` per event.
 */
data class EventTagName(
    val eventId: Long,
    val tagName: String,
)
