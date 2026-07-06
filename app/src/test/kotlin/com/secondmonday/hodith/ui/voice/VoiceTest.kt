package com.secondmonday.hodith.ui.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    private val voices = listOf(SeriousVoice, GothVoice, QuirkyVoice)

    @Test
    fun `every voice has a non-blank string for every key`() {
        for (voice in voices) {
            assertTrue(voice.noCasesEmptyState.isNotBlank())
            assertTrue(voice.bigPictureEarlyDays.isNotBlank())
            assertTrue(voice.homeNavLabel.isNotBlank())
            assertTrue(voice.bigPictureNavLabel.isNotBlank())
            assertTrue(voice.settingsNavLabel.isNotBlank())
            assertTrue(voice.comingSoonPlaceholder.isNotBlank())
            assertTrue(voice.homeCaseCounts(todayCount = 0, weekCount = 0).isNotBlank())
            assertTrue(voice.homeCaseCounts(todayCount = 3, weekCount = 12).isNotBlank())
        }
    }
}
