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
import com.prism.music.ui.screens.ArtistMark
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

    @Test fun lowResolutionRoundLogoExtendsAboveItsOldSlotAndStaysWithinPage() {
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
                            ArtistMark(file, "", ArtistImageLayout(4f, 0f, -0.3f, headerCoordinates = true),
                                72.dp, canvasHeight = 300.dp, overflow = true, onSuccess = { loaded = true })
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
        assertTrue("Expanded round logo is still clipped to its small slot", pixels[150, 70].red > 0.8f)
        assertTrue("Image must stay inside the full page boundary", pixels[0, 0].red < 0.1f)

    }
}
