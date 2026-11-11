package com.prism.music

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.ArtistPrefs
import com.prism.music.ui.screens.MusicTagKeyForm
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MusicTagKeyFormTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createComposeRule()

    @Test fun saveClickValidatesAndPersistsLocallyWithoutMakingANetworkRequest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = ArtistPrefs(context)
        val key = "0123456789abcdef".repeat(2)
        var onlineChecks = 0
        compose.setContent {
            MaterialTheme {
                MusicTagKeyForm(prefs) {
                    onlineChecks++
                    com.prism.music.data.meta.MetaRepository.TagConnection.UNAVAILABLE
                }
            }
        }
        val input = compose.onNodeWithTag("lastFmTagKey")
        val save = compose.onNodeWithTag("saveLastFmTagKey")
        val status = compose.onNodeWithTag("lastFmTagStatus")
        input.performTextReplacement("invalid")
        save.performClick()
        status.assertTextEquals(tr(R.string.music_tag_key_format))
        assertEquals("", prefs.musicTagKey.value)
        input.performTextReplacement(" $key ")
        save.performClick()
        compose.waitUntil(10_000) { prefs.musicTagKey.value == key }
        status.assertTextEquals(tr(R.string.music_tag_saved))
        assertEquals(key, ArtistPrefs(context).musicTagKey.value)
        assertEquals(0, onlineChecks)
    }
}
