package com.prism.music.data.artists

import kotlinx.serialization.Serializable

/** Null display choice means logo, then signature, then native text. */
@Serializable
data class ArtistDisplayChoice(val logo: Boolean = true, val signature: Boolean = false, val text: Boolean = false) {
    fun resolve(hasLogo: Boolean, hasSignature: Boolean): ArtistDisplayChoice {
        val l = logo && hasLogo
        val s = signature && hasSignature
        return ArtistDisplayChoice(l, s, text || (!l && !s))
    }
    companion object {
        fun automatic(hasLogo: Boolean, hasSignature: Boolean) =
            ArtistDisplayChoice(hasLogo, !hasLogo && hasSignature, !hasLogo && !hasSignature)
    }
}

/** Independent of source pixel dimensions: even a tiny imported image can fill the header. */
@Serializable
data class ArtistImageLayout(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun bounded() = ArtistImageLayout(
        scale.takeIf { it.isFinite() }?.coerceIn(0.25f, 12f) ?: 1f,
        x.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
        y.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
    )
}
