package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags

/**
 * How the Case Detail Log tab orders its event list. UI-only view state — no persistence and
 * no schema change. [BY_START] is the default and matches the order Room already returns
 * (`EventDao.observeEventsWithTagsForCase`, `ORDER BY occurredAt DESC`).
 */
internal enum class LogSortOrder { BY_START, BY_END }

/**
 * Orders [events] for the Log tab (spec §6). [BY_START] is newest-started first, unchanged from
 * how the DAO returns them. [BY_END] reads a duration-tracking Case's events by when they
 * finished: any still-running `START_STOP` event first (newest start among those), then the
 * rest by `endedAt` descending. An end-less `MANUAL` entry has no real end, so it falls back to
 * its own `occurredAt` — the same `IFNULL(endedAt, occurredAt)` reading `EventDao` already uses
 * in `getLatestEventEndForCase`. The still-running test matches [ongoingEventsIn].
 *
 * Pure: reads stored fields only, no clock, so it's exercised directly in `LogSortTest`.
 */
internal fun sortEventsForLog(
    events: List<EventWithTags>,
    order: LogSortOrder,
    durationMode: DurationMode,
): List<EventWithTags> =
    when (order) {
        LogSortOrder.BY_START ->
            events.sortedWith(
                compareByDescending<EventWithTags> { it.event.occurredAt }
                    .thenByDescending { it.event.id },
            )
        LogSortOrder.BY_END -> {
            fun isRunning(e: EventWithTags): Boolean = durationMode == DurationMode.START_STOP && e.event.endedAt == null
            events.sortedWith(
                compareByDescending<EventWithTags> { isRunning(it) }
                    .thenByDescending { it.event.endedAt ?: it.event.occurredAt }
                    .thenByDescending { it.event.occurredAt }
                    .thenByDescending { it.event.id },
            )
        }
    }
