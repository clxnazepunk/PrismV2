package com.prism.music

import com.prism.music.data.artists.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class ArtistIdentityStoreTest {
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 300 100"><path d="M0 0L20 20"/></svg>""".toByteArray()
    private fun directory() = Files.createTempDirectory("prism-artists").toFile()

    @Test fun eachRoleKeepsIndependentOrderSelectionAndEffectsAfterReload(): Unit = runBlocking {
        val dir = directory()
        try {
            val store = ArtistIdentityStore(dir)
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="20" height="10"/>""".toByteArray()
            val first = store.install("artist", ArtistImageRole.LOGO, svg, true)
            store.update("artist") { it.withLayout(ArtistImageRole.LOGO, first, ArtistImageLayout(scale = 4f, inverted = true)) }
            val second = store.install("artist", ArtistImageRole.LOGO, svg, true)
            val signature = store.install("artist", ArtistImageRole.SIGNATURE, svg, true)
            store.update("artist") { it.cycle(ArtistImageRole.LOGO).reorder(ArtistImageRole.LOGO, second.file, 0) }
            val reopened = ArtistIdentityStore(dir)
            val entry = reopened.entries.value.getValue("artist")
            assertEquals(listOf(second, first), entry.assets(ArtistImageRole.LOGO))
            assertEquals(first, entry.chosen(ArtistImageRole.LOGO))
            assertEquals(second, entry.cycle(ArtistImageRole.LOGO).chosen(ArtistImageRole.LOGO))
            assertEquals(signature, entry.chosen(ArtistImageRole.SIGNATURE))
            assertEquals(4f, entry.layout(ArtistImageRole.LOGO).scale, 0f)
            assertTrue(entry.layout(ArtistImageRole.LOGO).inverted)
            reopened.removeLocal("artist", ArtistImageRole.LOGO, first.file)
            assertNull(reopened.file(first))
            assertNotNull(reopened.file(second))
            assertNotNull(reopened.file(signature))
        } finally { dir.deleteRecursively() }
    }

    @Test fun switchingThemesRestoresTheWholeArtistPresentationAfterReopen(): Unit = runBlocking {
        val dir = directory()
        try {
            val store = ArtistIdentityStore(dir)
            val a = store.install("id", ArtistImageRole.LOGO, svg, true)
            val b = store.install("id", ArtistImageRole.LOGO, svg, true)
            val s = store.install("id", ArtistImageRole.SIGNATURE, svg, true)
            store.update("id") { it.copy(themes = listOf(ArtistTheme("one", "One", 5, a, s), ArtistTheme("two", "Two", 8, b)), activeThemeId = "one") }
            val image = android.graphics.Bitmap.createBitmap(5, 5, android.graphics.Bitmap.Config.ARGB_8888)
            val output = java.io.ByteArrayOutputStream()
            image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            image.recycle()
            val frame = ArtistPhotoFrame(false, 2f, 0.4f, -0.6f)
            store.setThemePhoto("id", "one", output.toByteArray(), frame)
            store.update("id") { it.cycleTheme() }
            var entry = ArtistIdentityStore(dir).entries.value.getValue("id")
            assertEquals(8, entry.activeTheme?.color)
            assertEquals(b, entry.chosen(ArtistImageRole.LOGO))
            assertNull(entry.chosen(ArtistImageRole.SIGNATURE))
            assertNull(entry.activeTheme?.photograph)
            store.update("id") { it.cycleTheme().cycle(ArtistImageRole.LOGO).reorderTheme("one", 1) }
            entry = ArtistIdentityStore(dir).entries.value.getValue("id")
            assertEquals(listOf("two", "one"), entry.themes.map { it.id })
            assertEquals(5, entry.activeTheme?.color)
            assertEquals(b, entry.chosen(ArtistImageRole.LOGO))
            assertEquals(s, entry.chosen(ArtistImageRole.SIGNATURE))
            assertEquals(frame, entry.activeTheme?.photoFrame)
            val photo = entry.activeTheme?.photograph!!
            assertNotNull(store.file(photo))
            store.update("id") { current ->
                current.editTheme("two") { it.copy(photograph = photo, photoFrame = frame) }
            }
            assertTrue(store.adjustThemePhoto("id", "two", photo, frame.copy(zoom = 3f)))
            assertEquals(3f, ArtistIdentityStore(dir).entries.value.getValue("id").themes
                .first { it.id == "two" }.photoFrame!!.zoom, 0f)
            assertEquals(frame, store.entries.value.getValue("id").activeTheme?.photoFrame)
            store.deleteTheme("id", "one")
            assertNotNull(store.file(photo))
            store.setThemePhoto("id", "two", null)
            assertNull(store.entries.value.getValue("id").activeTheme)
            assertNotNull(store.file(b))
            assertNull(store.file(entry.activeTheme?.photograph))
        } finally { dir.deleteRecursively() }
    }

    @Test fun logoSignatureAndArtistsWithSimilarIdsStayIndependent(): Unit = runBlocking {
        val dir = directory()
        try {
            val store = ArtistIdentityStore(dir)
            store.install("a/b", ArtistImageRole.LOGO, svg, true)
            store.install("a/b", ArtistImageRole.SIGNATURE, svg, true)
            store.install("a_b", ArtistImageRole.LOGO, svg, true)
            val first = store.entries.value.getValue("a/b")
            val second = store.entries.value.getValue("a_b")
            assertNotEquals(first.localLogo!!.file, first.localSignature!!.file)
            assertNotEquals(first.localLogo.file, second.localLogo!!.file)
            assertEquals(3f, first.localLogo.aspect, 0.0001f)
        } finally { dir.deleteRecursively() }
    }

    @Test fun remoteRefreshCannotOverwriteLocalChoiceAndResetRevealsRemote(): Unit = runBlocking {
        val dir = directory()
        try {
            val store = ArtistIdentityStore(dir)
            val remote = store.install("artist", ArtistImageRole.LOGO, svg, false)
            val local = store.install("artist", ArtistImageRole.LOGO, svg, true)
            val newer = store.install("artist", ArtistImageRole.LOGO, svg, false)
            assertEquals(local, store.entries.value.getValue("artist").chosen(ArtistImageRole.LOGO))
            assertFalse(java.io.File(dir, remote.file).exists())
            store.clearLocal("artist", ArtistImageRole.LOGO)
            assertEquals(newer, store.entries.value.getValue("artist").chosen(ArtistImageRole.LOGO))
            assertFalse(java.io.File(dir, local.file).exists())
            assertNull(store.entries.value.getValue("artist").chosen(ArtistImageRole.LOGO, automatic = false))
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidImagePreservesExistingResourceAndLeavesNoExtraFile(): Unit = runBlocking {
        val dir = directory()
        try {
            val store = ArtistIdentityStore(dir)
            val original = store.install("artist", ArtistImageRole.LOGO, svg, true)
            val before = dir.list()!!.toSet()
            try { store.install("artist", ArtistImageRole.LOGO, "not an image".toByteArray(), true); fail() }
            catch (_: IllegalArgumentException) { }
            assertEquals(original, store.entries.value.getValue("artist").localLogo)
            assertEquals(before, dir.list()!!.toSet())
        } finally { dir.deleteRecursively() }
    }

    @Test fun resourcesReopenAndRelocateWithoutAbsolutePaths(): Unit = runBlocking {
        val original = directory()
        val restored = directory()
        try {
            val store = ArtistIdentityStore(original)
            store.install("artist", ArtistImageRole.SIGNATURE, svg, true)
            original.copyRecursively(restored, overwrite = true)
            val again = ArtistIdentityStore(restored)
            val resource = again.entries.value.getValue("artist").localSignature!!
            assertEquals(restored.canonicalFile, again.file(resource)!!.parentFile.canonicalFile)
            assertArrayEquals(svg, again.file(resource)!!.readBytes())
            assertFalse(java.io.File(original, "index.json").readText().contains(original.path.replace("\\", "\\\\")))
        } finally { original.deleteRecursively(); restored.deleteRecursively() }
    }

    @Test fun svgCannotReadOutsideFilesAndPathsCannotEscapeStore() {
        for (bad in listOf(
            """<!DOCTYPE svg [<!ENTITY x SYSTEM "file:///private">]><svg/>""",
            """<svg xmlns="http://www.w3.org/2000/svg"><image href="https://example.test/image"/></svg>""",
        )) try { ArtistIdentityStore.imageInfo(bad.toByteArray()); fail() } catch (_: IllegalArgumentException) { }
        val dir = directory()
        try { assertNull(ArtistIdentityStore(dir).file(ArtistImageAsset("../outside.svg", 1f))) }
        finally { dir.deleteRecursively() }
    }

    @Test fun pngIsKeptWithoutCroppingOrReencoding(): Unit = runBlocking {
        val dir = directory()
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(4, 2, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.setPixel(1, 0, android.graphics.Color.RED)
            val png = java.io.ByteArrayOutputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
            bitmap.recycle()
            val store = ArtistIdentityStore(dir)
            val asset = store.install("artist", ArtistImageRole.LOGO, png, true)
            assertTrue(asset.file.endsWith(".png"))
            assertArrayEquals(png, store.file(asset)!!.readBytes())
            assertEquals(2f, asset.aspect, 0.0001f)
            val decoded = android.graphics.BitmapFactory.decodeFile(store.file(asset)!!.path)
            assertEquals(4, decoded.width)
            assertEquals(2, decoded.height)
            assertEquals(android.graphics.Color.TRANSPARENT, decoded.getPixel(0, 0))
            assertEquals(android.graphics.Color.RED, decoded.getPixel(1, 0))
            decoded.recycle()
        } finally { dir.deleteRecursively() }
    }

    @Test fun boundedDownloadsRejectOversizedResponses() {
        try { ArtistIdentities.bounded(ByteArrayInputStream(ByteArray(1025)), 1024); fail() }
        catch (_: IllegalArgumentException) { }
    }
}
