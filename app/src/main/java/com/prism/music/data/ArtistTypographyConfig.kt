package com.prism.music.data

import kotlinx.serialization.Serializable

/** SMART is retained only to read old backups; it always migrates to FIXED and is never offered. */
@Serializable
enum class ArtistFontMode { FIXED, RANDOM, PRISM, SMART }

@Serializable
data class ArtistTypographyConfig(
    val mode: ArtistFontMode = ArtistFontMode.FIXED,
    val fallback: String = "standard",
) {
    fun sanitized(): ArtistTypographyConfig = if (mode == ArtistFontMode.SMART) copy(mode = ArtistFontMode.FIXED) else this

    fun fontKey(genre: String?, randomIndex: Int, available: List<String>): String? {
        val base = fallback.takeIf { it in available } ?: "standard"
        return when (mode) {
            ArtistFontMode.FIXED, ArtistFontMode.SMART -> base
            ArtistFontMode.RANDOM -> if (available.isEmpty()) base else available[Math.floorMod(randomIndex, available.size)]
            ArtistFontMode.PRISM -> null
        }
    }
}
