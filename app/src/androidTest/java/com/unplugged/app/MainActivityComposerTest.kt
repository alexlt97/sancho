package com.unplugged.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityComposerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun composerShowsAttachmentMenuAndCompactSendControl() {
        composeRule.onAllNodesWithText("Sancho").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Model status", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
        composeRule.onAllNodesWithText("Import model").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send message").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Add attachment").performClick()

        composeRule.onNodeWithText("Choose image").assertIsDisplayed()
        composeRule.onNodeWithText("Use camera").assertIsDisplayed()
    }

    @Test
    fun settingsShowsModelImportControl() {
        composeRule.onNodeWithContentDescription("Open settings").performClick()

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Local model").assertIsDisplayed()
        composeRule.onNodeWithText("Import model").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to chat").performClick()
        composeRule.onNodeWithContentDescription("Open settings").assertIsDisplayed()
    }
}
