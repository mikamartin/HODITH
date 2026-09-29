package com.secondmonday.hodith.data

/**
 * Which fields a logged event's row shows — Log Share's card rows and the Case Detail Log tab's
 * own rows both gate on this, one reusable set rather than two near-duplicate enums (spec §6/§13).
 * A fixed set the user checks/unchecks, mirroring `viewmodel.ShareInsightsSection`'s shape.
 */
enum class LogRowField {
    NOTES,
    TAGS,
    DURATION,
    INTENSITY,
}
