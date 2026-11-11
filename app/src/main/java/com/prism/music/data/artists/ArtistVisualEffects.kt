package com.prism.music.data.artists

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** A single cached Coil transform, independent of header scale and never written over the source file. */
internal class ArtistVisualEffects(layout: ArtistImageLayout) : Transformation() {
    private val fit = layout.bounded()
    override val cacheKey = "artist-visual-v1:${fit.removeBackground}:${fit.backgroundTolerance}:${fit.brightness}:${fit.glow}:${fit.shadow}:${fit.grayscale}:${fit.inverted}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        val source = if (fit.removeBackground) UniformArtistBackground.remove(input, fit.backgroundTolerance) { context.ensureActive() } else input
        try {
            context.ensureActive()
            render(source, fit)
        } finally { if (source !== input) source.recycle() }
    }

    companion object {
        internal fun render(source: Bitmap, fit: ArtistImageLayout): Bitmap {
            val edge = minOf(source.width, source.height).coerceAtLeast(1)
            val radius = (edge * 0.04f).coerceIn(1f, 18f)
            val pad = if (fit.glow > 0 || fit.shadow > 0) (radius * 4).toInt() else 0
            val output = Bitmap.createBitmap(source.width + 2 * pad, source.height + 2 * pad, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            fun halo(intensity: Int, color: Int, dy: Float) {
                if (intensity <= 0) return
                val offsets = IntArray(2)
                val mask = source.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL) }, offsets)
                try {
                    canvas.drawBitmap(mask, pad + offsets[0].toFloat(), pad + offsets[1] + dy,
                        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = intensity * 255 / 100 })
                } finally { mask.recycle() }
            }
            halo(fit.shadow, Color.BLACK, radius)
            halo(fit.glow, Color.WHITE, 0f)
            val colors = ColorMatrix().apply { if (fit.grayscale) setSaturation(0f) }
            val scale = if (fit.inverted) -1f else 1f
            val shift = (if (fit.inverted) 255f else 0f) + fit.brightness * 1.8f
            colors.postConcat(ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, shift,
                0f, scale, 0f, 0f, shift,
                0f, 0f, scale, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            )))
            canvas.drawBitmap(source, pad.toFloat(), pad.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(colors)
            })
            return output
        }
    }
}
