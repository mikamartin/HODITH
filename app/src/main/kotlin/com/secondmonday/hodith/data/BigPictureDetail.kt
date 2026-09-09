package com.secondmonday.hodith.data

/** One optional field a Big Picture detail row can show (spec §9); [token] is its stored form. */
enum class BigPictureDetailField(
    val token: String,
) {
    NOTES("notes"),
    TAGS("tags"),
    DURATION("duration"),
    INTENSITY("intensity"),
}

/**
 * Which optional fields the Big Picture day/week detail rows show for each event (spec §9). The
 * user edits this from the grid filter-row's edit icon; it persists via [SettingsRepository].
 * Time and the Case's icon/name are always shown, so they aren't represented here.
 *
 * Serialized as a comma-joined list of the enabled fields' [BigPictureDetailField.token]s. An
 * absent stored value (`null`, never set) reads as [DEFAULT]; a stored empty string is a real
 * choice — every field turned off — and round-trips as such.
 */
data class BigPictureDetail(
    val notes: Boolean,
    val tags: Boolean,
    val duration: Boolean,
    val intensity: Boolean,
) {
    fun has(field: BigPictureDetailField): Boolean =
        when (field) {
            BigPictureDetailField.NOTES -> notes
            BigPictureDetailField.TAGS -> tags
            BigPictureDetailField.DURATION -> duration
            BigPictureDetailField.INTENSITY -> intensity
        }

    fun with(
        field: BigPictureDetailField,
        enabled: Boolean,
    ): BigPictureDetail =
        when (field) {
            BigPictureDetailField.NOTES -> copy(notes = enabled)
            BigPictureDetailField.TAGS -> copy(tags = enabled)
            BigPictureDetailField.DURATION -> copy(duration = enabled)
            BigPictureDetailField.INTENSITY -> copy(intensity = enabled)
        }

    /** Comma-joined [BigPictureDetailField.token]s for the enabled fields, in enum order. */
    fun serialize(): String = BigPictureDetailField.entries.filter { has(it) }.joinToString(",") { it.token }

    companion object {
        /** Spec §9: notes, tags and same-day duration on; intensity an opt-in. */
        val DEFAULT = BigPictureDetail(notes = true, tags = true, duration = true, intensity = false)

        val ALL_OFF = BigPictureDetail(notes = false, tags = false, duration = false, intensity = false)

        private val byToken = BigPictureDetailField.entries.associateBy { it.token }

        /**
         * [raw] `null` (never stored) reads as [DEFAULT]; any present string — including `""` — is
         * an explicit choice, parsed token by token with unknown or duplicate tokens ignored and
         * order irrelevant.
         */
        fun parse(raw: String?): BigPictureDetail {
            if (raw == null) return DEFAULT
            val fields = raw.split(",").mapNotNull { byToken[it.trim()] }.toSet()
            return BigPictureDetail(
                notes = BigPictureDetailField.NOTES in fields,
                tags = BigPictureDetailField.TAGS in fields,
                duration = BigPictureDetailField.DURATION in fields,
                intensity = BigPictureDetailField.INTENSITY in fields,
            )
        }
    }
}
