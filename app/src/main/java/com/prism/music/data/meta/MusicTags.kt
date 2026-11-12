package com.prism.music.data.meta

import com.prism.music.R
import com.prism.music.tr
import java.text.Normalizer

/** Detail is kept beside legacy genres so radio/recommendation categories keep their existing meaning. */
object MusicTags {
    val detailedGenres = listOf("Salsa", "Cumbia", "Regional Mexican", "Ballad", "Reggaeton", "Latin")
    val genresAll: List<String> get() = Genres.all + detailedGenres
    val moods = listOf("mood:romantic", "mood:sad", "mood:happy", "mood:calm", "mood:energetic", "mood:party")
    private fun clean(raw: String) = Normalizer.normalize(raw.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
    private val aliases = linkedMapOf(
        "Salsa" to setOf("salsa"),
        "Cumbia" to setOf("cumbia", "cumbias"),
        "Regional Mexican" to setOf("regional mexican", "regional mexicano", "traditional mexicano", "musica mexicana", "banda", "banda nortena", "norteno", "nortena", "ranchera", "rancheras", "corrido", "corridos", "mariachi"),
        "Ballad" to setOf("ballad", "ballads", "balada", "baladas"),
        "Reggaeton" to setOf("reggaeton", "regueton"),
        "Latin" to setOf("latin", "latin music", "latino", "latina", "musica latina"),
        "mood:romantic" to setOf("romantic", "romantico", "romantica", "romanticas", "love songs"),
        "mood:sad" to setOf("sad", "sad songs", "triste", "tristes", "melancholic", "melancolico", "melancholy"),
        "mood:happy" to setOf("happy", "feliz", "alegre"),
        "mood:calm" to setOf("calm", "relaxing", "relax", "chill", "tranquilo", "tranquila", "peaceful", "soothing", "mellow"),
        "mood:energetic" to setOf("energetic", "energico", "energia", "high energy"),
        "mood:party" to setOf("party", "fiesta"),
    )

    fun explicit(raw: String, includeMoods: Boolean = true): Set<String> {
        val text = clean(raw)
        return aliases.filter { (id, keys) ->
            (includeMoods || id !in moods) && keys.any { key ->
                // Match whole phrases, never "sad" inside an artist/name/compound.
                text == key || (id !in moods && " $text ".contains(" $key "))
            }
        }.keys
    }

    fun genres(raw: String): Set<String> {
        return raw.split("|", " / ").flatMap { part ->
            explicit(part, includeMoods = false).ifEmpty { setOfNotNull(Genres.normalize(part)) }
        }.toSet()
    }

    fun displayGenre(tags: Set<String>, legacy: String?): String? =
        tags.firstOrNull { it in detailedGenres } ?: legacy ?: tags.firstOrNull { it in Genres.all }

    fun shown(id: String): String = when (id) {
        "Salsa" -> R.string.detail_salsa
        "Cumbia" -> R.string.detail_cumbia
        "Regional Mexican" -> R.string.detail_regional_mexican
        "Ballad" -> R.string.detail_ballad
        "Reggaeton" -> R.string.detail_reggaeton
        "Latin" -> R.string.detail_latin
        "mood:romantic" -> R.string.mood_romantic
        "mood:sad" -> R.string.mood_sad
        "mood:happy" -> R.string.mood_happy
        "mood:calm" -> R.string.calm
        "mood:energetic" -> R.string.tune_energetic
        "mood:party" -> R.string.mood_party
        else -> null
    }?.let { tr(it) } ?: Genres.shown(id)

    fun languageTag(raw: String): String? = when (clean(raw)) {
        "spanish language", "in spanish", "sung in spanish", "en espanol" -> "lang:es"
        "english language", "in english", "sung in english", "en ingles" -> "lang:en"
        "portuguese language", "sung in portuguese", "em portugues" -> "lang:pt"
        "french language", "sung in french", "en francais" -> "lang:fr"
        "japanese language", "sung in japanese" -> "lang:ja"
        "korean language", "sung in korean" -> "lang:ko"
        else -> null
    }

    fun communityMoods(tags: Map<String, Int>): Set<String> =
        tags.filterValues { it > 0 }.keys.flatMap { explicit(it).filter(moods::contains) }.toSet()

    /** Low-weight community labels are not enough evidence. Artist moods never propagate to songs. */
    fun supportedTags(tags: Map<String, Int>): Set<String> {
        val top = tags.values.maxOrNull() ?: return emptySet()
        if (top <= 0) return emptySet()
        return tags.filter { (raw, count) ->
            count >= 10 && count * 2L >= top.toLong() &&
                !Regex("vocalists|seen live|favo[u]?rites", RegexOption.IGNORE_CASE).containsMatchIn(raw)
        }.keys.flatMap { raw ->
            explicit(raw, includeMoods = false).ifEmpty { setOfNotNull(Genres.normalize(raw)) }
        }.toSet() + communityMoods(tags.filterValues { it >= 5 && it * 8L >= top }) +
            tags.filterValues { it >= 5 }.keys.mapNotNull(::languageTag)
    }
}
