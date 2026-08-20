package com.secondmonday.hodith.ui.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.DeveloperModeUnlockEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * [AboutScreen] is a stateless composable driven entirely by callbacks, same pattern as
 * `TriggersScreenTest` — no Hilt/Activity/Room needed.
 */
@UiTest
class AboutScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        onBack: () -> Unit = {},
        unlockEvents: MutableSharedFlow<DeveloperModeUnlockEvent> = MutableSharedFlow(extraBufferCapacity = 1),
        onVersionTapped: () -> Unit = {},
        onOpenPrivacyPolicy: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                AboutScreen(
                    onBack = onBack,
                    unlockEvents = unlockEvents,
                    onVersionTapped = onVersionTapped,
                    onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                )
            }
        }
    }

    @Smoke
    @Test
    fun showsVersionPrivacyAndLicensesSections() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.aboutIdeaLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutIdeaBody).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutVersionLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutPrivacyLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutPrivacyBody).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutPrivacyPolicyLinkLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutLicensesLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutLicensesBody).assertExists()
    }

    @Test
    fun privacyBody_doesNotClaimEverythingStaysOnThePhone() {
        // Regression guard: the original copy claimed an absolute "everything stays on your phone,"
        // which wasn't true once `allowBackup`/`data_extraction_rules.xml` are accounted for. Checks
        // a literal substring rather than PlainVoice.aboutPrivacyBody itself, so this still catches a
        // future revert to the old claim instead of trivially passing against whatever the copy says.
        setContent()

        composeTestRule.onAllNodesWithText("stays on your phone", substring = true, ignoreCase = true).assertCountEquals(0)
        composeTestRule.onNodeWithText("backup", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun backButton_invokesOnBack() {
        var backPressed = false
        setContent(onBack = { backPressed = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.backButtonDescription).performClick()

        assertEquals(true, backPressed)
    }

    @Test
    fun versionRow_tapInvokesOnVersionTapped() {
        var tapCount = 0
        setContent(onVersionTapped = { tapCount++ })

        composeTestRule.onNodeWithText(PlainVoice.aboutVersionLabel).performClick()

        assertEquals(1, tapCount)
    }

    @Test
    fun privacyPolicyLink_tapInvokesOnOpenPrivacyPolicy() {
        var tapCount = 0
        setContent(onOpenPrivacyPolicy = { tapCount++ })

        composeTestRule.onNodeWithText(PlainVoice.aboutPrivacyPolicyLinkLabel).performClick()

        assertEquals(1, tapCount)
    }

    @Test
    fun unlockedEvent_showsSnackbarMessage() {
        val unlockEvents = MutableSharedFlow<DeveloperModeUnlockEvent>(extraBufferCapacity = 1)
        setContent(unlockEvents = unlockEvents)

        composeTestRule.runOnIdle { unlockEvents.tryEmit(DeveloperModeUnlockEvent.Unlocked) }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithText(PlainVoice.aboutDeveloperModeUnlockedMessage)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }
}
