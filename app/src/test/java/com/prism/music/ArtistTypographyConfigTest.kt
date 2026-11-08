package com.prism.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ArtistTypographyConfigTest {
    private val fonts = listOf("standard", "serif", "condensed")

    @Test fun newInstallationUsesStandardAndFixedFontCanBeSelected() {
        val config = ArtistTypographyConfig()
        assertEquals(ArtistFontMode.FIXED, config.mode)
        assertEquals("standard", config.fontKey("Hip-hop", 2, fonts))
        assertEquals("serif", config.copy(fallback = "serif").fontKey("Pop", 0, fonts))
    }

    @Test fun removedSmartModeAlwaysMigratesToFixedWithoutLosingTheChosenFont() {
        val config = ArtistTypographyConfig(ArtistFontMode.SMART, "serif").sanitized()
        assertEquals(ArtistFontMode.FIXED, config.mode)
        assertEquals("serif", config.fallback)
        assertEquals("serif", config.fontKey("Rock", 0, fonts))
    }

    @Test fun unsupportedFontsFallBackAndOriginalModeRemainsAvailable() {
        assertEquals("standard", ArtistTypographyConfig(fallback = "deleted").fontKey("Rock", 0, fonts))
        assertNull(ArtistTypographyConfig(ArtistFontMode.PRISM).fontKey("Rock", 0, fonts))
    }

    @Test fun randomUsesVisitSeedWithoutChangingDuringRecomposition() {
        val config = ArtistTypographyConfig(mode = ArtistFontMode.RANDOM)
        assertEquals(config.fontKey(null, -123, fonts), config.fontKey("Pop", -123, fonts))
        assertEquals(fonts.toSet(), (0..2).map { config.fontKey(null, it, fonts) }.toSet())
        assertTrue(config.fontKey(null, Int.MIN_VALUE, fonts) in fonts)
    }

    @Test fun oldSmartBackupWithGroupsIsAcceptedAndPreservesIndividualChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("artist_prefs", 0)
        prefs.edit().clear().putString("sig:artist", "condensed")
            .putString("typography_config", """{"mode":"SMART","fallback":"serif","groups":[{"id":"g","genres":["Rock"],"font":"condensed"}]}""").commit()
        val artist = ArtistPrefs(context)
        assertEquals(ArtistFontMode.FIXED, artist.typography.value.mode)
        assertEquals("serif", artist.typography.value.fallback)
        assertEquals("condensed", artist.signature("artist"))
        artist.setTypography(artist.typography.value.copy(mode = ArtistFontMode.RANDOM))
        assertEquals(ArtistFontMode.RANDOM, ArtistPrefs(context).typography.value.mode)
        assertEquals("condensed", artist.signature("artist"))
    }

    @Test fun missingAndCorruptPreferencesUseDefaultsWithoutErasingIndividualChoices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("artist_prefs", 0)
        prefs.edit().clear().putString("sig:artist", "serif").commit()
        assertEquals(ArtistTypographyConfig(), ArtistPrefs(context).typography.value)
        prefs.edit().putString("typography_config", "{broken").commit()
        val artist = ArtistPrefs(context)
        assertEquals(ArtistTypographyConfig(), artist.typography.value)
        assertEquals("serif", artist.signature("artist"))
    }

    @Test fun optionalTagKeyAndFontPreferencesReloadIndependently() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("artist_prefs", 0).edit().clear().commit()
        val artist = ArtistPrefs(context)
        artist.setMusicTagKey(" test-key ")
        artist.setTypography(ArtistTypographyConfig(ArtistFontMode.RANDOM, "serif"))
        val next = ArtistPrefs(context)
        assertEquals("test-key", next.musicTagKey.value)
        assertEquals(ArtistFontMode.RANDOM, next.typography.value.mode)
        next.setMusicTagKey("")
        artist.reload()
        assertEquals("", artist.musicTagKey.value)
        assertEquals("serif", artist.typography.value.fallback)
    }
}
