package com.secondmonday.hodith.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.domain.timeline.ZoomLevel

/**
 * One user-visible string per key, in three personalities (spec §12). Composables read
 * [LocalVoice] instead of branching on theme, so a string can never ship in only one voice.
 * Minimal key set for now — only what Big Picture needs; Phase 4 extends this interface with
 * the full string set rather than replacing it.
 */
interface Voice {
    val bigPictureEmptyState: String
    val bigPictureEarlyDays: String

    fun timeRangeLabel(zoomLevel: ZoomLevel): String
}

object SeriousVoice : Voice {
    override val bigPictureEmptyState = "No cases yet."
    override val bigPictureEarlyDays = "Insufficient data. Keep logging."

    override fun timeRangeLabel(zoomLevel: ZoomLevel): String =
        when (zoomLevel) {
            ZoomLevel.WEEK -> "Week"
            ZoomLevel.MONTH -> "Month"
            ZoomLevel.THREE_MONTH -> "3 Months"
            ZoomLevel.YEAR -> "Year"
        }
}

object GothVoice : Voice {
    override val bigPictureEmptyState = "Nothing is being watched. Yet."
    override val bigPictureEarlyDays = "The evidence is yet insufficient for despair."

    override fun timeRangeLabel(zoomLevel: ZoomLevel): String =
        when (zoomLevel) {
            ZoomLevel.WEEK -> "Week"
            ZoomLevel.MONTH -> "Month"
            ZoomLevel.THREE_MONTH -> "3 Months"
            ZoomLevel.YEAR -> "Year"
        }
}

object QuirkyVoice : Voice {
    override val bigPictureEmptyState = "It's quiet in here… suspiciously quiet."
    override val bigPictureEarlyDays = "Too soon to tell — feed me more moments!"

    override fun timeRangeLabel(zoomLevel: ZoomLevel): String =
        when (zoomLevel) {
            ZoomLevel.WEEK -> "Week"
            ZoomLevel.MONTH -> "Month"
            ZoomLevel.THREE_MONTH -> "3 Months"
            ZoomLevel.YEAR -> "Year"
        }
}

val LocalVoice = staticCompositionLocalOf<Voice> { SeriousVoice }
