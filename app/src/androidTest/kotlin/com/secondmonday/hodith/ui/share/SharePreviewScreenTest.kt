package com.secondmonday.hodith.ui.share

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.data.testHunch
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareInsightsSection
import com.secondmonday.hodith.viewmodel.ShareSelection
import com.secondmonday.hodith.viewmodel.ShareUiState
import org.junit.Assert.assertEquals
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

/** 12 events, 5 days apart — comfortably past the Preliminary bar so a Hunch resolves to a Verdict. */
private fun resolvedHunchEvents(): List<EventWithTags> =
    (0..55L step 5).map { day -> EventWithTags(testEvent(caseId = 1L, occurredAt = millisAtDay(day)), emptyList()) }

/**
 * [SharePreviewScreen] is stateless but needs a real `GraphicsLayer` (tied to composition) for the
 * capture modifier, same reason `ShareCardTemplate` itself needed [UiTest] rather than a plain unit
 * test — otherwise this follows [com.secondmonday.hodith.ui.casedetail.CaseDetailScreenTest]'s
 * pattern of driving the stateless screen directly with fake callbacks and `TestFixtures.kt`.
 */
@UiTest
class SharePreviewScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        uiState: ShareUiState,
        now: Long = millisAtDay(60),
        onFormatSelect: (ShareCardFormat) -> Unit = {},
        onSectionToggle: (ShareInsightsSection, Boolean) -> Unit = { _, _ -> },
        onShowHunchVsRealityToggle: (Boolean) -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                SharePreviewScreen(
                    uiState = uiState,
                    now = now,
                    graphicsLayer = rememberGraphicsLayer(),
                    onBack = {},
                    onFormatSelect = onFormatSelect,
                    onDisplayNameChange = {},
                    onSectionToggle = onSectionToggle,
                    onShowHunchVsRealityToggle = onShowHunchVsRealityToggle,
                    onShareClick = {},
                )
            }
        }
    }

    @Smoke
    @Test
    fun formatToggle_selectingSquare_invokesCallback() {
        var selected: ShareCardFormat? = null
        setContent(
            uiState = ShareUiState(case = testCase(id = 1L), events = emptyList(), isLoading = false),
            onFormatSelect = { selected = it },
        )

        composeTestRule.onNodeWithText(PlainVoice.shareFormatSquareLabel).performClick()

        assertEquals(ShareCardFormat.SQUARE, selected)
    }

    @Test
    fun durationAndIntensityRows_onlyAppearWhenTheCaseTracksThem() {
        setContent(
            uiState =
                ShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.NONE, intensityEnabled = false),
                    events = emptyList(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertDoesNotExist()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertDoesNotExist()
    }

    @Test
    fun durationAndIntensityRows_appearWhenTheCaseTracksThem() {
        setContent(
            uiState =
                ShareUiState(
                    case = testCase(id = 1L, durationMode = DurationMode.MANUAL, intensityEnabled = true),
                    events = emptyList(),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelDuration).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelIntensity).assertExists()
    }

    @Test
    fun hunchVsRealityToggle_onlyAppearsOnStoryWithAResolvedHunch() {
        val hunch = testHunch(caseId = 1L, expectedCount = 5, resolvedAt = null)

        setContent(
            uiState =
                ShareUiState(
                    case = testCase(id = 1L),
                    events = resolvedHunchEvents(),
                    activeHunch = hunch,
                    selection = ShareSelection(format = ShareCardFormat.STORY),
                    isLoading = false,
                ),
        )
        composeTestRule.onNodeWithText(PlainVoice.shareHunchVsRealityToggleLabel).assertExists()
    }

    @Test
    fun hunchVsRealityToggle_hiddenOnSquareEvenWithAResolvedHunch() {
        val hunch = testHunch(caseId = 1L, expectedCount = 5, resolvedAt = null)

        setContent(
            uiState =
                ShareUiState(
                    case = testCase(id = 1L),
                    events = resolvedHunchEvents(),
                    activeHunch = hunch,
                    selection = ShareSelection(format = ShareCardFormat.SQUARE),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHunchVsRealityToggleLabel).assertDoesNotExist()
    }

    @Test
    fun hunchVsRealityToggle_hiddenWithoutAnActiveHunch() {
        setContent(
            uiState =
                ShareUiState(
                    case = testCase(id = 1L),
                    events = emptyList(),
                    activeHunch = null as HunchEntity?,
                    selection = ShareSelection(format = ShareCardFormat.STORY),
                    isLoading = false,
                ),
        )

        composeTestRule.onNodeWithText(PlainVoice.shareHunchVsRealityToggleLabel).assertDoesNotExist()
    }

    @Test
    fun sectionChecklist_togglingARow_invokesCallbackWithTheSection() {
        var toggled: Pair<ShareInsightsSection, Boolean>? = null
        setContent(
            uiState = ShareUiState(case = testCase(id = 1L), events = emptyList(), isLoading = false),
            onSectionToggle = { section, selected -> toggled = section to selected },
        )

        // By tag, not by the "Rhythm" label text: that text can also appear in the live card
        // preview above once it has real data, so the tag is the only unambiguous target.
        // performScrollTo() first: the screen's a scrolling Column and this row can sit below the
        // fold, and the v2 test API's performClick() needs the target actually reachable, not just
        // present in the semantics tree.
        composeTestRule
            .onNodeWithTag(SECTION_TOGGLE_TAG_PREFIX + ShareInsightsSection.RHYTHM.name)
            .performScrollTo()
            .performClick()

        assertEquals(ShareInsightsSection.RHYTHM to false, toggled)
    }
}
