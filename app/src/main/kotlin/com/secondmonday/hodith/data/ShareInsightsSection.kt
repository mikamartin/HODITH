package com.secondmonday.hodith.data

/**
 * Spec §13's checklist-driven Story section picker. The user orders these by drag and drop, and the
 * card follows that order; the order is device-wide and persisted via [SettingsRepository]. The
 * declaration order is the default.
 */
enum class ShareInsightsSection {
    GAPS,
    STREAKS,
    DURATION,
    RHYTHM,
    INTENSITY,
    TRENDS,
    TAGS,
}

/**
 * The saved order with every section exactly once: saved entries first, in saved order (repeats
 * dropped), then any section the saved order is missing, in declaration order. A new section added
 * later therefore lands at the end rather than vanishing from the picker.
 */
fun orderedShareSections(saved: List<ShareInsightsSection>): List<ShareInsightsSection> = (saved + ShareInsightsSection.entries).distinct()

/**
 * Moves the row at index [from] of the picker's visible rows to index [to]. The visible rows are the
 * sections in [order] that are also in [available], so a section the Case has no data for keeps its
 * slot in the full order while the rest reflow around it.
 */
fun reorderVisibleSections(
    order: List<ShareInsightsSection>,
    available: List<ShareInsightsSection>,
    from: Int,
    to: Int,
): List<ShareInsightsSection> {
    val visible = order.filter { it in available }.toMutableList()
    visible.add(to, visible.removeAt(from))

    val reordered = visible.iterator()
    return order.map { section -> if (section in available) reordered.next() else section }
}
