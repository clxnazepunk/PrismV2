package com.prism.music

import com.prism.music.data.Backup
import com.prism.music.data.BackupPart
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
        File(resources, "index.json").writeText("""{"artist":{"localLogo":{"file":"$name","aspect":2.0},"logos":[{"file":"$other","aspect":2.0},{"file":"$name","aspect":2.0}],"assetLayouts":{"$name":{"scale":3.0,"grayscale":true}}}}""")
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
        assertFalse(staging.exists())
    }
}
