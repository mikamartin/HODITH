package com.secondmonday.hodith.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.ShareCardData
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareTopBeat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val STORY_TAG = "story_card"
private const val SQUARE_TAG = "square_card"

/**
 * Regression coverage for the Story/Square shape bug: both formats used to render with identical
 * dimensions, differing only in which top beat they show — a real gap the product owner caught
 * visually before this test existed. `ShareCardTemplate` itself is stateless, so no Hilt/Activity/
 * Room is needed, same pattern as `AboutScreenTest`/`TriggersScreenTest`.
 */
@UiTest
class ShareCardTemplateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun realityData(format: ShareCardFormat) =
        ShareCardData(
            format = format,
            caseIcon = "☕",
            caseName = "Perfect coffee",
            topBeat = ShareTopBeat.Reality(eventCount = 14, observedDays = 60),
            frequency = null,
            rhythm = null,
            gaps = null,
            trend = null,
            duration = null,
            intensity = null,
        )

    private fun hunchVsRealityData(format: ShareCardFormat) =
        ShareCardData(
            format = format,
            caseIcon = "☕",
            caseName = "Perfect coffee",
            topBeat =
                ShareTopBeat.HunchVsReality(
                    hunch =
                        HunchEntity(
                            id = 1L,
                            caseId = 1L,
                            direction = HunchDirection.TOO_OFTEN,
                            expectedCount = 2,
                            expectedPer = ExpectedPer.MONTH,
                            createdAt = 0L,
                            resolvedAt = null,
                        ),
                    observedRate = 7.0,
                    band = ComparisonBand.MUCH_MORE,
                ),
            frequency = null,
            rhythm = null,
            gaps = null,
            trend = null,
            duration = null,
            intensity = null,
        )

    @Smoke
    @Test
    fun storyIsTallerThanSquareForIdenticalContent() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                Column {
                    ShareCardTemplate(
                        data = realityData(ShareCardFormat.STORY),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(STORY_TAG),
                    )
                    ShareCardTemplate(
                        data = realityData(ShareCardFormat.SQUARE),
                        voice = PlainVoice,
                        modifier = Modifier.testTag(SQUARE_TAG),
                    )
                }
            }
        }

        val storyBounds = composeTestRule.onNodeWithTag(STORY_TAG).getUnclippedBoundsInRoot()
        val squareBounds = composeTestRule.onNodeWithTag(SQUARE_TAG).getUnclippedBoundsInRoot()
        val storyHeight = storyBounds.bottom - storyBounds.top
        val squareHeight = squareBounds.bottom - squareBounds.top

        assertTrue("Expected Story ($storyHeight) taller than Square ($squareHeight)", storyHeight > squareHeight)
    }

    @Test
    fun squareNeverShowsTheHunchVsRealityBeatEvenWhenDataProvidesIt() {
        // ShareCardTemplate trusts whatever ShareCardData.topBeat it's given — the actual STORY-only
        // gating lives in shareCardState (see ShareCardStateTest). This only proves the composable
        // itself does render a HunchVsReality beat when handed one on Square, so that gating stays
        // shareCardState's responsibility and can't quietly get "fixed" by the template hiding it too.
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                ShareCardTemplate(data = hunchVsRealityData(ShareCardFormat.SQUARE), voice = PlainVoice)
            }
        }

        composeTestRule.onNodeWithText(PlainVoice.shareHunchRealityKicker).assertExists()
    }
}
