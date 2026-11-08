package com.prism.music

import android.graphics.Bitmap
import android.graphics.Color
import com.prism.music.data.artists.ArtistImageLayout
import com.prism.music.data.artists.UniformArtistBackground
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistBackgroundTest {
    @Test fun whiteMatteIsRemovedWithoutDestroyingEnclosedLightDetailsOrOriginal() {
        val original = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.WHITE)
        for (x in 3..8) for (y in 3..8) original.setPixel(x, y, Color.BLACK)
        original.setPixel(5, 5, Color.WHITE)
        val result = UniformArtistBackground.remove(original, 32)
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        assertEquals(Color.BLACK, result.getPixel(3, 3))
        assertEquals(Color.WHITE, result.getPixel(5, 5))
        assertEquals(Color.WHITE, original.getPixel(0, 0))
        assertFalse(original.isRecycled)
        original.recycle(); result.recycle()
    }

    @Test fun blackMatteAndExistingTransparencyAreHandledIndependently() {
        val original = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.BLACK)
        original.setPixel(6, 6, Color.WHITE)
        val result = UniformArtistBackground.remove(original, 32)
        assertEquals(0, Color.alpha(result.getPixel(0, 0)))
        assertEquals(Color.WHITE, result.getPixel(6, 6))
        val transparent = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        transparent.setPixel(6, 6, Color.GREEN)
        val unchanged = UniformArtistBackground.remove(transparent, 32)
        assertEquals(Color.GREEN, unchanged.getPixel(6, 6))
        assertEquals(0, Color.alpha(unchanged.getPixel(0, 0)))
        original.recycle(); result.recycle(); transparent.recycle(); unchanged.recycle()
    }

    @Test fun complexBordersAreLeftUntouchedInsteadOfGuessingASubject() {
        val original = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        for (x in 0..15) for (y in 0..15) original.setPixel(x, y, colors[(x + y) % 4])
        val result = UniformArtistBackground.remove(original, 32)
        for (x in 0..15) for (y in 0..15) assertEquals(original.getPixel(x, y), result.getPixel(x, y))
        original.recycle(); result.recycle()
    }

    @Test fun processingIsBoundedAndDoesNotRecycleTheSharedOriginal() {
        val original = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.WHITE)
        original.setPixel(1024, 512, Color.BLACK)
        val result = UniformArtistBackground.remove(original, 32)
        assertEquals(1024, result.width)
        assertEquals(512, result.height)
        assertFalse(original.isRecycled)
        original.recycle(); result.recycle()
    }

    @Test fun cancellationStopsProcessingWithoutChangingTheOriginal() {
        val original = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.WHITE)
        try { UniformArtistBackground.remove(original, 32) { throw kotlinx.coroutines.CancellationException() }; fail() }
        catch (_: kotlinx.coroutines.CancellationException) {}
        assertEquals(Color.WHITE, original.getPixel(0, 0))
        assertFalse(original.isRecycled)
        original.recycle()
    }

    @Test fun backgroundAndBlendChoicesRemainIndependentFromSizeAndLegacyCoordinates() {
        val layout = ArtistImageLayout(4f, 0.2f, -0.8f, blend = "darken", removeBackground = true, backgroundTolerance = 35)
        val migrated = layout.forHeader(400f, 72f)
        assertTrue(migrated.removeBackground)
        assertEquals("darken", migrated.blend)
        assertEquals(35, migrated.backgroundTolerance)
        assertEquals(4f, migrated.scale, 0f)
        assertEquals("normal", layout.copy(blend = "unknown").bounded().blend)
        assertEquals(100, layout.copy(backgroundTolerance = 999).bounded().backgroundTolerance)
    }
}
