package com.hipka.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Compose navigation with guest configuration; no mocked backend claims. */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun primaryDestinationsAndAvatarAreDistinct() {
        val activity = compose.activity
        compose.waitUntil(20000) {
            compose.onAllNodesWithContentDescription(activity.getString(R.string.nav_library)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(activity.getString(R.string.nav_library)).performClick()
        compose.onNodeWithText(activity.getString(R.string.local_library_title)).assertIsDisplayed()
        compose.onNodeWithText(activity.getString(R.string.downloads_title)).performClick()
        compose.onNodeWithContentDescription(activity.getString(R.string.nav_search)).performClick()
        compose.onNodeWithText(activity.getString(R.string.v2_downloadable)).assertIsDisplayed()
        compose.onNodeWithContentDescription(activity.getString(R.string.nav_chats)).performClick()
        compose.onNodeWithText(activity.getString(R.string.v2_guest)).assertIsDisplayed()
        compose.onNodeWithContentDescription(activity.getString(R.string.nav_playlists)).performClick()
        compose.onNodeWithText(activity.getString(R.string.create_playlist)).assertIsDisplayed()
        compose.onNodeWithText("Hipka Vibes").performClick()
        compose.onNodeWithText(activity.getString(R.string.v2_vibes_note)).assertIsDisplayed()
        compose.onNodeWithContentDescription(activity.getString(R.string.nav_home)).performClick()
        compose.onAllNodesWithText(activity.getString(R.string.home_quick_artists)).onFirst().performClick()
        compose.onNodeWithText(activity.getString(R.string.home_quick_artists)).assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription(activity.getString(R.string.nav_profile)).performClick()
        compose.onNodeWithText(activity.getString(R.string.v2_guest)).assertIsDisplayed()
    }
}
