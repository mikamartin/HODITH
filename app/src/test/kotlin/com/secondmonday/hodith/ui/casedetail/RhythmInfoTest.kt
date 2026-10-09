package com.secondmonday.hodith.ui.casedetail

import com.secondmonday.hodith.ui.voice.BrightVoice
import com.secondmonday.hodith.ui.voice.IntenseVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.ui.voice.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RhythmInfoTest {
    private val voices = listOf<Voice>(PlainVoice, IntenseVoice, BrightVoice)

    @Test
    fun `regular mode selects the Rhythm title and intro, not the Start-times ones`() {
        for (voice in voices) {
            assertEquals(voice.insightsRhythmInfoTitle, rhythmInfoTitle(voice, plottedByStart = false))
            assertEquals(voice.insightsRhythmInfoIntro, rhythmInfoIntro(voice, plottedByStart = false))
        }
    }

    @Test
    fun `start-times mode selects the Start-times title and intro, not the regular ones`() {
        for (voice in voices) {
            assertEquals(voice.insightsRhythmStartsInfoTitle, rhythmInfoTitle(voice, plottedByStart = true))
            assertEquals(voice.insightsRhythmStartsInfoIntro, rhythmInfoIntro(voice, plottedByStart = true))
            assertNotEquals(rhythmInfoTitle(voice, plottedByStart = true), rhythmInfoTitle(voice, plottedByStart = false))
        }
    }
}
