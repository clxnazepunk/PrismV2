package com.prism.music.data.artists

import kotlinx.serialization.Serializable

/** Positions are fractions of the available overflow, so a frame survives a viewport size change. */
@Serializable
data class ArtistPhotoFrame(
    val fill: Boolean = true,
    val zoom: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
) {
    fun scale(width: Float, height: Float, imageWidth: Float, imageHeight: Float): Float {
        val horizontal = width / imageWidth
        val vertical = height / imageHeight
        return (if (fill) maxOf(horizontal, vertical) else minOf(horizontal, vertical)) * zoom
    }

    fun offset(scaledSize: Float, viewport: Float, position: Float): Float =
        (viewport - scaledSize) / 2f + overflow(scaledSize, viewport) * position

    fun gesture(
        width: Float, height: Float, aspect: Float,
        panX: Float, panY: Float, zoomFactor: Float, focusX: Float, focusY: Float,
    ): ArtistPhotoFrame {
        val newZoom = (zoom * zoomFactor).coerceIn(1f, MAX_ZOOM)
        val ratio = newZoom / zoom
        val oldScale = scale(width, height, aspect, 1f)
        fun move(viewport: Float, scaled: Float, position: Float, pan: Float, focus: Float): Float {
            val room = overflow(scaled * ratio, viewport)
            if (room <= 0f) return 0f
            val centerOffset = overflow(scaled, viewport) * position
            val fromCenter = focus - viewport / 2f
            return ((centerOffset * ratio + fromCenter * (1f - ratio) + pan) / room).coerceIn(-1f, 1f)
        }
        return copy(zoom = newZoom, x = move(width, oldScale * aspect, x, panX, focusX),
            y = move(height, oldScale, y, panY, focusY))
    }

    fun normalized() = copy(
        zoom = zoom.takeIf(Float::isFinite)?.coerceIn(1f, MAX_ZOOM) ?: 1f,
        x = x.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
        y = y.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
    )

    private fun overflow(scaled: Float, viewport: Float) = (scaled - viewport).coerceAtLeast(0f) / 2f

    companion object {
        const val MAX_ZOOM = 5f
    }
}
