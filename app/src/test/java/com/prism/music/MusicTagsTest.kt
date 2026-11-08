package com.prism.music

import com.prism.music.data.ArtistFontGroup
import com.prism.music.data.ArtistFontMode
import com.prism.music.data.ArtistTypographyConfig
import com.prism.music.data.artists.ArtistImageLayout
import com.prism.music.data.db.*
import com.prism.music.data.meta.*
import com.prism.music.data.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class MusicTagsTest {
    private class Dao : MetaDao {
        val entries = hashMapOf<String, GenreEntity>()
        override suspend fun genre(key: String) = entries[key]
        override suspend fun genres(keys: List<String>) = keys.mapNotNull(entries::get)
        override suspend fun putGenre(e: GenreEntity) { entries[e.key] = e }
        override suspend fun clearGenresBefore(before: Long) {}
        override suspend fun motion(key: String): MotionArtEntity? = null
        override suspend fun putMotion(e: MotionArtEntity) {}
    }

    @Test fun regionalGenresStaySpecificWhileLegacyRadioCategoriesRemainUnchanged() {
        assertTrue("Salsa" in MusicTags.genres("Salsa"))
        assertTrue("Cumbia" in MusicTags.genres("Cumbia"))
        assertTrue("Regional Mexican" in MusicTags.genres("Banda Norteña"))
        assertTrue("Ballad" in MusicTags.genres("Baladas"))
        assertEquals("Pop", Genres.normalize("Salsa"))
        assertTrue("Hip-hop" in MusicTags.genres("Rap"))
    }

    @Test fun moodsAreExplicitAndNeverInferredFromAGenreOrNegativePhrase() {
        assertTrue(MusicTags.explicit("Salsa").none { it in MusicTags.moods })
        assertEquals(setOf("mood:romantic"), MusicTags.explicit("Romántico"))
        assertTrue(MusicTags.explicit("not sad").isEmpty())
        assertTrue(MusicTags.explicit("romantic").isEmpty().not())
        assertTrue(MusicTags.explicit("romantic", includeMoods = false).isEmpty())
        assertFalse("mood:sad" in MusicTags.supportedTags(mapOf("romantic" to 100, "sad" to 2)))
        assertFalse("Pop" in MusicTags.supportedTags(mapOf("female vocalists" to 100)))
    }

    @Test fun smartFontsAcceptRegionalGenresButNeverMoodsOrOverlappingGroups() {
        val config = ArtistTypographyConfig(ArtistFontMode.SMART, "standard", listOf(
            ArtistFontGroup("a", setOf("Salsa", "Cumbia"), "serif"),
            ArtistFontGroup("b", setOf("Salsa", "Rock", "mood:romantic"), "condensed"),
        )).sanitized()
        assertEquals(setOf("Rock"), config.groups[1].genres)
        assertEquals("serif", config.fontForGenres(listOf("Salsa", "Pop"), 0, listOf("standard", "serif", "condensed")))
        assertEquals("standard", config.fontForGenres(listOf("mood:romantic"), 0, listOf("standard", "serif")))
    }

    @Test fun oldImagePositionsMigrateWithoutJumpingAndCanNowMoveAcrossFullHeader() {
        val old = ArtistImageLayout(4f, 0.2f, -0.8f)
        val migrated = old.forHeader(400f, 72f)
        assertTrue(migrated.headerCoordinates)
        assertEquals(old.y * 72f, migrated.y * 400f, 0.001f)
        assertEquals(migrated, migrated.forHeader(400f, 72f))
        assertEquals(-400f, migrated.copy(y = -1f).bounded().y * 400f, 0f)
        assertEquals(4f, migrated.scale, 0f)
    }

    @Test fun cachedExactSongGenresDoNotLeakToOtherSongsOrTurnIntoMoods(): Unit = runBlocking {
        val dao = Dao()
        dao.entries["it1:artist"] = GenreEntity("it1:artist", "one\tSalsa|Pop\ntwo\tCumbia", 1)
        val meta = MetaRepository(OkHttpClient(), dao, tagKey = { "" })
        val songs = listOf(Song("1", "One", listOf(ArtistRef("Artist"))),
            Song("2", "Two", listOf(ArtistRef("Artist"))), Song("3", "Unknown", listOf(ArtistRef("Artist"))))
        val tags = meta.cachedSongTags(songs)
        assertTrue("Salsa" in tags.getValue("1"))
        assertFalse("Salsa" in tags.getValue("2"))
        assertTrue(tags.getValue("3").isEmpty())
        assertTrue(tags.values.flatten().none { it in MusicTags.moods })
        assertTrue("Salsa" in meta.cachedArtistTags("Artist", "Pop"))
    }

    @Test fun trackTagsAreCachedAndRequestsContainNoSessionHeaders(): Unit = runBlocking {
        val dao = Dao()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val request = chain.request()
            assertEquals("ws.audioscrobbler.com", request.url.host)
            assertNull(request.header("Cookie"))
            assertNull(request.header("Authorization"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"toptags":{"@attr":{"artist":"Artist","track":"One"},"tag":[{"name":"romantic","count":100},{"name":"sad","count":1}]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val forbidden = OkHttpClient.Builder().addInterceptor { error("YouTube client's transport must not be used") }.build()
        val meta = MetaRepository(forbidden, dao, tagKey = { "test-key" }, tagsHttp = client)
        val song = Song("1", "One", listOf(ArtistRef("Artist")))
        val batches = mutableListOf<Map<String, Set<String>>>()
        meta.resolveSongTags(listOf(song)) { batches += it }
        assertEquals(1, requests)
        assertTrue("mood:romantic" in batches.last().getValue("1"))
        assertFalse("mood:sad" in batches.last().getValue("1"))
        meta.resolveSongTags(listOf(song)) {}
        assertEquals(1, requests)
        assertTrue("mood:romantic" in meta.cachedSongTags(listOf(song)).getValue("1"))
    }

    @Test fun aDifferentTrackIsRejectedAndMissingKeyDoesNotRequestAnything(): Unit = runBlocking {
        val dao = Dao()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"toptags":{"@attr":{"artist":"Other Artist","track":"One"},"tag":[{"name":"romantic","count":100}]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val song = Song("1", "One", listOf(ArtistRef("Artist")))
        MetaRepository(client, dao, tagKey = { "" }, tagsHttp = client).resolveSongTags(listOf(song)) {}
        assertEquals(0, requests)
        MetaRepository(client, dao, tagKey = { "test" }, tagsHttp = client).resolveSongTags(listOf(song)) {}
        assertEquals(1, requests)
        assertTrue(dao.entries.isEmpty())
    }

    @Test fun repeatedServiceErrorsStopWithoutCachingInventedLabels(): Unit = runBlocking {
        val dao = Dao()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(503).message("Unavailable")
                .body("".toResponseBody()).build()
        }.build()
        val songs = (1..10).map { Song("$it", "Song $it", listOf(ArtistRef("Artist"))) }
        MetaRepository(client, dao, tagKey = { "test" }, tagsHttp = client).resolveSongTags(songs) {}
        assertEquals(3, requests)
        assertTrue(dao.entries.isEmpty())
    }
}
