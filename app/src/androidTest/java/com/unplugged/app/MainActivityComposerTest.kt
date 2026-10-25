package com.unplugged.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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
        composeRule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send message").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Add attachment").performClick()

        composeRule.onNodeWithText("Choose image").assertIsDisplayed()
        composeRule.onNodeWithText("Use camera").assertIsDisplayed()
    }
}
