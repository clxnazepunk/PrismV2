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
        val app = org.robolectric.RuntimeEnvironment.getApplication<android.app.Application>()
        val staging = File(app.filesDir, "restore_pending").apply { mkdirs() }
        File(staging, "prism-backup.json").writeText("""{"format":2,"app":"${app.packageName}","parts":["LIBRARY"]}""")
        val resources = File(staging, "files/artist_identity").apply { mkdirs() }
        val name = "local-11111111-1111-1111-1111-111111111111.svg"
        File(resources, name).writeText("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 50"/>""")
        File(resources, "index.json").writeText("""{"artist":{"localLogo":{"file":"$name","aspect":2.0}}}""")
        assertEquals(BackupPart.LIBRARY, Backup.filePart("artist_identity"))
        Backup.applyPending(app)
        val store = ArtistIdentityStore(File(app.filesDir, "artist_identity"))
        val logo = store.entries.value.getValue("artist").localLogo!!
        assertNotNull(store.file(logo))
        assertEquals(2f, logo.aspect, 0.0001f)
        assertFalse(staging.exists())
    }
}
