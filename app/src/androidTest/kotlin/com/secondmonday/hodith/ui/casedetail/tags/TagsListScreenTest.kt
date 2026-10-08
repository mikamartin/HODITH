package com.secondmonday.hodith.ui.casedetail.tags

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.EventWithTags
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.TagBreakdownEntry
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * [TagsListScreen] is stateless, driven by plain data and callbacks, like [TrendsListScreenTest].
 * Covers the title naming the section and Case, the back arrow, the summary above the list, that
 * every tag renders (no cap on this screen), and that a row opens the same drill-down the card uses.
 */
@UiTest
class TagsListScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val now =
        LocalDate
            .of(2026, 7, 23)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private val tags =
        listOf(
            TagBreakdownEntry("alpha", 12),
            TagBreakdownEntry("beta", 11),
            TagBreakdownEntry("gamma", 10),
            TagBreakdownEntry("delta", 4),
            TagBreakdownEntry("epsilon", 3),
            TagBreakdownEntry("zeta", 2),
        )

    private val alphaTag = TagEntity(id = 1L, name = "alpha")

    private val eventsWithTags =
        listOf(
            EventWithTags(testEvent(caseId = 1L, id = 1L, occurredAt = now - 2 * DAY_MILLIS, note = "Alpha event"), listOf(alphaTag)),
            EventWithTags(testEvent(caseId = 1L, id = 2L, occurredAt = now - DAY_MILLIS, note = "Untagged event"), emptyList()),
        )

    private fun setContent(
        caseIcon: String = "☕",
        caseName: String = "Coffee",
        onBack: () -> Unit = {},
        onEditEvent: (EventEntity) -> Unit = {},
    ) {
        composeTestRule.setHodithContent {
            TagsListScreen(
                totalEventCount = 40,
                distinctTagCount = tags.size,
                taggedEventCount = 1,
                tags = tags,
                eventsWithTags = eventsWithTags,
                durationMode = DurationMode.NONE,
                caseIcon = caseIcon,
                caseName = caseName,
                nowMillis = { now },
                onBack = onBack,
                onEditEvent = onEditEvent,
            )
        }
    }

    @Test
    fun title_namesBothTheSectionAndTheCase() {
        setContent(caseIcon = "☕", caseName = "Coffee")

        composeTestRule.onNodeWithText(PlainVoice.insightsSectionLabelTags).assertExists()
        composeTestRule.onNodeWithText("☕ Coffee").assertExists()
    }

    @Test
    fun backArrow_invokesOnBack() {
        var backed = false
        setContent(onBack = { backed = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.backButtonDescription).performClick()

        assertTrue(backed)
    }

    @Test
    fun summary_showsTaggedEventsAndTotalTagsAboveTheList() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.insightsTagsTaggedLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.trendCountOfTotal(1, 40)).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.insightsTagsDistinctLabel).assertExists()
        composeTestRule.onNodeWithText("6").assertExists()
    }

    @Test
    fun everyTag_rendersWithItsCount_noCap() {
        setContent()

        tags.forEach { tag ->
            composeTestRule.onNodeWithText(tag.tagName).assertExists()
            composeTestRule.onNodeWithContentDescription(PlainVoice.insightsTagRowTapDescription(tag.tagName)).assertExists()
        }
        composeTestRule.onNodeWithText(PlainVoice.insightsTagsSeeAllAction).assertDoesNotExist()
    }

    @Test
    fun tagRow_tap_opensDrillDownListingOnlyThatTagsEvents() {
        setContent()

        composeTestRule.onNodeWithContentDescription(PlainVoice.insightsTagRowTapDescription("alpha")).performClick()

        composeTestRule.onNodeWithText(PlainVoice.insightsTagDrillDownTitle("alpha")).assertExists()
        composeTestRule.onNodeWithText("Alpha event").assertExists()
        composeTestRule.onNodeWithText("Untagged event").assertDoesNotExist()
    }

    @Test
    fun infoAction_opensTagsExplanationDialog() {
        setContent()

        composeTestRule.onNodeWithContentDescription(PlainVoice.caseSectionInfoDescription).performClick()

        composeTestRule.onNodeWithText(PlainVoice.insightsTagsInfoTitle).assertExists()
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60_000
    }
}
