package com.prism.music

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.prism.music.data.lastfm.LastFm
import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.Song
import com.prism.music.data.spotify.TokenBox
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class LastFmHotfixTest {
    @Test fun personalCredentialsUseOriginalSessionAndSignedScrobblePath(): Unit = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val requests = java.util.concurrent.LinkedBlockingQueue<Map<String, String>>()
        val key = "a".repeat(32)
        val secret = "b".repeat(32)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val form = request.body as FormBody
            val values = (0 until form.size).associate { form.name(it) to form.value(it) }
            assertNull(request.header("Cookie"))
            assertEquals(key, values["api_key"])
            assertEquals(LastFm.signature(values - "api_sig", secret), values["api_sig"])
            requests += values
            val body = if (values["method"] == "auth.getSession") """{"session":{"name":"listener","key":"session"}}""" else "{}"
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val box = object : TokenBox {
            override fun seal(plain: String) = plain.reversed() + "!"
            override fun open(sealed: String) = sealed.removeSuffix("!").reversed()
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val lastFm = LastFm(app, client, scope, box)
            assertTrue(lastFm.setCredentials(key, secret))
            assertTrue(lastFm.authUrl().contains(key))
            assertTrue(lastFm.finishSignIn("token"))
            lastFm.setOptions(LastFm.Options(firstArtistOnly = true, loveOnLike = true))
            val song = Song("id", "Track (feat. Guest)", listOf(ArtistRef("Lead"), ArtistRef("Guest")), durationSec = 60)
            lastFm.started(song)
            lastFm.heard(song, 31_000)
            lastFm.flush()
            val scrobble = requests.first { it["method"] == "track.scrobble" }
            assertEquals("session", scrobble["sk"])
            assertEquals("Track (feat. Guest)", scrobble["track[0]"])
            assertEquals("Lead", scrobble["artist[0]"])
            val reopened = LastFm(app, client, scope, box)
            assertEquals("listener", reopened.user.value)
            assertTrue(reopened.options.value.loveOnLike)
            assertTrue(reopened.available)
        } finally { scope.cancel() }
    }
}
