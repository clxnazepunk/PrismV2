package com.prism.music

import android.app.Application
import android.content.ContextWrapper
import android.net.Uri
import com.prism.music.data.Backup
import com.prism.music.data.OfflineLibrary
import com.prism.music.data.PlaylistCovers
import com.prism.music.data.artists.*
import com.prism.music.data.model.ArtistPage
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArtistPersonalizationBackupTest {
    private val app get() = org.robolectric.RuntimeEnvironment.getApplication()
    private val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 80"><path d="M0 0L20 20"/></svg>""".toByteArray()
    private fun page() = ArtistPage("UCsaved", "Artist", null, null, null,
        listOf(Shelf("Songs", items = listOf(SongItem(Song("song", "Song"))))), null, null)

    @Test fun libraryRestoreKeepsProfileBothImagesAndTheirLocalPriority(): Unit = runBlocking {
        val offline = OfflineLibrary(app)
        offline.saveArtist(page(), permanent = true)
        val original = ArtistIdentityStore(File(app.filesDir, "artist_identity"))
        val logo = original.install("UCsaved", ArtistImageRole.LOGO, svg, true)
        val signature = original.install("UCsaved", ArtistImageRole.SIGNATURE, svg, true)
        val staging = File(app.filesDir, "restore_pending").apply { mkdirs() }
        File(staging, "prism-backup.json").writeText("""{"format":2,"app":"${app.packageName}","parts":["LIBRARY"]}""")
        // Same staged layout as Backup.stage; applyPending itself performs the restore below.
        for (folder in listOf("offline", "artist_identity")) File(app.filesDir, folder).copyRecursively(File(staging, "files/$folder"), overwrite = true)
        offline.clear()
        original.clearLocal("UCsaved", ArtistImageRole.LOGO)
        original.clearLocal("UCsaved", ArtistImageRole.SIGNATURE)
        Backup.applyPending(app)
        val restoredProfiles = OfflineLibrary(app)
        assertTrue(restoredProfiles.isArtistSaved("UCsaved"))
        assertEquals("Artist", restoredProfiles.artist("UCsaved")!!.name)
        val restored = ArtistIdentityStore(File(app.filesDir, "artist_identity"))
        val entry = restored.entries.value.getValue("UCsaved")
        assertEquals(logo, entry.localLogo)
        assertEquals(signature, entry.localSignature)
        assertArrayEquals(svg, restored.file(entry.localLogo)!!.readBytes())
        assertArrayEquals(svg, restored.file(entry.localSignature)!!.readBytes())
        restored.install("UCsaved", ArtistImageRole.LOGO, svg, false, source = "https://example.test/new")
        assertEquals(logo, restored.entries.value.getValue("UCsaved").chosen(ArtistImageRole.LOGO))
    }

    @Test fun settingsOnlyRestoreDoesNotEraseLibraryPersonalizations(): Unit = runBlocking {
        OfflineLibrary(app).saveArtist(page(), permanent = true)
        val resources = ArtistIdentityStore(File(app.filesDir, "artist_identity"))
        resources.install("UCsaved", ArtistImageRole.SIGNATURE, svg, true)
        val staging = File(app.filesDir, "restore_pending").apply { mkdirs() }
        File(staging, "prism-backup.json").writeText("""{"format":2,"app":"${app.packageName}","parts":["SETTINGS"]}""")
        File(staging, "settings.json").writeText("{}")
        Backup.applyPending(app)
        assertTrue(OfflineLibrary(app).isArtistSaved("UCsaved"))
        val again = ArtistIdentityStore(File(app.filesDir, "artist_identity"))
        assertNotNull(again.file(again.entries.value.getValue("UCsaved").localSignature))
    }

    private fun coverContext(files: File, prefix: String) = object : ContextWrapper(app) {
        override fun getFilesDir() = files
        override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefix + name, mode)
    }

    @Test fun restoredLegacyCoversFollowTheNewPrivateDirectory() {
        val root = Files.createTempDirectory("prism-cover-restore").toFile()
        val oldShared = PlaylistCovers.shared
        try {
            val oldFiles = File(root, "old/files").apply { mkdirs() }
            val newFiles = File(root, "new/files").apply { mkdirs() }
            val prefix = "cover-restore-${java.util.UUID.randomUUID()}-"
            val old = PlaylistCovers(coverContext(oldFiles, prefix))
            val photo = File(old.dir, "song-original.jpg").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
            old.useImage(old.songKey("song"), Uri.fromFile(photo).toString())
            old.dir.copyRecursively(File(newFiles, "covers"), overwrite = true)
            val restored = PlaylistCovers(coverContext(newFiles, prefix))
            assertEquals(Uri.fromFile(File(newFiles, "covers/song-original.jpg")).toString(), restored.custom(restored.songKey("song")))
        } finally { PlaylistCovers.shared = oldShared; root.deleteRecursively() }
    }

    @Test fun restoringCoversDoesNotRewriteWebOrUnrelatedExternalImages() {
        val root = Files.createTempDirectory("prism-cover-restore").toFile()
        val oldShared = PlaylistCovers.shared
        try {
            val files = File(root, "files").apply { mkdirs() }
            val prefix = "cover-restore-${java.util.UUID.randomUUID()}-"
            val store = PlaylistCovers(coverContext(files, prefix))
            store.dir.mkdirs()
            File(store.dir, "same.jpg").writeText("owned")
            val external = Uri.fromFile(File(root, "external/same.jpg")).toString()
            store.useImage("external", external)
            store.useImage("web", "https://example.test/same.jpg")
            val again = PlaylistCovers(coverContext(files, prefix))
            assertEquals(external, again.custom("external"))
            assertEquals("https://example.test/same.jpg", again.custom("web"))
        } finally { PlaylistCovers.shared = oldShared; root.deleteRecursively() }
    }
}
