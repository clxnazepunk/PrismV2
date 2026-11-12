package com.prism.music

import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem
import com.prism.music.ui.components.displayArtists
import com.prism.music.ui.components.displayTitle
import org.junit.Assert.assertEquals
import org.junit.Test

class SongCreditTextTest {
    private val artists = listOf(ArtistRef("Lead"), ArtistRef("Guest"))

    @Test fun localizedCreditUsesLiteralFeatWithoutEditingTheSource() {
        val song = Song("id", "Track (con Guest)", artists)
        assertEquals("Track (feat. Guest)", song.displayTitle)
        assertEquals("Track (feat. Guest)", SongItem(song).displayTitle)
        assertEquals("Lead feat. Guest", song.displayArtists)
        assertEquals("Track (con Guest)", song.title)
        assertEquals("Lead, Guest", song.artistText)
    }

    @Test fun localizedSeparatorFromYouTubeIsPreservedAsStructuredCredit() {
        fun artist(name: String, id: String) = kotlinx.serialization.json.Json.parseToJsonElement(
            """{"text":"$name","navigationEndpoint":{"browseEndpoint":{"browseId":"$id","browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"MUSIC_PAGE_TYPE_ARTIST"}}}}}"""
        ) as kotlinx.serialization.json.JsonObject
        val separator = kotlinx.serialization.json.buildJsonObject { put("text", kotlinx.serialization.json.JsonPrimitive(" con ")) }
        val credits = com.prism.music.data.innertube.Parser.artistsFromRuns(listOf(artist("Lead", "lead"), separator, artist("Guest", "guest")))
        assertEquals(listOf(ArtistRef("Lead", "lead"), ArtistRef("Guest", "guest", featured = true)), credits)
        val song = Song("id", "Original title", credits)
        assertEquals("Lead feat. Guest", song.displayArtists)
        assertEquals("Lead, Guest", song.artistText)
    }

    @Test fun structuredCreditsStayLiteralUnderEnglishAndSpanishLocales() {
        val previous = java.util.Locale.getDefault()
        try {
            for (locale in listOf(java.util.Locale.ENGLISH, java.util.Locale.forLanguageTag("es-MX"))) {
                java.util.Locale.setDefault(locale)
                val song = Song("id", "Title con Guest", listOf(ArtistRef("Lead"), ArtistRef("Guest", featured = true)))
                assertEquals("Title feat. Guest", song.displayTitle)
                assertEquals("Lead feat. Guest", song.displayArtists)
                assertEquals("Title con Guest", song.title)
                assertEquals("Con Altura", song.copy(title = "Con Altura").displayTitle)
            }
        } finally { java.util.Locale.setDefault(previous) }
    }

    @Test fun multipleFeaturedArtistsAndGenuineConAreDistinguishedInBothLocales() {
        val original = java.util.Locale.getDefault()
        try {
            for (locale in listOf(java.util.Locale.ENGLISH, java.util.Locale.forLanguageTag("es-MX"))) {
                java.util.Locale.setDefault(locale)
                val title = "Bailando con mi sombra (con A, B y C) - Remaster"
                val song = Song("id", title, listOf(ArtistRef("Lead"), ArtistRef("A"), ArtistRef("B"), ArtistRef("C")))
                assertEquals("Bailando con mi sombra (feat. A, B y C) - Remaster", song.displayTitle)
                assertEquals("Lead feat. A, B, C", song.displayArtists)
                assertEquals(title, song.title)
                assertEquals("La vida con A", song.copy(title = "La vida con A").displayTitle)
            }
        } finally { java.util.Locale.setDefault(original) }
    }

    @Test fun existingCreditsAndOrdinarySpanishTitlesArePreserved() {
        for (title in listOf("Track (feat. Guest)", "Con Altura", "Track (con calma)", "Track - con mi gente")) {
            assertEquals(title, Song("id", title, artists).displayTitle)
        }
        assertEquals("Track (con Guest)", Song("id", "Track (con Guest)", artists.take(1)).displayTitle)
    }
}
