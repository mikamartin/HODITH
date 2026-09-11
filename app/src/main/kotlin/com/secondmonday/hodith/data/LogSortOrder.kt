package com.secondmonday.hodith.data

/**
 * How the Case Detail Log tab orders and pages its event list (spec §6). [BY_START] is the
 * default — newest-started first, `LIMIT`-capped, matches `EventDao`'s own default order.
 * [BY_END] floats a still-running `START_STOP` event to the top, then orders by when each
 * event finished. Pushed into `EventDao`'s paged queries directly (see
 * `EventDao.observeEventsWithTagsForCasePagedByStart` / `...PagedByEnd`) rather than sorted in
 * memory — paging already means re-querying on every loaded-limit change, so re-querying on every
 * sort-order change too is no new class of complexity, just one more query switch.
 */
enum class LogSortOrder { BY_START, BY_END }
