package com.secondmonday.hodith.ui.voice

import com.secondmonday.hodith.domain.timeline.ZoomLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    private val voices = listOf(SeriousVoice, GothVoice, QuirkyVoice)

    @Test
    fun `every voice has a non-blank string for every key`() {
        for (voice in voices) {
            assertTrue(voice.bigPictureEmptyState.isNotBlank())
            assertTrue(voice.bigPictureEarlyDays.isNotBlank())
            for (zoomLevel in ZoomLevel.entries) {
                assertTrue(voice.timeRangeLabel(zoomLevel).isNotBlank())
            }
        }
    }
}
