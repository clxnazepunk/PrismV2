package com.prism.music

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.prism.music.data.artists.ArtistPhotoFrame
import com.prism.music.ui.screens.ArtistFramedPhoto
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismUi
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtistPhotoAdjustmentAppTest {
    @get:Rule val compose = androidx.compose.ui.test.junit4.v2.createComposeRule()

    @Test fun artistPhotoPreviewAndHeaderRenderTheSameNormalizedCrop() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(160, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.RED)
        android.graphics.Canvas(bitmap).drawRect(80f, 0f, 160f, 80f,
            android.graphics.Paint().apply { color = android.graphics.Color.GREEN })
        val file = File(context.cacheDir, "artist-photo-frame.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val source = android.net.Uri.fromFile(file).toString()
        var frame by mutableStateOf(ArtistPhotoFrame(x = -1f))
        var previewLoaded by mutableStateOf(false)
        var headerLoaded by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f), LocalUi provides PrismUi(reduceMotion = true)) {
                Column {
                    ArtistFramedPhoto(source, frame, Modifier.size(64.dp).background(Color.Blue).testTag("photo-preview"),
                        onImageSize = { previewLoaded = true })
                    ArtistFramedPhoto(source, frame, Modifier.size(128.dp).background(Color.Blue).testTag("photo-header"),
                        onImageSize = { headerLoaded = true })
                }
            }
        }
        compose.waitUntil(10_000) {
            previewLoaded && headerLoaded
        }
        fun sample(tag: String): Color {
            val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
            return pixels[pixels.width / 2, pixels.height / 8]
        }
        assertTrue(sample("photo-preview").green > 0.9f)
        assertTrue(sample("photo-header").green > 0.9f)
        compose.runOnIdle { frame = ArtistPhotoFrame(fill = false) }
        assertTrue(sample("photo-preview").blue > 0.9f)
        assertTrue(sample("photo-header").blue > 0.9f)
    }
}
