package com.prism.music

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.prism.music.ui.screens.PlaylistRecommendationFooter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PlaylistRecommendationFooterTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createComposeRule()

    @Test fun emptyLoadingRetryAndDisabledStatesAreReachable() {
        var enabled by mutableStateOf(true)
        var hasSeeds by mutableStateOf(false)
        var loading by mutableStateOf(false)
        var failed by mutableStateOf(false)
        var refreshes = 0
        compose.setContent {
            MaterialTheme {
                PlaylistRecommendationFooter(enabled, hasSeeds, true, loading, failed, false, { refreshes++ }) {}
            }
        }
        compose.onNodeWithTag("playlistRecommendations").assertExists()
        compose.onNodeWithTag("refreshPlaylistSuggestions").assertIsNotEnabled()

        compose.runOnIdle { hasSeeds = true; loading = true }
        compose.onNodeWithTag("loadingPlaylistSuggestions").assertExists()
        compose.onNodeWithTag("refreshPlaylistSuggestions").assertIsNotEnabled()

        compose.runOnIdle { loading = false; failed = true }
        compose.onNodeWithTag("retryPlaylistSuggestions").performClick()
        compose.runOnIdle { assertEquals(1, refreshes); enabled = false }
        compose.onNodeWithTag("playlistRecommendations").assertDoesNotExist()
    }
}
