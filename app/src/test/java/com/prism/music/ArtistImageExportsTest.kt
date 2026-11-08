package com.prism.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.artists.*
import com.prism.music.ui.screens.ArtistExportDocument
import com.prism.music.ui.screens.ArtistExportRequest
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistImageExportsTest {
    private fun context() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun pickerUsesTheCurrentMimeAndNameAndCancellationDoesNotExport() {
        val contract = ArtistExportDocument()
        val intent = contract.createIntent(context(), ArtistExportRequest("image/svg+xml", "Artist-logo.svg"))
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
        assertEquals("image/svg+xml", intent.type)
        assertEquals("Artist-logo.svg", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertNull(contract.parseResult(Activity.RESULT_CANCELED, Intent().setData(Uri.parse("content://example/result"))))
        assertEquals("image/webp", ArtistImageExports.mime("local-file.webp"))
        assertFalse(ArtistImageExports.name("../../artist", ArtistImageRole.LOGO, "asset.svg", false).contains('/'))
    }

    @Test fun originalSvgIsCopiedExactlyWithoutRasterizingOrLosingItsTransparency(): Unit = runBlocking {
        val dir = File(context().filesDir, "export-store").apply { mkdirs() }
        val store = ArtistIdentityStore(dir)
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M0 0L20 20"/></svg>""".toByteArray()
        val asset = store.install("artist", ArtistImageRole.SIGNATURE, svg, true)
        val output = File(context().cacheDir, "saved-original.svg")
        ArtistImageExports.write(context(), store, asset, ArtistImageLayout(), Uri.fromFile(output), false)
        assertArrayEquals(svg, output.readBytes())
        assertArrayEquals(svg, store.file(asset)!!.readBytes())
    }

    @Test fun exportedPngAppliesTransparencyWhileOriginalAndBlendSettingsStayIndependent() {
        val app = context()
        val bitmap = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixel(6, 6, Color.BLACK)
        val png = ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        bitmap.recycle()
        val store = ArtistIdentityStore(File(app.filesDir, "export-png-store"))
        val asset = runBlocking { store.install("artist", ArtistImageRole.LOGO, png, true) }
        val output = File(app.cacheDir, "saved-transparent.png")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val task = scope.async { ArtistImageExports.write(app, store, asset,
                ArtistImageLayout(removeBackground = true, blend = "darken"), Uri.fromFile(output), true) }
            val deadline = System.nanoTime() + 15_000_000_000L
            while (!task.isCompleted && System.nanoTime() < deadline) {
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(10)
            }
            assertTrue("PNG export timed out", task.isCompleted)
            runBlocking { task.await() }
            val decoded = BitmapFactory.decodeFile(output.path)
            assertEquals(0, Color.alpha(decoded.getPixel(0, 0)))
            assertEquals("Tiny PNG must not be unnecessarily upscaled", 12, decoded.width)
            assertEquals(12, decoded.height)
            val pixels = IntArray(decoded.width * decoded.height)
            decoded.getPixels(pixels, 0, decoded.width, 0, 0, decoded.width, decoded.height)
            assertTrue("Export lost the foreground at ${decoded.width}x${decoded.height}", pixels.any { Color.alpha(it) > 200 && Color.red(it) < 32 })
            val previewDir = File("build/artist-header-previews").apply { mkdirs() }
            output.copyTo(File(previewDir, "export-transparent.png"), overwrite = true)
            assertArrayEquals(png, store.file(asset)!!.readBytes())
            decoded.recycle()
        } finally { scope.cancel() }
    }
}
