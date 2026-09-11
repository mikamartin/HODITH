package com.secondmonday.hodith.data

/**
 * A capped, sorted slice of one Case's event history for the Log tab's row list (spec §6) — see
 * `HodithRepository.observeLogEventsForCase`. [hasMore] is true when the underlying query
 * returned more rows than [events] (a `limit + 1` peek-ahead the repository trims before exposing
 * this), so the "Show more" button knows whether to render.
 */
data class LogEventsPage(
    val events: List<EventWithTags>,
    val hasMore: Boolean,
)
