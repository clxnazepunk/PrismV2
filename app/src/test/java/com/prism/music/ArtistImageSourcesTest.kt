package com.prism.music

import com.prism.music.data.artists.ArtistImageSources
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ArtistImageSourcesTest {
    private val header = """<script id="serialized-server-data">[{"artistLogo":{"dictionary":{"width":500,"height":100,"url":"https://example.test/{w}x{h}.{f}"}}}]</script>"""

    @Test fun signatureUsesKnownLicenseAndSvgRatherThanFirstResult() {
        val body = """{"signatures":[{"asset":{"url":"https://example.test/unknown.png","format":"png"}},
            {"asset":{"url":"https://example.test/known.svg","format":"svg"},"license":{"status":"known","name":"Public Domain"},"source":{"url":"https://example.test/source"}}]}"""
        val image = ArtistImageSources.parseSignature(body)!!
        assertEquals("https://example.test/known.svg", image.url)
        assertEquals("Public Domain", image.license)
        assertEquals("https://example.test/source", image.source)
    }

    @Test fun ambiguousOrMissingSignatureDoesNotGuess() {
        assertNull(ArtistImageSources.parseSignature("""{"matches":[{"name":"Same"}]}"""))
        assertNull(ArtistImageSources.parseSignature("""{"signatures":[]}"""))
        assertNull(ArtistImageSources.parseSignature("""{"signatures":[{"url":"http://example.test/unsafe.png"}]}"""))
    }

    @Test fun legacySignatureShapeIsStillSupported() {
        assertEquals("https://example.test/a.png", ArtistImageSources.parseSignature("""{"signatures":[{"asset":"https://example.test/a.png"}]}""")!!.url)
    }

    @Test fun apiBaseMustBeHttpsWithoutCredentialsOrQuery() {
        assertEquals("https://example.test/api", ArtistImageSources.baseUrl(" https://example.test/api/ "))
        assertNull(ArtistImageSources.baseUrl("http://example.test"))
        assertNull(ArtistImageSources.baseUrl("https://user:password@example.test"))
        assertNull(ArtistImageSources.baseUrl("https://example.test?cookie=secret"))
        assertNull(ArtistImageSources.baseUrl("javascript:alert(1)"))
    }

    @Test fun signatureQueryIsEncodedAndUsesNoAccountData() {
        var asked = ""
        ArtistImageSources { url -> asked = url; """{"signatures":[]}""" }.signature("A & B", "https://example.test")
        assertEquals("https://example.test/v1/signatures?artist=A+%26+B", asked)
    }

    @Test fun appleLogoPreservesAspectAndNeedsRealArtwork() {
        assertEquals("https://example.test/1000x200.png", ArtistImageSources.parseAppleLogo(header, "https://music.apple.com/us/artist/a/2")!!.url)
        assertNull(ArtistImageSources.parseAppleLogo("""<script id="serialized-server-data">[{"artistLogo":null}]</script>""", "source"))
        assertNull(ArtistImageSources.parseAppleLogo("changed page format", "source"))
    }

    @Test fun sameArtistNameNeedsSharedCatalogueTitles() {
        val requests = ArrayList<String>()
        val source = ArtistImageSources { url ->
            requests += url
            when {
                "/search?" in url -> """{"results":[{"artistId":1,"artistName":"Same"},{"artistId":2,"artistName":"Same"}]}"""
                "id=1&" in url -> """{"results":[{"trackName":"Someone else"}]}"""
                "id=2&" in url -> """{"results":[{"trackName":"The right song"}]}"""
                url == "https://music.apple.com/us/artist/2" -> header
                else -> error("unexpected request: $url")
            }
        }
        assertNotNull(source.logo("Same", listOf("The right song")))
        assertFalse(requests.contains("https://music.apple.com/us/artist/1"))
        assertEquals(4, requests.size)
    }

    @Test fun noCatalogueEvidenceMeansNoAppleRequests() {
        var requests = 0
        assertNull(ArtistImageSources { requests++; error("must not request") }.logo("Artist", listOf("War")))
        assertEquals(0, requests)
    }
}
