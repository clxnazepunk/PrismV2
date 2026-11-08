package com.prism.music.data

import com.prism.music.data.meta.Genres
import kotlinx.serialization.Serializable

@Serializable
enum class ArtistFontMode { FIXED, RANDOM, SMART, PRISM }

@Serializable
data class ArtistFontGroup(val id: String, val genres: Set<String>, val font: String)

@Serializable
data class ArtistTypographyConfig(
    val mode: ArtistFontMode = ArtistFontMode.FIXED,
    val fallback: String = "standard",
    val groups: List<ArtistFontGroup> = emptyList(),
) {
    /** Enforce uniqueness in storage too: editing UI is not the only entry (backups). First group wins. */
    fun sanitized(): ArtistTypographyConfig {
        val claimed = mutableSetOf<String>()
        val ids = mutableSetOf<String>()
        return copy(groups = groups.filter { it.id.isNotBlank() && ids.add(it.id) }.mapNotNull { group ->
            val allowed = group.genres.filterTo(linkedSetOf()) { it in Genres.all && claimed.add(it) }
            group.copy(genres = allowed).takeIf { allowed.isNotEmpty() }
        })
    }

    /** Null delegates to Prism's preserved original algorithm. Never guesses a missing genre. */
    fun fontKey(genre: String?, randomIndex: Int, available: List<String>): String? {
        val base = fallback.takeIf { it in available } ?: "standard"
        return when (mode) {
            ArtistFontMode.FIXED -> base
            ArtistFontMode.RANDOM -> if (available.isEmpty()) base else available[Math.floorMod(randomIndex, available.size)]
            ArtistFontMode.SMART -> groups.firstOrNull { genre != null && genre in it.genres }?.font?.takeIf { it in available } ?: base
            ArtistFontMode.PRISM -> null
        }
    }
}
