package com.prism.music

import com.prism.music.data.Backup
import com.prism.music.data.BackupPart
import com.prism.music.data.PlaylistCovers
import com.prism.music.data.artists.ArtistPhotoFrame
import com.prism.music.data.artists.ArtistIdentityStore
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ArtistIdentityBackupTest {
    @Test fun libraryRestoreBringsArtistImagesAndAssociations() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val staging = File(app.filesDir, "restore_pending").apply { mkdirs() }
        File(staging, "prism-backup.json").writeText("""{"format":2,"app":"${app.packageName}","parts":["LIBRARY"]}""")
        val resources = File(staging, "files/artist_identity").apply { mkdirs() }
        val name = "local-11111111-1111-1111-1111-111111111111.svg"
        File(resources, name).writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50"/>""")
        val other = "local-22222222-2222-2222-2222-222222222222.svg"
        File(resources, other).writeText(File(resources, name).readText())
        val photograph = "local-33333333-3333-3333-3333-333333333333.jpg"
        File(resources, photograph).writeBytes(byteArrayOf(1))
        val covers = File(staging, "files/covers").apply { mkdirs() }
        File(covers, "artist-backup.jpg").writeBytes(byteArrayOf(2))
        val preferences = File(staging, "shared_prefs").apply { mkdirs() }
        File(preferences, "playlist_covers.xml").writeText("""
            <map>
                <string name="artist:artist">file:///old/files/covers/artist-backup.jpg</string>
                <string name="artist_frame:artist:artist">{"fill":false,"zoom":2.0,"y":0.25}</string>
            </map>
        """.trimIndent())
        File(resources, "index.json").writeText("""{"artist":{"localLogo":{"file":"$name","aspect":2.0},"logos":[{"file":"$other","aspect":2.0},{"file":"$name","aspect":2.0}],"assetLayouts":{"$name":{"scale":3.0,"grayscale":true}},"activeThemeId":"theme","themes":[{"id":"theme","name":"Theme","logo":{"file":"$name","aspect":2.0},"photograph":{"file":"$photograph","aspect":2.0},"photoFrame":{"zoom":3.0,"x":-0.5}}]}}""")
        assertEquals(BackupPart.LIBRARY, Backup.filePart("artist_identity"))
        Backup.applyPending(app)
        val store = ArtistIdentityStore(File(app.filesDir, "artist_identity"))
        val logo = store.entries.value.getValue("artist").localLogo!!
        assertNotNull(store.file(logo))
        assertEquals(2f, logo.aspect, 0.0001f)
        val entry = store.entries.value.getValue("artist")
        val role = com.prism.music.data.artists.ArtistImageRole.LOGO
        assertEquals(listOf(other, name), entry.assets(role).map { it.file })
        assertEquals(name, entry.chosen(role)?.file)
        assertEquals(3f, entry.layout(role).scale, 0f)
        assertTrue(entry.layout(role).grayscale)
        assertNotNull(store.file(entry.activeTheme?.photograph))
        assertEquals(ArtistPhotoFrame(zoom = 3f, x = -0.5f), entry.activeTheme?.photoFrame)
        val restoredCovers = PlaylistCovers(app)
        assertEquals(ArtistPhotoFrame(fill = false, zoom = 2f, y = 0.25f), restoredCovers.artistFrame("artist"))
        assertTrue(File(android.net.Uri.parse(restoredCovers.custom(restoredCovers.artistKey("artist"))!!).path!!).isFile)
        assertFalse(staging.exists())
    }
}
