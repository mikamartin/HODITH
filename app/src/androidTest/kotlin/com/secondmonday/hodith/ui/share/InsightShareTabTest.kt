package com.secondmonday.hodith.ui.share

import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.LogShareSelection
import com.secondmonday.hodith.viewmodel.LogShareUiState
import com.secondmonday.hodith.viewmodel.ShareSelection
import com.secondmonday.hodith.viewmodel.ShareUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

/** The Insights tab (Story) is the one with the section picker; tests of the picker select that tab explicitly. */
private val STORY_SELECTION = ShareSelection()

private fun tabLabel(tab: ShareTab): String =
    when (tab) {
        ShareTab.SUMMARY -> PlainVoice.shareTabSummaryLabel
        ShareTab.INSIGHTS -> PlainVoice.shareTabInsightsLabel
        ShareTab.HISTORY -> PlainVoice.shareTabHistoryLabel
    }

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

/** 12 events, 5 days apart — enough for the Trends section to have findings. */
private fun trendsEligibleEvents(): List<EventWithTags> =
    (0..55L step 5).map { day -> EventWithTags(testEvent(caseId = 1L, occurredAt = millisAtDay(day)), emptyList()) }

/** 12 events, 5 days apart, none carrying a duration, intensity or tag. */
private fun bareEvents(): List<EventWithTags> = trendsEligibleEvents()

/**
 * 12 events, 5 days apart, each lasting 30 minutes at intensity 3. Tags: "alpha" on all, "beta" on
 * the first three, "gamma" on the first two, "delta" on the first, so alpha, beta and gamma are the top three.
 */
private fun richEvents(): List<EventWithTags> =
    (0..55L step 5).mapIndexed { index, day ->
        val event = testEvent(caseId = 1L, occurredAt = millisAtDay(day), endedAt = millisAtDay(day) + 30 * 60_000L, intensity = 3)
        val tagNames = listOfNotNull("alpha", "beta".takeIf { index < 3 }, "gamma".takeIf { index < 2 }, "delta".takeIf { index < 1 })
        EventWithTags(event, tagNames.map { TagEntity(name = it) })
    }

/** A Case that tracks both duration and intensity. */
private fun trackingCase() = testCase(id = 1L, durationMode = DurationMode.MANUAL, intensityEnabled = true)

/** The hero's observed line contains this under every voice, so the hero is findable without knowing the exact day count. */
private const val OBSERVED_LINE_FRAGMENT = "d observed"

/**
 * Vertical position of the card's copy of [title]. The picker rows carry the same labels as the card's titles and sit above the
 * card, so the card's copy is the last match in tree order.
 */
internal fun ComposeContentTestRule.cardTitleTop(title: String): Float =
    onAllNodesWithText(title)
        .fetchSemanticsNodes()
        .last()
        .positionInRoot.y

/**
 * [ShareScreen] needs a real `GraphicsLayer` (tied to composition) for the capture modifier, same reason
 * `ShareCardTemplate` itself needed [UiTest] rather than a plain unit test. Drives the stateless host with fake callbacks
 * and selects the tab under test by clicking its label.
 */
