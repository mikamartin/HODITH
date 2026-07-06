package com.secondmonday.hodith.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One user-visible string per key, in three personalities (spec §12). Composables read
 * [LocalVoice] instead of branching on theme, so a string can never ship in only one voice.
 * Minimal key set for now — only what Big Picture needs; Phase 4 extends this interface with
 * the full string set rather than replacing it.
 */
interface Voice {
    val bigPictureEmptyState: String
    val bigPictureEarlyDays: String
}

object SeriousVoice : Voice {
    override val bigPictureEmptyState = "No cases yet."
    override val bigPictureEarlyDays = "Insufficient data. Keep logging."
}

object GothVoice : Voice {
    override val bigPictureEmptyState = "Nothing is being watched. Yet."
    override val bigPictureEarlyDays = "The evidence is yet insufficient for despair."
}

object QuirkyVoice : Voice {
    override val bigPictureEmptyState = "It's quiet in here… suspiciously quiet."
    override val bigPictureEarlyDays = "Too soon to tell — feed me more moments!"
}

val LocalVoice = staticCompositionLocalOf<Voice> { SeriousVoice }
