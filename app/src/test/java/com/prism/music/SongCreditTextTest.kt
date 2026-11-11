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

    @Test fun existingCreditsAndOrdinarySpanishTitlesArePreserved() {
        for (title in listOf("Track (feat. Guest)", "Con Altura", "Track (con calma)", "Track - con mi gente")) {
            assertEquals(title, Song("id", title, artists).displayTitle)
        }
        assertEquals("Track (con Guest)", Song("id", "Track (con Guest)", artists.take(1)).displayTitle)
    }
}
