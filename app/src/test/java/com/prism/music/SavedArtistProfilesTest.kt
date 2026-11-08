package com.prism.music

import android.app.Application
import android.content.ContextWrapper
import com.prism.music.data.OfflineLibrary
import com.prism.music.data.model.ArtistPage
import com.prism.music.data.model.Shelf
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SavedArtistProfilesTest {
    private fun phone(dir: File) = object : ContextWrapper(org.robolectric.RuntimeEnvironment.getApplication()) {
        override fun getFilesDir() = dir
    }

    private fun page(id: String, name: String = id) = ArtistPage(
        id, name, "https://example.test/photo-$id", "Biography", null,
        listOf(Shelf("Songs", items = listOf(SongItem(Song("song-$id", "A song"))))), null, null,
    )

    @Test fun savedProfileSurvivesTheVisitedPageLimitAndReopening() {
        val dir = Files.createTempDirectory("prism-saved-artists").toFile()
        try {
            val store = OfflineLibrary(phone(dir))
            assertTrue(store.saveArtist(page("UCsaved"), permanent = true))
            repeat(OfflineLibrary.MAX_ARTISTS + 5) { i -> store.saveArtist(page("UC$i")) }
            val again = OfflineLibrary(phone(dir))
            assertTrue(again.isArtistSaved("UCsaved"))
            assertEquals("UCsaved", again.artist("UCsaved")!!.id)
            assertEquals(listOf("UCsaved"), again.savedArtistItems().map { it.id })
            assertTrue("photo is retained with the saved profile", "https://example.test/photo-UCsaved" in again.covers())
        } finally { dir.deleteRecursively() }
    }

    @Test fun stoppingPermanentStorageKeepsAnOrdinaryVisitedCopy() {
        val dir = Files.createTempDirectory("prism-saved-artists").toFile()
        try {
            val store = OfflineLibrary(phone(dir))
            store.saveArtist(page("UCsaved"), permanent = true)
            assertTrue(store.removeSavedArtist("UCsaved"))
            assertFalse(store.isArtistSaved("UCsaved"))
            assertTrue(store.savedArtistItems().isEmpty())
            assertEquals("UCsaved", store.artist("UCsaved")!!.id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun similarNamesAndIdsNeverMergeSavedProfiles() {
        val dir = Files.createTempDirectory("prism-saved-artists").toFile()
        try {
            val store = OfflineLibrary(phone(dir))
            store.saveArtist(page("name:A/B", "Same"), permanent = true)
            store.saveArtist(page("name:A_B", "Same"), permanent = true)
            assertEquals(setOf("name:A/B", "name:A_B"), store.savedArtistItems().map { it.id }.toSet())
            assertEquals("name:A/B", store.artist("name:A/B")!!.id)
            assertEquals("name:A_B", store.artist("name:A_B")!!.id)
            assertNull("an ambiguous name must not pick the first profile", store.artistNamed("Same"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun ordinaryCacheAlsoPreservesUnsanitizedIdentity() {
        val dir = Files.createTempDirectory("prism-saved-artists").toFile()
        try {
            val store = OfflineLibrary(phone(dir))
            store.saveArtist(page("name:A/B"))
            store.saveArtist(page("name:A_B"))
            assertEquals("name:A/B", store.artist("name:A/B")!!.id)
            assertEquals("name:A_B", store.artist("name:A_B")!!.id)
        } finally { dir.deleteRecursively() }
    }

    @Test fun previouslyKeptProfilesStillOpen() {
        val dir = Files.createTempDirectory("prism-saved-artists").toFile()
        try {
            val context = phone(dir)
            val store = OfflineLibrary(context)
            store.saveArtist(page("UCold"))
            val files = File(dir, "offline/artists")
            val kept = File(files, "UCold.json").readText()
            // The old unsafe name is read for compatibility, but only for the id its data actually contains.
            File(files, "legacy_name.json").writeText(kept.replace("UCold", "legacy:name"))
            assertEquals("legacy:name", store.artist("legacy:name")!!.id)
            File(files, "wrong_id.json").writeText(kept)
            assertNull(store.artist("wrong:id"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun explicitProfileMayKeepOnlyHeaderButLogoutStillClearsIt() {
        val dir = Files.createTempDirectory("prism-saved-artists").toFile()
        try {
            val store = OfflineLibrary(phone(dir))
            assertTrue(store.saveArtist(page("header").copy(shelves = emptyList()), permanent = true))
            assertNotNull(store.artist("header"))
            store.clear()
            assertFalse(store.isArtistSaved("header"))
            assertTrue(store.savedArtistItems().isEmpty())
            assertNull(store.artist("header"))
        } finally { dir.deleteRecursively() }
    }
}
