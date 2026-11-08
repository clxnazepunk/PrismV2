package com.prism.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.*
import com.prism.music.data.innertube.InnerTube
import com.prism.music.data.meta.Genres
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ArtistTypographyConfigTest {
    private val fonts = listOf("standard", "serif", "condensed")

    @Test fun newInstallationUsesStandardWithoutAutomaticDecorativeFont() {
        val config = ArtistTypographyConfig()
        assertEquals(ArtistFontMode.FIXED, config.mode)
        assertEquals("standard", config.fontKey("Hip-hop", 2, fonts))
        assertEquals("standard", config.fontKey(null, 0, fonts))
        assertEquals("serif", config.copy(fallback = "serif").fontKey("Pop", 0, fonts))
    }

    @Test fun groupsMatchSeveralPlaylistGenresAndUnknownUsesChosenFallback() {
        val config = ArtistTypographyConfig(ArtistFontMode.SMART, "serif",
            listOf(ArtistFontGroup("a", setOf("Hip-hop", "Rock"), "condensed")))
        assertEquals("condensed", config.fontKey("Hip-hop", 0, fonts))
        assertEquals("condensed", config.fontKey("Rock", 0, fonts))
        assertEquals("serif", config.fontKey("Jazz", 0, fonts))
        assertEquals("serif", config.fontKey(null, 0, fonts))
        assertEquals("serif", config.fontKey("Salsa", 0, fonts))
    }

    @Test fun duplicatesCannotCrossGroupsEvenAfterImport() {
        val config = ArtistTypographyConfig(groups = listOf(
            ArtistFontGroup("a", setOf("Hip-hop", "Rock"), "condensed"),
            ArtistFontGroup("b", setOf("Rock", "Jazz"), "serif"),
            ArtistFontGroup("a", setOf("Pop"), "serif"),
            ArtistFontGroup("c", setOf("nonexistent"), "serif"),
        )).sanitized()
        assertEquals(listOf("a", "b"), config.groups.map { it.id })
        assertEquals(setOf("Jazz"), config.groups[1].genres)
        val assigned = config.groups.flatMap { it.genres }
        assertEquals(assigned.size, assigned.toSet().size)
        assertTrue(assigned.all { it in Genres.all })
    }

    @Test fun removedGroupReleasesItsGenres() {
        val a = ArtistFontGroup("a", setOf("Hip-hop"), "serif")
        val config = ArtistTypographyConfig(groups = listOf(a))
        val changed = config.copy(groups = config.groups.filterNot { it.id == "a" } +
            ArtistFontGroup("b", setOf("Hip-hop"), "condensed")).sanitized()
        assertEquals("b", changed.groups.single().id)
        assertEquals(setOf("Hip-hop"), changed.groups.single().genres)
    }

    @Test fun unsupportedFontsFallBackSafelyAndOriginalModeRemainsAvailable() {
        val config = ArtistTypographyConfig(ArtistFontMode.SMART, "deleted",
            listOf(ArtistFontGroup("a", setOf("Rock"), "deleted")))
        assertEquals("standard", config.fontKey("Rock", 0, fonts))
        assertNull(config.copy(mode = ArtistFontMode.PRISM).fontKey("Rock", 0, fonts))
    }

    @Test fun randomUsesVisitSeedWithoutChangingDuringRecomposition() {
        val config = ArtistTypographyConfig(mode = ArtistFontMode.RANDOM)
        assertEquals(config.fontKey(null, -123, fonts), config.fontKey("Pop", -123, fonts))
        assertEquals(fonts.toSet(), (0..2).map { config.fontKey(null, it, fonts) }.toSet())
        assertTrue(config.fontKey(null, Int.MIN_VALUE, fonts) in fonts)
    }

    @Test fun storedConfigurationReloadsAlongsideIndividualArtistChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("artist_prefs", 0)
        prefs.edit().clear().commit()
        val artist = ArtistPrefs(context)
        artist.setSignature("artist", "condensed")
        artist.setColor("artist", 0x123456)
        val config = ArtistTypographyConfig(ArtistFontMode.SMART, "serif",
            listOf(ArtistFontGroup("group", setOf("Rock", "Metal"), "condensed")))
        artist.setTypography(config)
        assertEquals(config, ArtistPrefs(context).typography.value)
        assertEquals("condensed", artist.signature("artist"))
        assertEquals(0x123456, artist.colors.value["artist"])
        val restored = config.copy(mode = ArtistFontMode.RANDOM)
        prefs.edit().putString("typography_config",
            InnerTube.json.encodeToString(ArtistTypographyConfig.serializer(), restored)).commit()
        artist.reload()
        assertEquals(restored, artist.typography.value)
        assertEquals("condensed", artist.signature("artist"))
    }

    @Test fun oldPreferencesAndMalformedSettingsDoNotEraseIndividualChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("artist_prefs", 0)
        prefs.edit().clear().putString("sig:artist", "serif").commit()
        assertEquals(ArtistTypographyConfig(), ArtistPrefs(context).typography.value)
        prefs.edit().putString("typography_config", "{broken").commit()
        val artist = ArtistPrefs(context)
        assertEquals(ArtistTypographyConfig(), artist.typography.value)
        assertEquals("serif", artist.signature("artist"))
    }
}
