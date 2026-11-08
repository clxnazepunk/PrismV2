package com.prism.music

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ArtistCustomizationLanguageTest {
    private val names = listOf(
        "artist_adjust", "artist_zoom", "artist_horizontal", "artist_vertical", "artist_remove_background",
        "artist_background_tolerance", "artist_background_hint", "artist_blend_mode", "artist_blend_normal",
        "artist_blend_multiply", "artist_blend_screen", "artist_blend_darken", "artist_blend_lighten",
        "artist_export_original", "artist_export_png", "artist_signature_caption", "artist_display_auto",
        "artist_display_priority", "artist_native_text", "artist_invert", "artist_font_settings",
        "artist_font_fixed", "artist_font_random", "artist_font_original", "artist_font_fallback",
        "artist_font_gradient", "detail_salsa", "detail_cumbia", "detail_regional_mexican", "detail_ballad",
        "detail_reggaeton", "detail_latin", "mood_romantic", "mood_sad", "mood_happy", "mood_party",
        "music_moods", "music_tag_api", "music_tag_hint",
    )

    private fun translations(folder: String): Map<String, String> {
        val document = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/$folder/strings.xml"))
        val entries = document.documentElement.getElementsByTagName("string")
        return (0 until entries.length).map { entries.item(it) as org.w3c.dom.Element }
            .associate { it.getAttribute("name") to Strings.compile(it.textContent) }
    }

    @Test fun allNewLabelsSwitchSpanishEnglishAndBackWithoutRememberingOldWords() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        try {
            for (tag in listOf("es", "en", "es")) {
                val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
                Strings.resources = app.createConfigurationContext(configuration).resources
                val words = translations(if (tag == "es") "values-es" else "values")
                for (name in names) {
                    val id = R.string::class.java.getField(name).getInt(null)
                    assertEquals("$tag/$name", words.getValue(name), tr(id))
                }
            }
        } finally { Strings.resources = null }
    }

    @Test fun artistNamesAndStoredGenreIdsStayIndependentOfTheInterfaceLanguage() {
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val configuration = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag("es")) }
            Strings.resources = app.createConfigurationContext(configuration).resources
            assertEquals("Romántico", com.prism.music.data.meta.MusicTags.shown("mood:romantic"))
            assertEquals("Regional mexicana", com.prism.music.data.meta.MusicTags.shown("Regional Mexican"))
            assertTrue("Salsa" in com.prism.music.data.meta.MusicTags.genres("Salsa"))
            assertTrue(com.prism.music.data.artists.ArtistImageExports.name("Artist Name",
                com.prism.music.data.artists.ArtistImageRole.LOGO, "resource.svg", false).startsWith("Artist Name"))
        } finally { Strings.resources = null }
    }
}
