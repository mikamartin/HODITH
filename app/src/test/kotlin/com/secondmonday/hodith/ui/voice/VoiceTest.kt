package com.secondmonday.hodith.ui.voice

import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TrendDirection
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceTest {
    private val voices = listOf(PlainVoice, IntenseVoice, BrightVoice)

    @Test
    fun `every voice has a non-blank string for every key`() {
        for (voice in voices) {
            assertTrue(voice.homeHeaderTitle.isNotBlank())
            assertTrue(voice.noCasesEmptyState.isNotBlank())
            assertTrue(voice.bigPictureEarlyDays.isNotBlank())
            assertTrue(voice.bigPictureCasesFilterLabel.isNotBlank())
            assertTrue(voice.bigPictureTagsFilterLabel.isNotBlank())
            assertTrue(voice.bigPictureFilterCountAll.isNotBlank())
            assertTrue(voice.bigPictureAllCasesLabel.isNotBlank())
            assertTrue(voice.bigPictureAllTagsLabel.isNotBlank())
            assertTrue(voice.bigPictureUntaggedOnlyLabel.isNotBlank())
            assertTrue(voice.bigPictureNoCasesSelectedNote.isNotBlank())
            assertTrue(voice.bigPictureSelectAllAction.isNotBlank())
            assertTrue(voice.bigPictureClearAllAction.isNotBlank())
            assertTrue(voice.bigPictureFilterCount(selected = 2, total = 5).isNotBlank())
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
            assertTrue(voice.caseNameDuplicateError.isNotBlank())
            assertTrue(voice.caseDescriptionLabel.isNotBlank())
            assertTrue(voice.caseDescriptionHint.isNotBlank())
            assertTrue(voice.caseIconLabel.isNotBlank())
            assertTrue(voice.caseIconRequiredError.isNotBlank())
            assertTrue(voice.caseIconSectionExpandDescription.isNotBlank())
            assertTrue(voice.caseIconSectionCollapseDescription.isNotBlank())
            assertTrue(voice.caseSectionInfoDescription.isNotBlank())
            assertTrue(voice.infoDialogDismissAction.isNotBlank())
            assertTrue(voice.caseLogFlowLabel.isNotBlank())
            assertTrue(voice.caseLogFlowOneTap.isNotBlank())
            assertTrue(voice.caseLogFlowDetailSheet.isNotBlank())
            assertTrue(voice.caseLogFlowInfoTitle.isNotBlank())
            assertTrue(voice.caseLogFlowInfoBody.isNotBlank())
            assertTrue(voice.caseDurationModeLabel.isNotBlank())
            assertTrue(voice.caseDurationModeNone.isNotBlank())
            assertTrue(voice.caseDurationModeManual.isNotBlank())
            assertTrue(voice.caseDurationModeStartStop.isNotBlank())
            assertTrue(voice.caseDurationModeInfoTitle.isNotBlank())
            assertTrue(voice.caseDurationModeInfoBody.isNotBlank())
            assertTrue(voice.caseIntensityToggleLabel.isNotBlank())
            assertTrue(voice.caseCheckInLabel.isNotBlank())
            assertTrue(voice.caseCheckInInfoTitle.isNotBlank())
            assertTrue(voice.caseCheckInInfoBody.isNotBlank())
            assertTrue(voice.caseSaveButton.isNotBlank())
            assertTrue(voice.caseDetailEditDescription.isNotBlank())
            assertTrue(voice.archiveCaseDescription.isNotBlank())
            assertTrue(voice.archiveCaseConfirmTitle.isNotBlank())
            assertTrue(voice.archiveCaseConfirmBody.isNotBlank())
            assertTrue(voice.archiveCaseConfirmAction.isNotBlank())
            assertTrue(voice.archiveCaseCancelAction.isNotBlank())
            assertTrue(voice.archivedCasesTitle.isNotBlank())
            assertTrue(voice.archivedCasesEmptyState.isNotBlank())
            assertTrue(voice.eventListEmptyState.isNotBlank())
            assertTrue(voice.logSummaryLine(eventCount = 42, observedDays = 96).isNotBlank())
            assertTrue(voice.deleteEventConfirmTitle.isNotBlank())
            assertTrue(voice.deleteEventConfirmBody.isNotBlank())
            assertTrue(voice.deleteEventConfirmAction.isNotBlank())
            assertTrue(voice.deleteEventCancelAction.isNotBlank())
            assertTrue(voice.deleteCaseForeverConfirmTitle.isNotBlank())
            assertTrue(voice.deleteCaseForeverConfirmAction.isNotBlank())
            assertTrue(voice.deleteCaseForeverCancelAction.isNotBlank())
            assertTrue(voice.retroLogEntryDescription.isNotBlank())
            assertTrue(voice.logSheetNewEventTitle.isNotBlank())
            assertTrue(voice.logSheetEditEventTitle.isNotBlank())
            assertTrue(voice.logSheetTimeLabel.isNotBlank())
            assertTrue(voice.logSheetIntensityLabel.isNotBlank())
            assertTrue(voice.logSheetDurationLabel.isNotBlank())
            assertTrue(voice.logSheetDurationHint.isNotBlank())
            assertTrue(voice.logSheetNoteLabel.isNotBlank())
            assertTrue(voice.logSheetNoteHint.isNotBlank())
            assertTrue(voice.logSheetTagsLabel.isNotBlank())
            assertTrue(voice.logSheetAddTagHint.isNotBlank())
            assertTrue(voice.logSheetRemoveTagDescription.isNotBlank())
            assertTrue(voice.logSheetSaveButton.isNotBlank())
            assertTrue(voice.logSheetPickerConfirm.isNotBlank())
            assertTrue(voice.logSheetPickerCancel.isNotBlank())
            assertTrue(voice.logSheetStartButton.isNotBlank())
            assertTrue(voice.logSheetEndLabel.isNotBlank())
            assertTrue(voice.logSheetOngoingLabel.isNotBlank())
            assertTrue(voice.logSheetStopNowAction.isNotBlank())
            assertTrue(voice.staleOngoingEditEndTimeAction.isNotBlank())
            assertTrue(voice.staleOngoingStillGoingAction.isNotBlank())
            assertTrue(voice.quickLogUndoAction.isNotBlank())
            assertTrue(voice.settingsSupportSectionLabel.isNotBlank())
            assertTrue(voice.settingsRateAppButton.isNotBlank())
            assertTrue(voice.settingsContactUsButton.isNotBlank())
            assertTrue(voice.settingsAppearanceSectionLabel.isNotBlank())
            assertTrue(voice.settingsThemeSectionLabel.isNotBlank())
            assertTrue(voice.themeOptionPlain.isNotBlank())
            assertTrue(voice.themeOptionIntense.isNotBlank())
            assertTrue(voice.themeOptionBright.isNotBlank())
            assertTrue(voice.settingsThemeInfoTitle.isNotBlank())
            assertTrue(voice.settingsThemeInfoBody.isNotBlank())
            assertTrue(voice.settingsCheckInSectionLabel.isNotBlank())
            assertTrue(voice.checkInIntervalOptionOff.isNotBlank())
            assertTrue(voice.checkInIntervalOptionSeven.isNotBlank())
            assertTrue(voice.checkInIntervalOptionFourteen.isNotBlank())
            assertTrue(voice.checkInIntervalOptionThirty.isNotBlank())
            assertTrue(voice.settingsCheckInInfoTitle.isNotBlank())
            assertTrue(voice.settingsCheckInInfoBody.isNotBlank())
            assertTrue(voice.settingsDataSectionLabel.isNotBlank())
            assertTrue(voice.settingsExportButton.isNotBlank())
            assertTrue(voice.settingsImportButton.isNotBlank())
            assertTrue(voice.settingsDeveloperModeSectionLabel.isNotBlank())
            assertTrue(voice.settingsLoadDemoDataButton.isNotBlank())
            assertTrue(voice.settingsDemoDataLoadedMessage.isNotBlank())
            assertTrue(voice.settingsDeleteAllDataButton.isNotBlank())
            assertTrue(voice.settingsDeleteAllDataConfirmTitle.isNotBlank())
            assertTrue(voice.settingsDeleteAllDataConfirmBody.isNotBlank())
            assertTrue(voice.settingsDeleteAllDataConfirmAction.isNotBlank())
            assertTrue(voice.settingsDeleteAllDataCancelAction.isNotBlank())
            assertTrue(voice.aboutScreenTitle.isNotBlank())
            assertTrue(voice.aboutVersionLabel.isNotBlank())
            assertTrue(voice.aboutDeveloperModeUnlockedMessage.isNotBlank())
            assertTrue(voice.homeCaseCounts(todayCount = 0, weekCount = 0).isNotBlank())
            assertTrue(voice.homeCaseCounts(todayCount = 3, weekCount = 12).isNotBlank())
            assertTrue(voice.archivedCasesLink(count = 2).isNotBlank())
            assertTrue(voice.archivedCaseEventCount(count = 5).isNotBlank())
            assertTrue(voice.unarchiveCaseDescription(caseName = "Test Case").isNotBlank())
            assertTrue(voice.deleteCaseForeverDescription(caseName = "Test Case").isNotBlank())
            assertTrue(voice.deleteCaseForeverConfirmBody(eventCount = 5).isNotBlank())
            assertTrue(voice.eventIntensityLabel(intensity = 3).isNotBlank())
            assertTrue(voice.eventDurationLabel(duration = "45m").isNotBlank())
            assertTrue(voice.quickLogButtonDescription(caseName = "Test Case").isNotBlank())
            assertTrue(voice.quickLogUndoMessage(caseName = "Test Case").isNotBlank())
            assertTrue(voice.startActionDescription(caseName = "Test Case").isNotBlank())
            assertTrue(voice.stopActionDescription(caseName = "Test Case").isNotBlank())
            assertTrue(voice.ongoingIndicator(elapsed = "2h 14m").isNotBlank())
            assertTrue(voice.staleOngoingPromptMessage(caseName = "Test Case", elapsed = "1d 2h").isNotBlank())
            assertTrue(voice.hunchTabNoneTitle.isNotBlank())
            assertTrue(voice.hunchTabNoneBody.isNotBlank())
            assertTrue(voice.hunchAddButtonLabel.isNotBlank())
            assertTrue(voice.hunchNudgeTitle.isNotBlank())
            assertTrue(voice.hunchNudgeDismissAction.isNotBlank())
            assertTrue(voice.hunchEarlyHeadline.isNotBlank())
            assertTrue(voice.hunchResolveLabel.isNotBlank())
            assertTrue(voice.hunchCreatingTitle.isNotBlank())
            assertTrue(voice.hunchCreatingDirectionLabel.isNotBlank())
            assertTrue(voice.hunchCreatingFreqLabel.isNotBlank())
            assertTrue(voice.hunchCreatingFreqSuffix.isNotBlank())
            assertTrue(voice.hunchCreatingSaveButton.isNotBlank())
            assertTrue(voice.hunchCreatingDecreaseCountDescription.isNotBlank())
            assertTrue(voice.hunchCreatingIncreaseCountDescription.isNotBlank())
            assertTrue(voice.hunchHistoryHeader.isNotBlank())
            assertTrue(voice.hunchExpectedPerDay.isNotBlank())
            assertTrue(voice.hunchExpectedPerWeek.isNotBlank())
            assertTrue(voice.hunchExpectedPerMonth.isNotBlank())
            assertTrue(voice.caseDetailLogTabLabel.isNotBlank())
            assertTrue(voice.caseDetailInsightsTabLabel.isNotBlank())
            assertTrue(voice.caseDetailHunchTabLabel.isNotBlank())
            assertTrue(voice.insightsNotEnoughDataMessage.isNotBlank())
            assertTrue(voice.insightsSectionLabelHeatmap.isNotBlank())
            assertTrue(voice.insightsHeatmapShowMoreAction.isNotBlank())
            assertTrue(voice.insightsHeatmapShowFewerAction.isNotBlank())
            assertTrue(voice.insightsSectionLabelFrequency.isNotBlank())
            assertTrue(voice.insightsSectionLabelRhythm.isNotBlank())
            assertTrue(voice.insightsSectionLabelGaps.isNotBlank())
            assertTrue(voice.insightsSectionLabelTrend.isNotBlank())
            assertTrue(voice.insightsSectionLabelDuration.isNotBlank())
            assertTrue(voice.insightsSectionLabelIntensity.isNotBlank())
            assertTrue(voice.insightsSectionLabelTags.isNotBlank())
            assertTrue(voice.insightsTagsTotalLabel.isNotBlank())
            assertTrue(voice.insightsGapsLongestLabel.isNotBlank())
            assertTrue(voice.insightsGapsCurrentLabel.isNotBlank())
            assertTrue(voice.insightsGapsAverageLabel.isNotBlank())
            assertTrue(voice.insightsStreakLongestLabel.isNotBlank())
            assertTrue(voice.insightsStreakAverageLabel.isNotBlank())
            assertTrue(voice.insightsBurstFlagLabel.isNotBlank())
            assertTrue(voice.insightsDurationAverageLabel.isNotBlank())
            assertTrue(voice.insightsDurationLongestLabel.isNotBlank())
            assertTrue(voice.insightsDurationTotalLabel.isNotBlank())
            assertTrue(voice.insightsIntensityAverageLabel.isNotBlank())
            assertTrue(voice.insightsFrequencyGranularityDay.isNotBlank())
            assertTrue(voice.insightsFrequencyGranularityWeek.isNotBlank())
            assertTrue(voice.insightsFrequencyGranularityMonth.isNotBlank())
            assertTrue(voice.insightsTimeOfDayMorning.isNotBlank())
            assertTrue(voice.insightsTimeOfDayAfternoon.isNotBlank())
            assertTrue(voice.insightsTimeOfDayEvening.isNotBlank())
            assertTrue(voice.insightsTimeOfDayNight.isNotBlank())
            assertTrue(voice.insightsFrequencyInfoTitle.isNotBlank())

            for (granularity in FrequencyGranularity.entries) {
                assertTrue(voice.insightsFrequencyInfoBody(granularity).isNotBlank())
            }

            for (direction in TrendDirection.entries) {
                assertTrue(voice.insightsTrendSentence(direction, recentCount = 12, priorCount = 18).isNotBlank())
            }
            for (direction in ShiftDirection.entries) {
                assertTrue(voice.insightsGapShiftSentence(direction).isNotBlank())
                assertTrue(voice.insightsStreakShiftSentence(direction).isNotBlank())
            }
            assertTrue(voice.hunchNudgeBody(caseIcon = "🐛", caseName = "Test Case").isNotBlank())
            assertTrue(voice.hunchProgressLabel(eventCount = 3, windowDays = 9).isNotBlank())
            assertTrue(voice.hunchHistorySummary(total = 3, heldUpCount = 1).isNotBlank())
            assertTrue(voice.hunchHistoryRowWhen(monthsAgo = 4).isNotBlank())
            assertTrue(voice.hunchEarlyBadgeLabel.isNotBlank())

            for (direction in HunchDirection.entries) {
                assertTrue(voice.hunchDirectionPillLabel(direction).isNotBlank())
                assertTrue(voice.hunchChipLabel(direction, expectedFrequencyLabel = "~5×/week").isNotBlank())
                assertTrue(voice.hunchHistoryRowText(direction, expectedFrequencyLabel = "~5×/week").isNotBlank())
                for (band in ComparisonBand.entries) {
                    assertTrue(voice.verdictHeadline(direction, band, observedRateLabel = "2.1×/week").isNotBlank())
                }
            }

            for (band in ComparisonBand.entries) {
                assertTrue(voice.hunchHistoryRowOutcome(band, observedRateLabel = "2.1×/week").isNotBlank())
            }

            for (tier in ConfidenceTier.entries) {
                assertTrue(voice.verdictMeta(tier, eventCount = 15, windowDays = 50).isNotBlank())
                assertTrue(voice.hunchTierBadgeLabel(tier).isNotBlank())
            }

            assertTrue(voice.notificationChannelName.isNotBlank())
            assertTrue(voice.notificationChannelDescription.isNotBlank())
            assertTrue(voice.triggerFiredNotificationTitle(caseName = "Test Case").isNotBlank())
            assertTrue(voice.checkInDueNotificationTitle(caseName = "Test Case").isNotBlank())
            assertTrue(voice.checkInDueNotificationBody(silentDays = 9).isNotBlank())
            assertTrue(voice.notificationsDeniedBannerMessage.isNotBlank())
            assertTrue(voice.notificationsDeniedBannerAction.isNotBlank())

            assertTrue(voice.shareOpenDescription.isNotBlank())
            assertTrue(voice.shareRealityKicker.isNotBlank())
            assertTrue(voice.shareRealityEventsLabel.isNotBlank())
            assertTrue(voice.shareRealityDaysObservedLabel.isNotBlank())
            assertTrue(voice.shareHunchRealityKicker.isNotBlank())
            assertTrue(voice.shareHunchExpectedLabel.isNotBlank())
            assertTrue(voice.shareHunchObservedLabel.isNotBlank())
            assertTrue(voice.shareCardFooter.isNotBlank())
            assertTrue(voice.shareIntenseStampLabel.isNotBlank())
            assertTrue(voice.shareFormatStoryLabel.isNotBlank())
            assertTrue(voice.shareFormatSquareLabel.isNotBlank())
            assertTrue(voice.shareHunchVsRealityToggleLabel.isNotBlank())
            assertTrue(voice.shareNameFieldLabel.isNotBlank())
            assertTrue(voice.shareSectionsPickerLabel.isNotBlank())

            for (granularity in FrequencyGranularity.entries) {
                assertTrue(voice.shareFrequencyTitle(granularity).isNotBlank())
            }

            for (direction in HunchDirection.entries) {
                for (band in ComparisonBand.entries) {
                    assertTrue(voice.sharePunchline(direction, band).isNotBlank())
                }
            }
        }
    }

    @Test
    fun `sharePunchline never uses first- or second-person pronouns`() {
        // Share cards are viewed by whoever the card is shared with, not just the user who made
        // the Hunch — "you"/"your"/"I"/"my" would address the wrong audience once it leaves the app.
        val pronounPattern = Regex("""\b(I|I'm|I've|I'd|you|your|you're|you've|you'd|my)\b""", RegexOption.IGNORE_CASE)

        for (voice in voices) {
            for (direction in HunchDirection.entries) {
                for (band in ComparisonBand.entries) {
                    val punchline = voice.sharePunchline(direction, band)
                    assertTrue(
                        "Expected no first/second-person pronoun in \"$punchline\" ($voice, $direction/$band)",
                        !pronounPattern.containsMatchIn(punchline),
                    )
                }
            }
        }
    }
}
