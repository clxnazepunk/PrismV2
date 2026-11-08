package com.prism.music.data.artists

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ArtistImageEdits {
    /** Change RGB, preserving alpha; a new owned PNG persists the result, not a theme tint. */
    internal fun inverted(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                -1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f,
                0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f,
            )))
        }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        return result
    }

    suspend fun invert(context: Context, store: ArtistIdentityStore, id: String, role: ArtistImageRole, asset: ArtistImageAsset) =
        withContext(Dispatchers.IO) {
            val file = requireNotNull(store.file(asset))
            // Reuse the existing Coil raster/SVG decoders, bounded independently of display zoom.
            val request = ImageRequest.Builder(context).data(file).size(2048, 2048).allowHardware(false).build()
            val source = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                ?: throw IllegalArgumentException("Cannot decode artist image")
            var bitmap = inverted(source)
            try {
                var bytes: ByteArray
                while (true) {
                    bytes = ByteArrayOutputStream().use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); out.toByteArray()
                    }
                    if (bytes.size <= ArtistIdentityStore.MAX_IMAGE_BYTES) break
                    require(maxOf(bitmap.width, bitmap.height) > 256)
                    val smaller = Bitmap.createScaledBitmap(bitmap, maxOf(1, bitmap.width / 2), maxOf(1, bitmap.height / 2), true)
                    bitmap.recycle()
                    bitmap = smaller
                }
                store.install(id, role, bytes, true, asset.source, asset.license)
            } finally { bitmap.recycle() }
        }
}
