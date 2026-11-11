package com.prism.music

import android.graphics.Bitmap
import android.graphics.Color
import com.prism.music.data.artists.ArtistImageLayout
import com.prism.music.data.artists.ArtistVisualEffects
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistVisualEffectsTest {
    @Test fun colorAndAlphaChangeOnlyInTheRenderedCopy() {
        val input = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        input.setPixel(16, 16, Color.RED)
        val inverted = ArtistVisualEffects.render(input, ArtistImageLayout(inverted = true))
        val gray = ArtistVisualEffects.render(input, ArtistImageLayout(grayscale = true, brightness = 10))
        val glow = ArtistVisualEffects.render(input, ArtistImageLayout(glow = 50, shadow = 40))
        assertEquals(Color.CYAN, inverted.getPixel(16, 16))
        assertEquals(Color.RED, input.getPixel(16, 16))
        assertEquals(0, Color.alpha(inverted.getPixel(0, 0)))
        val pixel = gray.getPixel(16, 16)
        assertEquals(Color.red(pixel), Color.green(pixel))
        assertEquals(Color.green(pixel), Color.blue(pixel))
        assertTrue(glow.width > input.width)
        assertEquals(Color.RED, glow.getPixel(glow.width / 2, glow.height / 2))
        listOf(input, inverted, gray, glow).forEach { it.recycle() }
    }
}
