package com.app.beatnote

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeatNoteAppTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testMusicLibraryDisplaysTitleWithoutSampleSongs() {
        // Verify app title BEATNOTE is displayed
        composeTestRule.onNodeWithText("BEATNOTE").assertExists()

        // Verify Music Library heading is displayed
        composeTestRule.onNodeWithText("Music Library").assertExists()

        // The real library must not be populated with hardcoded sample data.
        composeTestRule.onNodeWithText("Midnight Bounce").assertDoesNotExist()
    }
}
