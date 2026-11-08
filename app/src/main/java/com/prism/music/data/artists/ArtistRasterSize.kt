package com.prism.music.data.artists

import android.graphics.BitmapFactory
import coil3.size.Size
import coil3.size.SizeResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Avoid rasterizing a tiny original at the maximum size before a transformation. */
data class ArtistRasterSize(private val file: File, private val maximum: Int) : SizeResolver {
    override suspend fun size(): Size = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        if (file.extension != "svg") BitmapFactory.decodeFile(file.path, bounds)
        val original = maxOf(bounds.outWidth, bounds.outHeight).takeIf { it > 0 } ?: maximum
        val side = minOf(maximum, original).coerceAtLeast(1)
        Size(side, side)
    }
}
