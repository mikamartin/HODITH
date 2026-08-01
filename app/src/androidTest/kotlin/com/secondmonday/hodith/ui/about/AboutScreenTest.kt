package com.secondmonday.hodith.ui.about

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.PlainVoice
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

    private fun setContent(onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalVoice provides PlainVoice) {
                AboutScreen(onBack = onBack)
            }
        }
    }

    @Smoke
    @Test
    fun showsVersionPrivacyAndLicensesSections() {
        setContent()

        composeTestRule.onNodeWithText(PlainVoice.aboutVersionLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutPrivacyLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutPrivacyBody).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutLicensesLabel).assertExists()
        composeTestRule.onNodeWithText(PlainVoice.aboutLicensesBody).assertExists()
    }

    @Test
    fun backButton_invokesOnBack() {
        var backPressed = false
        setContent(onBack = { backPressed = true })

        composeTestRule.onNodeWithContentDescription(PlainVoice.backButtonDescription).performClick()

        assertEquals(true, backPressed)
    }
}
