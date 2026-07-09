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
            assertTrue(voice.newCaseTitle.isNotBlank())
            assertTrue(voice.editCaseTitle.isNotBlank())
            assertTrue(voice.newCaseFabDescription.isNotBlank())
            assertTrue(voice.backButtonDescription.isNotBlank())
            assertTrue(voice.caseNameLabel.isNotBlank())
            assertTrue(voice.caseNameHint.isNotBlank())
            assertTrue(voice.caseNameRequiredError.isNotBlank())
            assertTrue(voice.caseDescriptionLabel.isNotBlank())
            assertTrue(voice.caseDescriptionHint.isNotBlank())
            assertTrue(voice.caseIconLabel.isNotBlank())
            assertTrue(voice.caseIconRequiredError.isNotBlank())
            assertTrue(voice.caseLogFlowLabel.isNotBlank())
            assertTrue(voice.caseLogFlowOneTap.isNotBlank())
            assertTrue(voice.caseLogFlowDetailSheet.isNotBlank())
            assertTrue(voice.caseDurationModeLabel.isNotBlank())
            assertTrue(voice.caseDurationModeNone.isNotBlank())
            assertTrue(voice.caseDurationModeManual.isNotBlank())
            assertTrue(voice.caseDurationModeStartStop.isNotBlank())
            assertTrue(voice.caseIntensityToggleLabel.isNotBlank())
            assertTrue(voice.casePinnedToggleLabel.isNotBlank())
            assertTrue(voice.caseCheckInLabel.isNotBlank())
            assertTrue(voice.caseCheckInDefault.isNotBlank())
            assertTrue(voice.caseCheckInCustom.isNotBlank())
            assertTrue(voice.caseCheckInOff.isNotBlank())
            assertTrue(voice.caseCheckInCustomDaysHint.isNotBlank())
            assertTrue(voice.caseSaveButton.isNotBlank())
            assertTrue(voice.homeCaseCounts(todayCount = 0, weekCount = 0).isNotBlank())
            assertTrue(voice.homeCaseCounts(todayCount = 3, weekCount = 12).isNotBlank())
        }
    }
}
