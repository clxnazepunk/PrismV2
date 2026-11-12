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
import com.prism.music.data.artists.ArtistPhotoFrame
import kotlinx.coroutines.launch
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

    @Test fun photoResultWaitsForConfirmationAndStoresFullAspectAndFrameForOriginalArtist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("playlist_covers", 0).edit().clear().commit()
        val covers = PlaylistCovers(context)
        val registry = Registry()
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        var menuOpen by mutableStateOf(true)
        var artistId by mutableStateOf("first")
        var error by mutableStateOf(false)
        var selection by mutableStateOf<Pair<String, Uri>?>(null)
        val frame = ArtistPhotoFrame(false, 2f, 0.3f, -0.4f)
        val input = File(context.cacheDir, "artist-input.png")
        val bitmap = Bitmap.createBitmap(20, 10, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                val scope = rememberCoroutineScope()
                val launch = rememberArtistPhotoPicker(artistId) { target, uri -> selection = target to uri }
                selection?.let { (target, uri) ->
                    Box(Modifier.size(48.dp).testTag("confirm").clickable {
                        scope.launch { error = !covers.useArtistPhoto(target, uri, frame) }
                    })
                }
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
        compose.runOnIdle { assertTrue(registry.dispatchResult(registry.requestCode, Uri.fromFile(input))) }
        compose.onNodeWithTag("confirm").assertExists()
        compose.runOnIdle {
            assertNull(covers.custom(covers.artistKey("first")))
            assertNull(covers.artistFrame("first"))
        }
        compose.onNodeWithTag("confirm").performClick()
        compose.waitUntil(10_000) {
            // The importer returns via Android Dispatchers.Main, separate from Compose v2's test clock.
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            error || covers.custom(covers.artistKey("first")) != null
        }
        compose.runOnIdle {
            assertFalse("Photo callback returned an import failure", error)
            val saved = covers.custom(covers.artistKey("first"))!!
            assertTrue(File(Uri.parse(saved).path!!).isFile)
            val imported = android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(File(Uri.parse(saved).path!!)),
            )
            assertEquals(20, imported.width)
            assertEquals(10, imported.height)
            imported.recycle()
            val reopened = PlaylistCovers(context)
            assertEquals(saved, reopened.custom(covers.artistKey("first")))
            assertEquals(frame, reopened.artistFrame("first"))
            val adjusted = frame.copy(zoom = 3f)
            assertTrue(reopened.adjustArtistPhoto("first", saved, adjusted))
            assertEquals(saved, reopened.custom(covers.artistKey("first")))
            assertEquals(adjusted, PlaylistCovers(context).artistFrame("first"))
            reopened.reset(covers.artistKey("first"))
            assertNull(PlaylistCovers(context).artistFrame("first"))
            assertNull(covers.custom(covers.artistKey("second")))
        }
    }
}
