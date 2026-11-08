package com.prism.music

import android.graphics.Bitmap
import android.graphics.Color
import com.prism.music.data.artists.*
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistDisplayTest {
    @Test fun automaticChoosesOnlyOneAndFallsBackInOrder() {
        assertEquals(ArtistDisplayChoice(true, false, false), ArtistDisplayChoice.automatic(true, true))
        assertEquals(ArtistDisplayChoice(false, true, false), ArtistDisplayChoice.automatic(false, true))
        assertEquals(ArtistDisplayChoice(false, false, true), ArtistDisplayChoice.automatic(false, false))
    }

    @Test fun manualCombinationsAndMissingImageFallbackRespectChoice() {
        for (bits in 1..7) {
            val choice = ArtistDisplayChoice(bits and 1 != 0, bits and 2 != 0, bits and 4 != 0)
            assertEquals(choice, choice.resolve(true, true))
            assertEquals(ArtistDisplayChoice(false, false, true), choice.resolve(false, false))
        }
        // Selecting only signature must never show an available but unselected logo.
        assertEquals(ArtistDisplayChoice(false, false, true),
            ArtistDisplayChoice(false, true, false).resolve(true, false))
    }

    @Test fun smallImagesCanEnlargeTwelveTimesWithoutInvalidTransforms() {
        assertEquals(12f, ArtistImageLayout(12f).bounded().scale, 0f)
        assertEquals(ArtistImageLayout(12f, -1f, 1f), ArtistImageLayout(100f, -9f, 9f).bounded())
        assertEquals(ArtistImageLayout(), ArtistImageLayout(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).bounded())
    }

    @Test fun invertRgbPreservesTransparencyAndCanBeReversed() {
        val original = Bitmap.createBitmap(3, 1, Bitmap.Config.ARGB_8888)
        original.setPixel(0, 0, Color.BLACK)
        original.setPixel(1, 0, Color.WHITE)
        original.setPixel(2, 0, Color.argb(128, 40, 80, 120))
        val inverse = ArtistImageEdits.inverted(original)
        assertEquals(Color.WHITE, inverse.getPixel(0, 0))
        assertEquals(Color.BLACK, inverse.getPixel(1, 0))
        val translucent = inverse.getPixel(2, 0)
        assertEquals(128, Color.alpha(translucent))
        assertEquals(215f, Color.red(translucent).toFloat(), 2f)
        assertEquals(175f, Color.green(translucent).toFloat(), 2f)
        val twice = ArtistImageEdits.inverted(inverse)
        assertEquals(Color.BLACK, twice.getPixel(0, 0))
        assertEquals(Color.WHITE, twice.getPixel(1, 0))
        original.recycle(); inverse.recycle(); twice.recycle()
    }

    @Test fun editsSurviveRemoteRefreshAndBackupRelocation(): Unit = runBlocking {
        val dir = Files.createTempDirectory("artist-display").toFile()
        val restored = Files.createTempDirectory("artist-restored").toFile()
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 20"><path d="M0 0L20 20"/></svg>""".toByteArray()
        try {
            val store = ArtistIdentityStore(dir)
            store.install("artist", ArtistImageRole.SIGNATURE, svg, true)
            val signature = store.entries.value.getValue("artist").localSignature
            store.update("artist") { it.copy(display = ArtistDisplayChoice(true, true, true),
                logoLayout = ArtistImageLayout(8f, 0.3f, -0.4f), signatureCaption = false) }
            val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.BLACK)
            val inverse = ArtistImageEdits.inverted(bitmap)
            val png = ByteArrayOutputStream().use { inverse.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
            val local = store.install("artist", ArtistImageRole.LOGO, png, true, "https://example.test", "source")
            store.install("artist", ArtistImageRole.LOGO, svg, false)
            dir.copyRecursively(restored, overwrite = true)
            val again = ArtistIdentityStore(restored)
            val entry = again.entries.value.getValue("artist")
            assertEquals(local, entry.chosen(ArtistImageRole.LOGO))
            assertEquals(signature, entry.localSignature)
            assertEquals(ArtistDisplayChoice(true, true, true), entry.display)
            assertEquals(ArtistImageLayout(8f, 0.3f, -0.4f), entry.logoLayout)
            assertEquals(ArtistImageLayout(), entry.signatureLayout)
            assertFalse(entry.signatureCaption)
            val decoded = android.graphics.BitmapFactory.decodeFile(again.file(local)!!.path)
            assertEquals(Color.WHITE, decoded.getPixel(0, 0))
            assertEquals("https://example.test", local.source)
            bitmap.recycle(); inverse.recycle(); decoded.recycle()
        } finally { dir.deleteRecursively(); restored.deleteRecursively() }
    }

    @Test fun oldIdentityIndexUsesNewDefaults() {
        val dir = Files.createTempDirectory("artist-old").toFile()
        try {
            java.io.File(dir, "index.json").writeText("""{"artist":{"checkedAt":12}}""")
            val entry = ArtistIdentityStore(dir).entries.value.getValue("artist")
            assertNull(entry.display)
            assertEquals(ArtistImageLayout(), entry.logoLayout)
            assertTrue(entry.signatureCaption)
        } finally { dir.deleteRecursively() }
    }
}