@UiTest
class InsightShareTabTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: ShareUiState,
        now: Long = millisAtDay(60),
        tab: ShareTab = ShareTab.SUMMARY,
        onSectionToggle: (ShareInsightsSection, Boolean) -> Unit = { _, _ -> },
    ) {
        composeTestRule.setHodithContent {
            ShareScreen(
                insightState = uiState,
                logState =
                    LogShareUiState(
                        case = uiState.case,
                        events = uiState.events,
                        selection = LogShareSelection(dateTo = now),
                        isLoading = uiState.isLoading,
                    ),
                now = now,
                graphicsLayer = rememberGraphicsLayer(),
                onBack = {},
                onSectionToggle = onSectionToggle,
                onSectionMove = { _, _, _ -> },
                onInsightShareClick = {},
                onDateFromPicked = {},
                onDateToPicked = {},
                onFieldToggle = { _, _ -> },
                onLogSortOrderSelect = {},
                onLogShareClick = {},
            )
        }
        if (tab != ShareTab.SUMMARY) composeTestRule.onNodeWithText(tabLabel(tab)).performClick()
    }

    @Smoke
    @Test
    fun tabs_summaryInsightsAndHistory_withSummaryOpenFirst() {
        setContent(uiState = ShareUiState(case = testCase(id = 1L), events = emptyList(), isLoading = false))

        composeTestRule.onNodeWithText(PlainVoice.shareTabSummaryLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareTabInsightsLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareTabHistoryLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.sharePreviewLabel).assertExists()
    }

    @Test
    fun squareSelection_rendersTheSummaryHeroAndItsPanels() {
        setContent(
            uiState =
                ShareUiState(
                    case = testCase(id = 1L),
                    events = trendsEligibleEvents(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(OBSERVED_LINE_FRAGMENT, substring = true).assertExists()
        composeTestRule.onAllNodesWithText(PlainVoice.shareGapsTitle).assertCountEquals(1)
    }

    @Test
    fun storySelection_rendersTheSameSummaryHeroAndThePickedPanels() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState())

        composeTestRule.onNodeWithText(OBSERVED_LINE_FRAGMENT, substring = true).assertExists()
        // The picker row and the card's own panel title are the same word: two nodes, not one.
        composeTestRule.onAllNodesWithText(PlainVoice.shareGapsTitle).assertCountEquals(2)
    }

    @Test
    fun storySelection_withNothingPicked_rendersTheHeroAlone() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(selection = STORY_SELECTION.copy(selectedSections = emptySet())))

        composeTestRule.onNodeWithText(OBSERVED_LINE_FRAGMENT, substring = true).assertExists()
        // Only the picker row remains; the card has no Gaps panel.
        composeTestRule.onAllNodesWithText(PlainVoice.shareGapsTitle).assertCountEquals(1)
    }

    @Test
    fun screenTitle_isShare_andTheShareButtonReadsTheSameWordInPlain() {
        setContent(uiState = ShareUiState(case = testCase(id = 1L), events = emptyList(), isLoading = false))

        // In Plain the title and the Share button are both "Share", so the exact-text lookup finds two nodes.
        composeTestRule.onAllNodesWithText(PlainVoice.shareScreenTitle).assertCountEquals(2)
    }

    private fun storyState(
        case: CaseEntity = testCase(id = 1L),
        events: List<EventWithTags> = trendsEligibleEvents(),
        selection: ShareSelection = STORY_SELECTION,
    ) = ShareUiState(case = case, events = events, selection = selection, isLoading = false)

    private fun rowTag(section: ShareInsightsSection) = SECTION_TOGGLE_TAG_PREFIX + section.name

    @Test
    fun sectionPicker_isShownForStory() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState())

        composeTestRule.onNodeWithText(PlainVoice.shareSectionsPickerLabel).assertExists()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.RHYTHM)).assertExists()
    }

    @Test
    fun card_listsStorySectionsInTheSavedOrder() {
        val order =
            listOf(ShareInsightsSection.TAGS, ShareInsightsSection.GAPS) +
                (ShareInsightsSection.entries - ShareInsightsSection.TAGS - ShareInsightsSection.GAPS)
        setContent(
            tab = ShareTab.INSIGHTS,
            uiState =
                ShareUiState(
                    case = trackingCase(),
                    events = richEvents(),
                    selection = STORY_SELECTION,
                    sectionOrder = order,
                    isLoading = false,
                ),
        )

        assertTrue(composeTestRule.cardTitleTop(PlainVoice.shareTopTagsTitle) < composeTestRule.cardTitleTop(PlainVoice.shareGapsTitle))
    }

    @Test
    fun sectionPicker_hasADragHandleOnAlwaysAvailableRows() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState())

        composeTestRule.onNodeWithTag(SECTION_HANDLE_TAG_PREFIX + ShareInsightsSection.GAPS.name).assertExists()
        composeTestRule.onNodeWithTag(SECTION_HANDLE_TAG_PREFIX + ShareInsightsSection.RHYTHM.name).assertExists()
    }

    @Test
    fun sectionPicker_isHiddenBeforeTheFirstEvent_becauseThereIsNothingToPick() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = emptyList()))

        composeTestRule.onNodeWithText(PlainVoice.shareSectionsPickerLabel).assertDoesNotExist()
        ShareInsightsSection.entries.forEach { composeTestRule.onNodeWithTag(rowTag(it)).assertDoesNotExist() }
    }

    @Test
    fun sectionPicker_isHiddenOnTheSummaryTab_whateverTheCaseTracks() {
        setContent(
            uiState =
                storyState(
                    case = testCase(id = 1L, durationMode = DurationMode.MANUAL, intensityEnabled = true),
                    events = richEvents(),
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareSectionsPickerLabel).assertDoesNotExist()
        ShareInsightsSection.entries.forEach { composeTestRule.onNodeWithTag(rowTag(it)).assertDoesNotExist() }
    }

    @Test
    fun sectionPicker_forABareCase_offersOnlyGapsAndStartTimes() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = bareEvents()))

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.GAPS)).assertExists()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.RHYTHM)).assertExists()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.DURATION)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.INTENSITY)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.TAGS)).assertDoesNotExist()
    }

    @Test
    fun lengthAndIntensityRows_appearWhenTheCaseTracksThemAndHasData() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(case = trackingCase(), events = richEvents()))

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.DURATION)).assertExists()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.INTENSITY)).assertExists()
    }

    @Test
    fun lengthAndIntensityRows_stayHiddenWhenTheCaseTracksThemButNothingWasLogged() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(case = trackingCase(), events = bareEvents()))

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.DURATION)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.INTENSITY)).assertDoesNotExist()
    }

    @Test
    fun lengthAndIntensityRows_stayHiddenWhenTheCaseDoesNotTrackThemEvenIfEventsCarryData() {
        setContent(
            tab = ShareTab.INSIGHTS,
            uiState =
                storyState(
                    case = testCase(id = 1L, durationMode = DurationMode.NONE, intensityEnabled = false),
                    events = richEvents(),
                ),
        )

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.DURATION)).assertDoesNotExist()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.INTENSITY)).assertDoesNotExist()
    }

    @Test
    fun lengthRow_appearsWithoutAnIntensityRow_whenOnlyDurationIsTracked() {
        setContent(
            tab = ShareTab.INSIGHTS,
            uiState =
                storyState(
                    case = testCase(id = 1L, durationMode = DurationMode.MANUAL, intensityEnabled = false),
                    events = richEvents(),
                ),
        )

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.DURATION)).assertExists()
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.INTENSITY)).assertDoesNotExist()
    }

    @Test
    fun tagsRow_appearsWhenAnEventCarriesATag() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = richEvents()))

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.TAGS)).assertExists()
    }

    @Test
    fun tagsRow_hiddenWhenNoEventCarriesATag() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = trendsEligibleEvents()))

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.TAGS)).assertDoesNotExist()
    }

    @Test
    fun tagsSection_showsTheThreeBusiestTagsOnTheCard_andLeavesTheFourthOut() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = richEvents()))

        composeTestRule.onNodeWithText("alpha").assertExists()
        composeTestRule.onNodeWithText("beta").assertExists()
        composeTestRule.onNodeWithText("gamma").assertExists()
        composeTestRule.onNodeWithText("delta").assertDoesNotExist()
    }

    @Test
    fun tagsSection_isLeftOffTheCardWhenNotPicked() {
        setContent(
            tab = ShareTab.INSIGHTS,
            uiState =
                storyState(
                    events = richEvents(),
                    selection = STORY_SELECTION.copy(selectedSections = ShareInsightsSection.entries.toSet() - ShareInsightsSection.TAGS),
                ),
        )

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.TAGS)).assertExists()
        composeTestRule.onNodeWithText("alpha").assertDoesNotExist()
    }

    @Test
    fun trendsRow_hiddenWhenNoTrendsFindingsExist() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = emptyList()))

        // By tag, not by the "Trends" label text: with findings present, that text also appears
        // in the live card preview above (same reason sectionChecklist_togglingARow_... uses the
        // tag, not the Start times label, further down this file).
        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.TRENDS)).assertDoesNotExist()
    }

    @Test
    fun trendsRow_appearsWhenTrendsFindingsExist() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = trendsEligibleEvents()))

        composeTestRule.onNodeWithTag(rowTag(ShareInsightsSection.TRENDS)).assertExists()
    }

    @Test
    fun pickerRows_areLabelledWithTheirCardSectionTitles() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(case = trackingCase(), events = richEvents()))

        mapOf(
            ShareInsightsSection.GAPS to PlainVoice.shareGapsTitle,
            ShareInsightsSection.STREAKS to PlainVoice.shareStreaksTitle,
            ShareInsightsSection.DURATION to PlainVoice.shareDurationTitle,
            ShareInsightsSection.RHYTHM to PlainVoice.insightsSectionLabelRhythmStarts,
            ShareInsightsSection.INTENSITY to PlainVoice.insightsSectionLabelIntensity,
            ShareInsightsSection.TRENDS to PlainVoice.insightsSectionLabelTrends,
            ShareInsightsSection.TAGS to PlainVoice.shareTopTagsTitle,
        ).forEach { (section, title) ->
            composeTestRule.onNodeWithTag(rowTag(section)).assertTextContains(title)
        }
    }

    @Test
    fun pickerRows_listGapsStreaksLengthStartTimesIntensityTrendsTagsInThatOrder() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(case = trackingCase(), events = richEvents()))

        val tops =
            listOf(
                ShareInsightsSection.GAPS,
                ShareInsightsSection.STREAKS,
                ShareInsightsSection.DURATION,
                ShareInsightsSection.RHYTHM,
                ShareInsightsSection.INTENSITY,
                ShareInsightsSection.TRENDS,
                ShareInsightsSection.TAGS,
            ).map { composeTestRule.onNodeWithTag(rowTag(it)).getUnclippedBoundsInRoot().top }

        assertTrue("Expected rows top to bottom in card order, got tops=$tops", tops.zipWithNext().all { (above, below) -> above < below })
    }

    @Test
    fun pickerRows_neverOfferFrequency() {
        setContent(tab = ShareTab.INSIGHTS, uiState = storyState(events = richEvents()))

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelFrequency).assertDoesNotExist()
    }

    @Test
    fun sectionChecklist_togglingARow_invokesCallbackWithTheSection() {
        var toggled: Pair<ShareInsightsSection, Boolean>? = null
        setContent(
            tab = ShareTab.INSIGHTS,
            uiState = storyState(),
            onSectionToggle = { section, selected -> toggled = section to selected },
        )

        // By tag, not by the "Start times" label text: that text can also appear in the live card
        // preview elsewhere on the screen once it has real data, so the tag is the only
        // unambiguous target. performScrollTo() first: the screen's a scrolling Column and this
        // row can sit below the fold, and the v2 test API's performClick() needs the target
        // actually reachable, not just present in the semantics tree.
        composeTestRule
            .onNodeWithTag(rowTag(ShareInsightsSection.RHYTHM))
            .performScrollTo()
            .performClick()

        assertEquals(ShareInsightsSection.RHYTHM to false, toggled)
    }
}
