package com.secondmonday.hodith.data

/**
 * Which fields a logged event's row shows — History Share's card rows and the Case Detail
 * History tab's own rows both gate on this, one reusable set rather than two near-duplicate enums
 * (spec §6/§13). A fixed set the user checks/unchecks, mirroring [ShareInsightsSection]'s shape.
 */
enum class HistoryRowField {
    NOTES,
    TAGS,
    DURATION,
    INTENSITY,
}
