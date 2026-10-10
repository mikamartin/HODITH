package com.secondmonday.hodith.ui.share

import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HistoryRowField
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.DATE_RANGE_ALL_TIME_BUTTON_TAG
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.HistoryShareSelection
import com.secondmonday.hodith.viewmodel.HistoryShareUiState
import com.secondmonday.hodith.viewmodel.ShareUiState
import com.secondmonday.hodith.viewmodel.formatDateRangeBound
import com.secondmonday.hodith.viewmodel.toLocalDateIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/**
 * The History tab of [ShareScreen]. Needs a real `GraphicsLayer` for the capture modifier, same reason
 * [InsightShareTabTest] needs [UiTest] rather than a plain unit test. Drives the stateless host with fake
 * callbacks, then opens the History tab.
 */
@UiTest
class HistoryShareTabTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: HistoryShareUiState,
        now: Long = millisAtDay(60),
        onSortOrderSelect: (ChronologicalOrder) -> Unit = {},
        onDateFromPicked: (LocalDate?) -> Unit = {},
        onDateToPicked: (LocalDate) -> Unit = {},
        onFieldToggle: (HistoryRowField, Boolean) -> Unit = { _, _ -> },
        onShareClick: () -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            ShareScreen(
                insightState = ShareUiState(case = uiState.case, events = uiState.events, isLoading = uiState.isLoading),
                historyState = uiState,
                now = now,
                graphicsLayer = rememberGraphicsLayer(),
                onBack = {},
                onSectionToggle = { _, _ -> },
                onSectionMove = { _, _, _ -> },
                onInsightShareClick = {},
                onDateFromPicked = onDateFromPicked,
                onDateToPicked = onDateToPicked,
                onFieldToggle = onFieldToggle,
                onHistorySortOrderSelect = onSortOrderSelect,
                onHistoryShareClick = onShareClick,
            )
        }
        composeTestRule.onNodeWithText(PlainVoice.shareTabHistoryLabel).performClick()
    }

    private fun defaultSelection(dateTo: Long = millisAtDay(60)) = HistoryShareSelection(dateTo = dateTo)

    /**
     * The range button's exact text, including its "Range: " prefix. The live card preview below
     * renders its own range subtitle with the same bare value and no prefix (`historyShareRangeLabel`
     * shares `formatDateRangeBound`/`Voice.shareHistoryRangeNote` with this button, so the bare value
     * is now identical) -- matching on the bare value alone would be ambiguous, so the prefix is
     * what makes this the button specifically.
     */
    private fun rangeButtonText(value: String) = "${PlainVoice.shareHistoryRangeLabel}: $value"

    @Smoke
    @Test
    fun nameField_typing_keepsTheTypedTextInTheField() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNode(hasSetTextAction()).performTextClearance()
        composeTestRule.onNode(hasSetTextAction()).performTextInput("Sam")

        assertEquals("Sam", composeTestRule.nameFieldText())
    }

    @Test
    fun nameField_showsTheCaseNameByDefault() {
        val case = testCase(id = 1L)
        setContent(
            uiState = HistoryShareUiState(case = case, events = emptyList(), selection = defaultSelection(), isLoading = false),
        )

        assertEquals(case.name, composeTestRule.nameFieldText())
    }

    @Test
    fun nameField_clearing_leavesTheFieldEmpty() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNode(hasSetTextAction()).performTextClearance()

        assertEquals("", composeTestRule.nameFieldText())
    }

    @Test
    fun nameField_isAboveTheSortControl() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        val nameTop =
            composeTestRule
                .onNodeWithText(PlainVoice.shareNameFieldLabel)
                .fetchSemanticsNode()
                .positionInRoot.y
        val sortTop =
            composeTestRule
                .onNodeWithText(PlainVoice.shareHistorySortOldestLabel)
                .fetchSemanticsNode()
                .positionInRoot.y

        assertTrue("Name field ($nameTop) should sit above the sort control ($sortTop)", nameTop < sortTop)
    }

    @Test
    fun sortToggle_selectingOldestFirst_invokesCallback() {
        var selected: ChronologicalOrder? = null
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
            onSortOrderSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistorySortOldestLabel).performScrollTo().performClick()

        assertEquals(ChronologicalOrder.OLDEST_FIRST, selected)
    }

    @Test
    fun fieldRows_durationAndIntensity_onlyAppearWhenTheCaseTracksThem() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.NONE, intensityEnabled = false),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.shareHistoryFieldNotesLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareHistoryFieldTagsLabel).assertExists()
    }

    @Test
    fun fieldRows_durationAndIntensity_appearWhenTheCaseTracksThem() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.MANUAL, intensityEnabled = true),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertExists()
    }

    @Test
    fun fieldToggle_unchecking_invokesCallbackWithFieldAndFalse() {
        var toggled: Pair<HistoryRowField, Boolean>? = null
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.NONE, intensityEnabled = false),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
            onFieldToggle = { field, selected -> toggled = field to selected },
        )

        // By tag, not by label text: the label/Switch semantics don't reliably merge into one
        // clickable node in every context (see HISTORY_SHARE_FIELD_TOGGLE_TAG_PREFIX's own doc comment).
        composeTestRule.onNodeWithTag(HISTORY_SHARE_FIELD_TOGGLE_TAG_PREFIX + HistoryRowField.NOTES.name).performClick()

        assertEquals(HistoryRowField.NOTES to false, toggled)
    }

    @Test
    fun shareButton_click_invokesCallback() {
        var clicked = false
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
            onShareClick = { clicked = true },
        )

        composeTestRule.onNode(hasText(PlainVoice.shareOpenDescription) and hasClickAction()).performClick()

        assert(clicked)
    }

    @Test
    fun emptyRangeMessage_showsOnTheCardPreview_whenNoEventsMatch() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHistoryEmptyRangeMessage).assertExists()
    }

    @Test
    fun rangeButton_defaultsToAllTime() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareHistoryRangeAllTimeLabel)).assertExists()
    }

    @Test
    fun rangeButton_showsFormattedBounds_whenRangeIsNarrowed() {
        val from = millisAtDay(20)
        val to = millisAtDay(40)
        val now = millisAtDay(60)
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(dateTo = to).copy(dateFrom = from),
                    isLoading = false,
                ),
        )

        composeTestRule
            .onNodeWithText(
                rangeButtonText(
                    PlainVoice.shareHistoryRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).assertExists()
    }

    @Test
    fun rangeButton_tap_opensOneCombinedRangeDialog() {
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareHistoryRangeAllTimeLabel)).performClick()

        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerCancel).assertExists()
    }

    @Test
    fun rangeDialog_confirmWithoutChanges_roundTripsTheExistingRangeToBothCallbacks() {
        // Same regression guard as CaseDetailScreenTest's equivalent: the dialog must seed itself
        // from the current selection and hand both ends back out on Confirm, not just open.
        val from = millisAtDay(20)
        val to = millisAtDay(40)
        val now = millisAtDay(60)
        var changedFrom: LocalDate? = null
        var changedTo: LocalDate? = null
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(dateTo = to).copy(dateFrom = from),
                    isLoading = false,
                ),
            onDateFromPicked = { changedFrom = it },
            onDateToPicked = { changedTo = it },
        )

        composeTestRule
            .onNodeWithText(
                rangeButtonText(
                    PlainVoice.shareHistoryRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        assertEquals(from.toLocalDateIn(ZONE), changedFrom)
        assertEquals(to.toLocalDateIn(ZONE), changedTo)
    }

    @Test
    fun rangeDialog_tappingAStartAndEndDay_handsBothDaysBack() {
        // The shared range dialog (the History tab's From/To chips use it too) takes day taps, not
        // just Confirm: a start day then an end day in the displayed month reach both callbacks.
        val from = millisAtDay(20)
        val to = millisAtDay(40)
        val now = millisAtDay(60)
        var changedFrom: LocalDate? = null
        var changedTo: LocalDate? = null
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(dateTo = to).copy(dateFrom = from),
                    isLoading = false,
                ),
            onDateFromPicked = { changedFrom = it },
            onDateToPicked = { changedTo = it },
        )

        composeTestRule
            .onNodeWithText(
                rangeButtonText(
                    PlainVoice.shareHistoryRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).performClick()
        // Day cells expose their full date as their text, not the bare day number.
        composeTestRule.onNodeWithText("Thursday, January 15, 1970").performClick()
        composeTestRule.onNodeWithText("Friday, January 16, 1970").performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        assertEquals(LocalDate.of(1970, 1, 15), changedFrom)
        assertEquals(LocalDate.of(1970, 1, 16), changedTo)
    }

    @Test
    fun rangeDialog_allTimeTap_clearsBothBoundsInOneTapAndClosesTheDialog() {
        val from = millisAtDay(20)
        val to = millisAtDay(40)
        val now = millisAtDay(60)
        var changedFrom: LocalDate? = from.toLocalDateIn(ZONE)
        var changedTo: LocalDate? = to.toLocalDateIn(ZONE)
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(dateTo = to).copy(dateFrom = from),
                    isLoading = false,
                ),
            onDateFromPicked = { changedFrom = it },
            onDateToPicked = { changedTo = it },
        )

        composeTestRule
            .onNodeWithText(
                rangeButtonText(
                    PlainVoice.shareHistoryRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).performClick()
        composeTestRule.onNodeWithTag(DATE_RANGE_ALL_TIME_BUTTON_TAG).performClick()

        assertNull(changedFrom)
        assertEquals(now.toLocalDateIn(ZONE), changedTo)
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).assertDoesNotExist()
    }

    @Test
    fun rangeDialog_allTimeButton_hiddenWhenNothingIsFilteredYet() {
        // The range button's own label already reads "All time" when unfiltered, and the
        // live card preview below repeats the bare value -- by tag, not by label text, so this
        // doesn't collide with either.
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareHistoryRangeAllTimeLabel)).performClick()

        composeTestRule.onNodeWithTag(DATE_RANGE_ALL_TIME_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun rangeDialog_confirmWithNoStartSelected_clearsFromToSinceTheBeginning() {
        // dateFrom starts null (since-the-beginning) in the default selection -- confirming
        // without touching the calendar should keep it null, not coerce it to some other value.
        var changedFrom: LocalDate? = LocalDate.of(2026, 1, 1)
        setContent(
            uiState =
                HistoryShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
            onDateFromPicked = { changedFrom = it },
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareHistoryRangeAllTimeLabel)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        assertNull(changedFrom)
    }
}
