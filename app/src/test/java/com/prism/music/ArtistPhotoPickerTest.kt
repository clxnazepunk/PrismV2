package com.prism.music

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.PlaylistCovers
import com.prism.music.ui.screens.rememberArtistPhotoPicker
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistPhotoPickerTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createComposeRule()

    private class Registry : ActivityResultRegistry() {
        var requestCode = -1
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            this.requestCode = requestCode
        }
    }

    @Test fun photoResultAfterMenuClosesStillCopiesToOriginalArtistAndPersists() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("playlist_covers", 0).edit().clear().commit()
        val covers = PlaylistCovers(context)
        val registry = Registry()
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        var menuOpen by mutableStateOf(true)
        var artistId by mutableStateOf("first")
        var error by mutableStateOf(false)
        val input = File(context.cacheDir, "artist-input.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val launch = rememberArtistPhotoPicker(artistId, covers) { error = true }
                if (menuOpen) Box(Modifier.size(48.dp).testTag("photo").clickable { menuOpen = false; launch() })
            }
        }
        compose.onNodeWithTag("photo").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(menuOpen)
            assertTrue(registry.requestCode >= 0)
            artistId = "second"
        }
        compose.waitForIdle()
        compose.runOnIdle { registry.dispatchResult(registry.requestCode, Uri.fromFile(input)) }
        compose.waitUntil(10_000) { error || covers.custom(covers.artistKey("first")) != null }
        compose.runOnIdle {
            assertFalse("Photo callback returned an import failure", error)
            val saved = covers.custom(covers.artistKey("first"))!!
            assertTrue(File(Uri.parse(saved).path!!).isFile)
            assertEquals(saved, PlaylistCovers(context).custom(covers.artistKey("first")))
            assertNull(covers.custom(covers.artistKey("second")))
        }
    }
}
