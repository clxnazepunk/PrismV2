package com.prism.music

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

    @Test fun explicitConnectionCheckDistinguishesRejectionAndNetworkFailure(): Unit = runBlocking {
        var reply: Pair<Int, String>? = 200 to """{"toptags":{"tag":[]}}"""
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = reply ?: throw java.io.IOException("offline")
            val request = chain.request()
            assertEquals("track.gettoptags", request.url.queryParameter("method"))
            assertEquals("Paranoid Android", request.url.queryParameter("track"))
            assertNull(request.header("Cookie"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody()).build()
        }.build()
        val meta = MetaRepository(OkHttpClient(), Dao(), tagsHttp = client)
        assertEquals(MetaRepository.TagConnection.VALID, meta.checkTagConnection("test"))
        reply = 403 to """{"error":10,"message":"Invalid API key"}"""
        assertEquals(MetaRepository.TagConnection.INVALID, meta.checkTagConnection("test"))
        reply = null
        assertEquals(MetaRepository.TagConnection.UNAVAILABLE, meta.checkTagConnection("test"))
    }

    @Test fun aTrackWithoutLastFmMetadataDoesNotBlockFollowingTracks(): Unit = runBlocking {
        val requested = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val title = requireNotNull(request.url.queryParameter("track"))
            requested += title
            val body = if (title == "Missing") """{"error":6,"message":"Track not found"}"""
                else """{"toptags":{"@attr":{"artist":"Artist","track":"Found"},"tag":[{"name":"romantic","count":100}]}}"""
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val meta = MetaRepository(OkHttpClient(), Dao(), tagKey = { "test" }, tagsHttp = client)
        val songs = listOf("Missing", "Found").map { Song(it, it, listOf(ArtistRef("Artist"))) }
        meta.resolveSongTags(songs) {}
        meta.resolveSongTags(songs) {}
        assertEquals(listOf("Missing", "Found"), requested)
        assertEquals(emptySet<String>(), meta.cachedSongTags(songs)["Missing"])
        assertEquals(setOf("mood:romantic"), meta.cachedSongTags(songs)["Found"])
    }

    @Test fun regionalGenresStaySpecificWhileLegacyRadioCategoriesRemainUnchanged() {
        assertEquals(setOf("Salsa"), MusicTags.genres("Salsa"))
        assertEquals(setOf("Salsa", "Pop"), MusicTags.genres("Salsa|Pop"))
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
        assertEquals(setOf("Salsa", "mood:romantic"), MusicTags.supportedTags(mapOf("Salsa" to 100, "romantic" to 80, "romántico" to 70)))
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
    @Test fun aPlaylistCanKeepUnknownSongsUnknownWithoutChangingLegacyDefaults(): Unit = runBlocking {
        val dao = Dao()
        val noNetwork = OkHttpClient.Builder().addInterceptor { error("Unknown artist must not request metadata") }.build()
        val meta = MetaRepository(noNetwork, dao, tagKey = { "" })
        val unknown = Song("unknown", "Untitled", emptyList())
        val exact = mutableMapOf<String, String>()
        meta.resolveGenres(listOf(unknown), refineInline = false, guessMissing = false) { exact += it }
        assertTrue(exact.isEmpty())
        meta.resolveGenres(listOf(unknown), refineInline = false) { exact += it }
        assertEquals("Pop", exact["unknown"])
    }

    @Test fun largePlaylistsStopAtFortyUncachedTrackRequests(): Unit = runBlocking {
        val dao = Dao()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val title = chain.request().url.queryParameter("track")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"toptags":{"@attr":{"artist":"Artist","track":"$title"},"tag":[{"name":"romantic","count":100}]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val songs = (1..45).map { Song("$it", "Song $it", listOf(ArtistRef("Artist"))) }
        MetaRepository(client, dao, tagKey = { "test" }, tagsHttp = client).resolveSongTags(songs) {}
        assertEquals(40, requests)
        assertEquals(40, dao.entries.size)
    }

    @Test fun visibleImagePreviewKeepsTheSameCombinedLayoutAsTheSavedProfile() {
        val together = com.prism.music.data.artists.ArtistDisplayChoice(true, true, true)
        assertEquals(together, together.preview(com.prism.music.data.artists.ArtistImageRole.LOGO))
        assertEquals(together, together.preview(com.prism.music.data.artists.ArtistImageRole.SIGNATURE))
        val onlyLogo = com.prism.music.data.artists.ArtistDisplayChoice(true, false, false)
        assertEquals(com.prism.music.data.artists.ArtistDisplayChoice(false, true, false),
            onlyLogo.preview(com.prism.music.data.artists.ArtistImageRole.SIGNATURE))
    }

    @Test fun lastFmGenresCanBeShownWithoutLegacyMetadataAndMoodsNeverBecomeGenres() {
        assertEquals("Rock", MusicTags.displayGenre(setOf("Rock", "mood:romantic"), null))
        assertEquals("Salsa", MusicTags.displayGenre(setOf("Salsa"), "Pop"))
        assertNull(MusicTags.displayGenre(setOf("mood:romantic"), null))
    }

    @Test fun nonLatinArtistNamesAreComparedAsNamesRatherThanEmptyAsciiKeys(): Unit = runBlocking {
        val dao = Dao()
        var correct = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val artist = if (correct) "周杰倫" else "林俊傑"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"toptags":{"@attr":{"artist":"$artist","track":"夜曲"},"tag":[{"name":"romantic","count":100}]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val meta = MetaRepository(client, dao, tagKey = { "test" }, tagsHttp = client)
        val song = Song("unicode", "夜曲", listOf(ArtistRef("周杰倫")))
        meta.resolveSongTags(listOf(song)) {}
        assertTrue(dao.entries.isEmpty())
        correct = true
        meta.resolveSongTags(listOf(song)) {}
        assertTrue("mood:romantic" in meta.cachedSongTags(listOf(song)).getValue("unicode"))
    }

    @Test fun paginatedPlaylistBatchesShareOneBudgetUntilTheListenerRefreshes(): Unit = runBlocking {
        val dao = Dao()
        var requests = 0
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            val title = chain.request().url.queryParameter("track")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"toptags":{"@attr":{"artist":"Artist","track":"$title"},"tag":[{"name":"romantic","count":100}]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val meta = MetaRepository(client, dao, tagKey = { "test" }, tagsHttp = client)
        val first = Song("first", "First", listOf(ArtistRef("Artist")))
        val second = Song("second", "Second", listOf(ArtistRef("Artist")))
        val third = Song("third", "Third", listOf(ArtistRef("Artist")))
        val visit = TagLookupBudget(2)
        meta.resolveSongTags(listOf(first), budget = visit) {}
        meta.resolveSongTags(listOf(first, second, third), budget = visit) {}
        assertEquals(2, requests)
        meta.resolveSongTags(listOf(first, second, third), budget = visit) {}
        assertEquals(2, requests)
        meta.resolveSongTags(listOf(first, second, third), budget = TagLookupBudget(2)) {}
        assertEquals(3, requests)
    }

}
