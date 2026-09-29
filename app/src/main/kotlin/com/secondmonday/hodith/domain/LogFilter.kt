package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.EventWithTags

/**
 * Chronological direction for [filterAndSortEvents] — distinct from [com.secondmonday.hodith.data.LogSortOrder],
 * which picks *which* timestamp the Log tab's own paged query orders by (start vs. end, for
 * ongoing-event handling), not ascending/descending direction.
 */
enum class ChronologicalOrder { NEWEST_FIRST, OLDEST_FIRST }

/**
 * How many entries a Log Share card renders before truncating to the most recent matches (spec
 * §13/§6) — matches the Log tab's own existing initial page size (`EventDao`'s 30-row page), a
 * familiar number rather than an arbitrary new one. Applies to both Story and Square: Square's
 * `heightIn(min = ...)` is a floor, not a ceiling, so it grows for content exactly like Story.
 */
const val LOG_SHARE_CARD_ENTRY_CAP = 30

/**
 * Filters [events] to those whose [com.secondmonday.hodith.data.EventEntity.occurredAt] falls
 * within `[from, to]` inclusive (either bound `null` means unbounded on that side), then sorts by
 * [order]. Pure Kotlin, no Android imports, no `now` dependency — callers resolve "today" via an
 * injected [Clock] before calling. Built as its own reusable unit (not folded into Log Share's
 * card-content assembly) so a future Log-tab-filter item can call the same function.
 */
fun filterAndSortEvents(
    events: List<EventWithTags>,
    from: Long?,
    to: Long?,
    order: ChronologicalOrder,
): List<EventWithTags> {
    val filtered =
        events.filter { eventWithTags ->
            val occurredAt = eventWithTags.event.occurredAt
            (from == null || occurredAt >= from) && (to == null || occurredAt <= to)
        }
    return when (order) {
        ChronologicalOrder.NEWEST_FIRST -> filtered.sortedByDescending { it.event.occurredAt }
        ChronologicalOrder.OLDEST_FIRST -> filtered.sortedBy { it.event.occurredAt }
    }
}
