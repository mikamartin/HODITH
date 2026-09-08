package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.BrightVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Regression coverage for S2: the shared empty-state note must be centred by [TextAlign.Center] and
 * inset from both edges, not just positioned by its container. Before the fix the note had neither,
 * so once the longer Intense/Bright strings wrapped, every line laid out flush against the start edge.
 */
@UiTest
class CenteredEmptyStateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val containerTag = "empty_state_container"

    // The Bright string is the longest of the three voices, so it exercises the widest inset.
    private val note = BrightVoice.insightsNotEnoughDataMessage(eventsRemaining = 1)

    private fun setContent() {
        composeTestRule.setContent {
            Box(modifier = Modifier.fillMaxSize().testTag(containerTag)) {
                CenteredEmptyState(note)
            }
        }
    }

    @Test
    fun note_isInsetFromBothEdges_andNotFlushLeft() {
        setContent()

        val container = composeTestRule.onNodeWithTag(containerTag).getUnclippedBoundsInRoot()
        val noteBounds = composeTestRule.onNodeWithText(note).getUnclippedBoundsInRoot()

        val leftInset = (noteBounds.left - container.left).value
        val rightInset = (container.right - noteBounds.right).value

        assertTrue("Expected the note to be inset from the left edge, was ${leftInset}dp", leftInset > 8f)
        assertTrue(
            "Expected symmetric insets (left=${leftInset}dp, right=${rightInset}dp)",
            kotlin.math.abs(leftInset - rightInset) < 2f,
        )
    }

    @Test
    fun note_isCentreAligned() {
        setContent()

        val results = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNodeWithText(note)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsActions.GetTextLayoutResult)
            ?.action
            ?.invoke(results)

        val style = results.first().layoutInput.style
        assertEquals(TextAlign.Center, style.textAlign)
    }
}
