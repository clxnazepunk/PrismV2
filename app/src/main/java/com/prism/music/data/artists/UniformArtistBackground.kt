package com.prism.music.data.artists

import android.graphics.Bitmap
import android.graphics.Color
import coil3.size.Size
import coil3.transform.Transformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** Local, bounded and reversible. It is a uniform-matte remover, not an AI segmentation model. */
class UniformArtistBackground(private val tolerance: Int) : Transformation() {
    override val cacheKey = "artist-uniform-background-v1:${tolerance.coerceIn(0, 100)}"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        remove(input, tolerance) { context.ensureActive() }
    }

    companion object {
        internal fun remove(input: Bitmap, tolerance: Int, checkCancelled: () -> Unit = {}): Bitmap {
            val longest = maxOf(input.width, input.height)
            val ratio = minOf(1f, 1024f / longest)
            val sampled = if (ratio < 1f) Bitmap.createScaledBitmap(input,
                maxOf(1, (input.width * ratio).toInt()), maxOf(1, (input.height * ratio).toInt()), true) else input
            try {
                val w = sampled.width
                val h = sampled.height
                val pixels = IntArray(w * h)
                sampled.getPixels(pixels, 0, w, 0, 0, w, h)
                val edge = ArrayList<Int>()
                val step = maxOf(1, maxOf(w, h) / 128)
                for (x in 0 until w step step) { edge += pixels[x]; edge += pixels[(h - 1) * w + x] }
                for (y in 0 until h step step) { edge += pixels[y * w]; edge += pixels[y * w + w - 1] }
                val opaque = edge.filter { Color.alpha(it) >= 240 }
                fun output() = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
                // Already transparent or no consistent border: leave it alone, never erase a guessed subject.
                if (opaque.size * 2 < edge.size) return output()
                fun bucket(c: Int) = (Color.red(c) / 32 shl 6) or (Color.green(c) / 32 shl 3) or (Color.blue(c) / 32)
                val grouped = opaque.groupBy(::bucket)
                val dominant = grouped.values.maxByOrNull { it.size } ?: return output()
                if (dominant.size * 2 < opaque.size) return output()
                val red = dominant.sumOf { Color.red(it) } / dominant.size
                val green = dominant.sumOf { Color.green(it) } / dominant.size
                val blue = dominant.sumOf { Color.blue(it) } / dominant.size
                val threshold = tolerance.coerceIn(0, 100)
                val seen = BooleanArray(w * h)
                val queue = IntArray(w * h)
                var head = 0
                var tail = 0
                fun enqueue(index: Int) {
                    if (!seen[index]) { seen[index] = true; queue[tail++] = index }
                }
                for (x in 0 until w) { enqueue(x); enqueue((h - 1) * w + x) }
                for (y in 0 until h) { enqueue(y * w); enqueue(y * w + w - 1) }
                while (head < tail) {
                    if (head and 16383 == 0) checkCancelled()
                    val index = queue[head++]
                    val c = pixels[index]
                    val difference = maxOf(abs(Color.red(c) - red), abs(Color.green(c) - green), abs(Color.blue(c) - blue))
                    if (Color.alpha(c) == 0 || difference <= threshold) {
                        pixels[index] = c and 0x00ffffff
                        val x = index % w
                        val y = index / w
                        if (x > 0) enqueue(index - 1)
                        if (x + 1 < w) enqueue(index + 1)
                        if (y > 0) enqueue(index - w)
                        if (y + 1 < h) enqueue(index + w)
                    } else if (difference < threshold + 12) {
                        // Soften only the border-connected fringe; enclosed light details stay intact.
                        val alpha = Color.alpha(c) * (difference - threshold) / 12
                        pixels[index] = (c and 0x00ffffff) or (alpha shl 24)
                    }
                }
                return output()
            } finally { if (sampled !== input) sampled.recycle() }
        }
    }
}
