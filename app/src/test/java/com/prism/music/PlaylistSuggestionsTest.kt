package com.prism.music

import com.prism.music.data.PrismLibrary
import com.prism.music.data.foryou.PlaylistSuggestions
import com.prism.music.data.model.Song
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PlaylistSuggestionsTest {
    private fun song(id: String) = Song(id, id)

    @Test fun discoveryTuningChangesGenreAndArtistRankingsAcrossTheWholePlaylist() {
        val lead = com.prism.music.data.model.ArtistRef("Lead")
        val guest = com.prism.music.data.model.ArtistRef("New")
        val rock = song("rock").copy(artists = listOf(lead))
        val salsa = song("salsa").copy(artists = listOf(com.prism.music.data.model.ArtistRef("Other")))
        val similar = song("familiar").copy(artists = listOf(lead))
        val exploratory = song("new").copy(artists = listOf(guest))
        val tags = mapOf(rock.id to setOf("Rock"), salsa.id to setOf("Salsa"),
            similar.id to setOf("Salsa"), exploratory.id to setOf("Jazz"))
        val existing = listOf(rock, salsa)
        val candidates = listOf(similar, exploratory)
        val close = com.prism.music.data.foryou.PlaylistDiscovery(genre = 0, artist = 0, width = 0)
        val wide = close.copy(genre = 2, artist = 2, width = 100)
        assertEquals(similar, PlaylistSuggestions.rank(existing, candidates, tags, close, emptySet()).first())
        assertEquals(exploratory, PlaylistSuggestions.rank(existing, candidates, tags, wide, emptySet()).first())
        assertEquals(exploratory, PlaylistSuggestions.rank(existing, candidates, tags, close, setOf(similar.id)).first())
        assertEquals(setOf(rock, salsa), PlaylistSuggestions.discoverySeeds(existing, tags, 1).toSet())
    }

    @Test fun addingMovesOnlyTheReplacementToTheEndAndRejectsDuplicates() {
        val old = listOf(song("a"), song("b"), song("c"))
        assertEquals(listOf(song("a"), song("c"), song("fresh")), PlaylistSuggestions.replaceSlot(old, "b", song("fresh")))
        assertEquals(old, PlaylistSuggestions.replaceSlot(old, "b", song("a")))
        assertEquals(listOf(song("a"), song("c")), PlaylistSuggestions.replaceSlot(old, "b", null))
    }

    @Test fun languageWeightsUseOnlyPlaylistAndArtistFilterUsesNamesAndIds() {
        val lead = com.prism.music.data.model.ArtistRef("Lead", "id-a")
        val playlist = listOf(song("es1"), song("es2"), song("en1")).map { it.copy(artists = listOf(lead)) }
        val same = song("same").copy(artists = listOf(com.prism.music.data.model.ArtistRef("lead")))
        val spanish = song("es")
        val english = song("en")
        val french = song("fr")
        val candidates = listOf(same, spanish, english, french)
        val languages = mapOf("es1" to "es", "es2" to "es", "en1" to "en", "es" to "es", "en" to "en", "fr" to "fr")
        val tuning = com.prism.music.data.foryou.PlaylistDiscovery(noRepeatArtists = true, languageSensitivity = 0)
        val low = PlaylistSuggestions.rank(playlist, candidates, emptyMap(), tuning, emptySet(), languages = languages,
            occupied = listOf(song("unadded").copy(artists = listOf(com.prism.music.data.model.ArtistRef("Other")))))
        assertEquals(listOf(spanish, english, french), low)
        assertEquals(french, PlaylistSuggestions.rank(playlist, candidates, emptyMap(), tuning.copy(languageSensitivity = 2),
            emptySet(), languages = languages).first())
        assertEquals(spanish, PlaylistSuggestions.rank(playlist, candidates, mapOf("es1" to setOf("rock"), "es" to setOf("rock")),
            tuning.copy(genre = 0, languageSensitivity = 2), emptySet(), languages = languages).first())
        assertEquals(tuning, com.prism.music.data.foryou.PlaylistDiscovery.decode(tuning.encode()))
        assertFalse(com.prism.music.data.foryou.PlaylistDiscovery.decode("1,1,50,1").noRepeatArtists)
    }

    @Test fun excludesExistingDuplicatesAndPrivateContentWithoutChangingOrder() {
        val existing = listOf(song("a"), song("b"))
        val candidates = listOf(song("b"), song("c"), song("c"), song("local:1"),
            song("private").copy(isUpload = true), song("rss:1"), song("episode").copy(isEpisode = true),
            song("live").copy(isLive = true), song(""), song("d"))
        assertEquals(listOf("c", "d"), PlaylistSuggestions.select(existing, candidates).map { it.id })
        assertEquals(listOf("a", "b"), existing.map { it.id })
        assertEquals(10, candidates.size)
    }

    @Test fun samplesAtMostThreePublicSeedsAcrossThePlaylist() {
        val songs = listOf(song("local:1"), song("private").copy(isUpload = true)) +
            (0..100).map { song("s$it") }
        assertEquals(listOf("s0", "s50", "s100"), PlaylistSuggestions.seeds(songs).map { it.id })
        assertEquals(listOf("a"), PlaylistSuggestions.seeds(List(100) { song("a") }).map { it.id })
        assertTrue(PlaylistSuggestions.seeds(listOf(song("private").copy(isUpload = true))).isEmpty())
    }

    @Test fun capsResultsAndAcceptsInsufficientOrEmptyResults() {
        val candidates = (0..50).map { song("s$it") }
        assertEquals(10, PlaylistSuggestions.select(emptyList(), candidates).size)
        assertEquals(listOf(song("a")), PlaylistSuggestions.select(emptyList(), listOf(song("a"))))
        assertTrue(PlaylistSuggestions.select(emptyList(), emptyList()).isEmpty())
        assertTrue(PlaylistSuggestions.select(emptyList(), candidates, 0).isEmpty())
    }

    @Test fun reusesRadioAndKeepsOtherSeedsWhenOneFails() = runBlocking {
        val asked = ArrayList<String>()
        val out = PlaylistSuggestions.load(listOf(song("a"), song("b"), song("c"))) { seed ->
            asked += seed.id
            if (seed.id == "b") throw IOException("unavailable")
            listOf(seed, song("new"), song("new"))
        }
        assertEquals(listOf("a", "b", "c"), asked)
        assertEquals(listOf(song("new")), out)
    }

    @Test fun emptyPlaylistMakesNoRequestsAndSingleTrackCanSeedRadio() = runBlocking {
        var requests = 0
        val radio: suspend (Song) -> List<Song> = { seed ->
            requests++
            listOf(seed, song("new"))
        }
        assertTrue(PlaylistSuggestions.load(emptyList(), radio = radio).isEmpty())
        assertEquals(0, requests)
        assertEquals(listOf(song("new")), PlaylistSuggestions.load(listOf(song("seed")), radio = radio))
        assertEquals(1, requests)
    }

    @Test fun privateOnlyPlaylistMakesNoRequests() = runBlocking {
        var asked = false
        val out = PlaylistSuggestions.load(listOf(song("private").copy(isUpload = true))) {
            asked = true; emptyList()
        }
        assertFalse(asked)
        assertTrue(out.isEmpty())
    }

    @Test fun propagatesTotalFailureAndCancellation() = runBlocking {
        try {
            PlaylistSuggestions.load(listOf(song("a"))) { throw IOException("offline") }
            fail("a failed service must be retryable")
        } catch (_: IOException) { }
        var calls = 0
        try {
            PlaylistSuggestions.load(listOf(song("a"), song("b"))) {
                calls++; throw CancellationException("left the screen")
            }
            fail("cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, calls)
    }

    @Test fun respectsExistingRecommendationRulesForSeedsAndResults() = runBlocking {
        val asked = ArrayList<String>()
        val out = PlaylistSuggestions.load(
            listOf(song("excluded"), song("a")),
            allowed = { it.id != "excluded" && !it.explicit },
        ) { seed ->
            asked += seed.id
            listOf(song("excluded"), song("explicit").copy(explicit = true), song("new"))
        }
        assertEquals(listOf("a"), asked)
        assertEquals(listOf(song("new")), out)
    }

    @Test fun refreshLeavesOutPreviouslyShownSongsAndRotatesSeeds() = runBlocking {
        val playlist = (0..8).map { song("seed" + it) }
        val first = PlaylistSuggestions.load(playlist) {
            (0..29).map { song("new" + it) }
        }
        val second = PlaylistSuggestions.load(playlist, excluded = first.map { it.id }.toSet(), round = 1) {
            (0..29).map { song("new" + it) }
        }
        assertEquals(10, second.size)
        assertTrue(first.map { it.id }.toSet().intersect(second.map { it.id }.toSet()).isEmpty())
        assertNotEquals(PlaylistSuggestions.seeds(playlist), PlaylistSuggestions.seeds(playlist, 1))
    }

    @Test fun refreshDoesNotPretendThereAreFreshResultsWhenRadioIsExhausted() = runBlocking {
        val out = PlaylistSuggestions.load(listOf(song("seed")), excluded = setOf("old")) {
            listOf(song("seed"), song("old"))
        }
        assertTrue(out.isEmpty())
    }

    @Test fun addingSuggestionAppendsAndSurvivesReopening() {
        val dir = Files.createTempDirectory("prism-suggestions").toFile()
        try {
            val lib = PrismLibrary(dir)
            val id = lib.create("Discovery", listOf(song("a"), song("b")))
            val selected = PlaylistSuggestions.select(lib.list(id)!!.songs, listOf(song("a"), song("c")))
            assertEquals(1, lib.add(id, selected))
            assertEquals(0, lib.add(id, selected))
            assertEquals(listOf("a", "b", "c"), PrismLibrary(dir).list(id)!!.songs.map { it.id })
        } finally { dir.deleteRecursively() }
    }
}
