package com.secondmonday.hodith.ui.share

import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogRowField
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.domain.ChronologicalOrder
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.DATE_RANGE_ALL_TIME_BUTTON_TAG
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.LogShareSelection
import com.secondmonday.hodith.viewmodel.LogShareUiState
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.formatDateRangeBound
import com.secondmonday.hodith.viewmodel.toLocalDateIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

/**
 * [LogSharePreviewScreen] needs a real `GraphicsLayer` for the capture modifier, same reason
 * [SharePreviewScreenTest] needs [UiTest] rather than a plain unit test. Mirrors that file's
 * pattern of driving the stateless screen directly with fake callbacks.
 */
@UiTest
class LogSharePreviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: LogShareUiState,
        now: Long = millisAtDay(60),
        onFormatSelect: (ShareCardFormat) -> Unit = {},
        onSortOrderSelect: (ChronologicalOrder) -> Unit = {},
        onDateFromPicked: (LocalDate?) -> Unit = {},
        onDateToPicked: (LocalDate) -> Unit = {},
        onFieldToggle: (LogRowField, Boolean) -> Unit = { _, _ -> },
        onShareClick: () -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            LogSharePreviewScreen(
                uiState = uiState,
                now = now,
                graphicsLayer = rememberGraphicsLayer(),
                onBack = {},
                onFormatSelect = onFormatSelect,
                onSortOrderSelect = onSortOrderSelect,
                onDateFromPicked = onDateFromPicked,
                onDateToPicked = onDateToPicked,
                onFieldToggle = onFieldToggle,
                onShareClick = onShareClick,
            )
        }
    }

    private fun defaultSelection(dateTo: Long = millisAtDay(60)) = LogShareSelection(dateTo = dateTo)

    /**
     * The range button's exact text, including its "Range: " prefix. The live card preview below
     * renders its own range subtitle with the same bare value and no prefix (`logShareRangeLabel`
     * shares `formatDateRangeBound`/`Voice.shareLogRangeNote` with this button, so the bare value
     * is now identical) -- matching on the bare value alone would be ambiguous, so the prefix is
     * what makes this the button specifically.
     */
    private fun rangeButtonText(value: String) = "${PlainVoice.shareLogRangeLabel}: $value"

    @Smoke
    @Test
    fun formatToggle_selectingSquare_invokesCallback() {
        var selected: ShareCardFormat? = null
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
            onFormatSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareFormatSquareLabel).performClick()

        assertEquals(ShareCardFormat.SQUARE, selected)
    }

    @Test
    fun sortToggle_selectingOldestFirst_invokesCallback() {
        var selected: ChronologicalOrder? = null
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
            onSortOrderSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareLogSortOldestLabel).performClick()

        assertEquals(ChronologicalOrder.OLDEST_FIRST, selected)
    }

    @Test
    fun fieldRows_durationAndIntensity_onlyAppearWhenTheCaseTracksThem() {
        setContent(
            uiState =
                LogShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.NONE, intensityEnabled = false),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.shareLogFieldNotesLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.shareLogFieldTagsLabel).assertExists()
    }

    @Test
    fun fieldRows_durationAndIntensity_appearWhenTheCaseTracksThem() {
        setContent(
            uiState =
                LogShareUiState(
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
        var toggled: Pair<LogRowField, Boolean>? = null
        setContent(
            uiState =
                LogShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.NONE, intensityEnabled = false),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
            onFieldToggle = { field, selected -> toggled = field to selected },
        )

        // By tag, not by label text: the label/Switch semantics don't reliably merge into one
        // clickable node in every context (see LOG_SHARE_FIELD_TOGGLE_TAG_PREFIX's own doc comment).
        composeTestRule.onNodeWithTag(LOG_SHARE_FIELD_TOGGLE_TAG_PREFIX + LogRowField.NOTES.name).performClick()

        assertEquals(LogRowField.NOTES to false, toggled)
    }

    @Test
    fun shareButton_click_invokesCallback() {
        var clicked = false
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
            onShareClick = { clicked = true },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareLogButtonLabel).performClick()

        assert(clicked)
    }

    @Test
    fun emptyRangeMessage_showsOnTheCardPreview_whenNoEventsMatch() {
        setContent(
            uiState =
                LogShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareLogEmptyRangeMessage).assertExists()
    }

    @Test
    fun rangeButton_defaultsToAllTime() {
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareLogRangeAllTimeLabel)).assertExists()
    }

    @Test
    fun rangeButton_showsFormattedBounds_whenRangeIsNarrowed() {
        val from = millisAtDay(20)
        val to = millisAtDay(40)
        val now = millisAtDay(60)
        setContent(
            uiState =
                LogShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    selection = defaultSelection(dateTo = to).copy(dateFrom = from),
                    isLoading = false,
                ),
        )

        composeTestRule
            .onNodeWithText(
                rangeButtonText(
                    PlainVoice.shareLogRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).assertExists()
    }

    @Test
    fun rangeButton_tap_opensOneCombinedRangeDialog() {
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareLogRangeAllTimeLabel)).performClick()

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
                LogShareUiState(
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
                    PlainVoice.shareLogRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        assertEquals(from.toLocalDateIn(ZONE), changedFrom)
        assertEquals(to.toLocalDateIn(ZONE), changedTo)
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
                LogShareUiState(
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
                    PlainVoice.shareLogRangeNote(formatDateRangeBound(from, now, ZONE), formatDateRangeBound(to, now, ZONE)),
                ),
            ).performClick()
        composeTestRule.onNodeWithTag(DATE_RANGE_ALL_TIME_BUTTON_TAG).performClick()

        assertNull(changedFrom)
        assertEquals(now.toLocalDateIn(ZONE), changedTo)
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).assertDoesNotExist()
    }

    @Test
    fun rangeDialog_allTimeButton_hiddenWhenNothingIsFilteredYet() {
        // The range button's own label already reads "Range: All time" when unfiltered, and the
        // live card preview below repeats the bare value -- by tag, not by label text, so this
        // doesn't collide with either.
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareLogRangeAllTimeLabel)).performClick()

        composeTestRule.onNodeWithTag(DATE_RANGE_ALL_TIME_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun rangeDialog_confirmWithNoStartSelected_clearsFromToSinceTheBeginning() {
        // dateFrom starts null (since-the-beginning) in the default selection -- confirming
        // without touching the calendar should keep it null, not coerce it to some other value.
        var changedFrom: LocalDate? = LocalDate.of(2026, 1, 1)
        setContent(
            uiState = LogShareUiState(case = testCase(id = 1L), events = emptyList(), selection = defaultSelection(), isLoading = false),
            onDateFromPicked = { changedFrom = it },
        )

        composeTestRule.onNodeWithText(rangeButtonText(PlainVoice.shareLogRangeAllTimeLabel)).performClick()
        composeTestRule.onNodeWithText(PlainVoice.logSheetPickerConfirm).performClick()

        assertNull(changedFrom)
    }
}
