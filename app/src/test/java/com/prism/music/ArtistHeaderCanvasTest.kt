package com.prism.music

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.artists.ArtistImageLayout
import com.prism.music.data.artists.ArtistPhotoFrame
import com.prism.music.ui.screens.ArtistFramedPhoto
import com.prism.music.ui.theme.LocalUi
import com.prism.music.ui.theme.PrismUi
import com.prism.music.ui.screens.ArtistProfileMark
import com.prism.music.data.artists.ArtistImageRole
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
class ArtistHeaderCanvasTest {
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
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
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

    @Test fun lowResolutionRoundSignatureExtendsAboveItsOldSlotAndStaysWithinPage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).drawCircle(12f, 12f, 12f, android.graphics.Paint().apply { color = android.graphics.Color.WHITE })
        val file = File(context.cacheDir, "round-mark.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var loaded by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(300.dp).background(Color.Black).clipToBounds().testTag("header")) {
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                            ArtistProfileMark(ArtistImageRole.SIGNATURE, file, "", ArtistImageLayout(4f, 0f, -0.3f, headerCoordinates = true),
                                canvasHeight = 300.dp, onSuccess = { loaded = true })
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            loaded
        }
        val image = compose.onNodeWithTag("header").captureToImage()
        val pixels = image.toPixelMap()
        val output = File("build/artist-header-previews").apply { mkdirs() }
        File(output, "round-mark-expanded.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        // The old 72dp slot starts at y228. This point must now be visible far above that slot.
        assertTrue("Expanded round signature is still clipped to its small slot", pixels[150, 70].red > 0.8f)
        assertTrue("Image must stay inside the full page boundary", pixels[0, 0].red < 0.1f)

    }
    @Test fun switchingTransparentMarksKeepsTheBackgroundOutsideTheArtwork() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun dot(name: String, color: Int): File {
            val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bitmap).drawCircle(12f, 12f, 8f, android.graphics.Paint().apply { this.color = color })
            return File(context.cacheDir, name).also { file ->
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        val red = dot("theme-red.png", android.graphics.Color.RED)
        val green = dot("theme-green.png", android.graphics.Color.GREEN)
        var mark by mutableStateOf(red)
        var loaded by mutableStateOf<File?>(null)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(300.dp).background(Color.Blue).clipToBounds().testTag("theme-transition")) {
                        val shown = mark
                        ArtistProfileMark(ArtistImageRole.LOGO, shown, "", ArtistImageLayout(), 300.dp,
                            onSuccess = { loaded = shown })
                    }
                }
            }
        }
        fun await(file: File) = compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            loaded == file
        }
        fun render() = compose.onNodeWithTag("theme-transition").captureToImage()
        await(red)
        compose.mainClock.advanceTimeBy(300)
        assertTrue(render().toPixelMap()[150, 50].red > 0.8f)
        compose.runOnIdle { mark = green }
        await(green)
        assertTrue(render().toPixelMap()[20, 50].blue > 0.9f)
        // Coil's painter measures elapsed time with the monotonic system clock.
        Thread.sleep(300)
        compose.mainClock.advanceTimeBy(300)
        val image = render()
        assertTrue(image.toPixelMap()[150, 50].green > 0.8f)
        val output = File("build/artist-header-previews").apply { mkdirs() }
        File(output, "theme-transition.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun obsoleteBlendDoesNotAffectImportedSignature() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        android.graphics.Canvas(bitmap).drawCircle(12f, 12f, 5f, android.graphics.Paint().apply { color = android.graphics.Color.BLACK })
        val file = File(context.cacheDir, "white-background-mark.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        var loaded by mutableStateOf(false)
        val backdrop = Color(0.3f, 0.5f, 0.8f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    Box(Modifier.size(300.dp).background(backdrop).clipToBounds().testTag("blend-header")) {
                        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                            ArtistProfileMark(ArtistImageRole.SIGNATURE, file, "", ArtistImageLayout(4f, 0f, -0.3f, headerCoordinates = true, blend = "darken"),
                                canvasHeight = 300.dp, onSuccess = { loaded = true })
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            loaded
        }
        val image = compose.onNodeWithTag("blend-header").captureToImage()
        val output = File("build/artist-header-previews").apply { mkdirs() }
        File(output, "original-signature.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val pixels = image.toPixelMap()
        assertTrue("Old blend settings must not hide the original pixels", pixels[70, 80].red > 0.9f)
        assertTrue("Dark foreground must remain visible", pixels[150, 174].red < 0.1f)
    }

}
