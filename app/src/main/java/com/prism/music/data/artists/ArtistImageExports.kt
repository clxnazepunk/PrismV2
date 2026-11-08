package com.prism.music.data.artists

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

object ArtistImageExports {
    fun mime(file: String): String = when (file.substringAfterLast('.').lowercase()) {
        "svg" -> "image/svg+xml"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/png"
    }

    fun name(artist: String, role: ArtistImageRole, asset: String, rendered: Boolean): String {
        val safe = artist.replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80).trim().ifBlank { "artist" }
        val extension = if (rendered) "png" else asset.substringAfterLast('.').lowercase()
        return "$safe-${role.name.lowercase()}.$extension"
    }

    /** Write only to the destination explicitly chosen with Android's document picker. */
    suspend fun write(
        context: Context, store: ArtistIdentityStore, asset: ArtistImageAsset,
        layout: ArtistImageLayout, destination: Uri, rendered: Boolean,
    ) = withContext(Dispatchers.IO) {
        val source = requireNotNull(store.file(asset))
        if (!rendered) {
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                source.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Cannot open destination")
        } else {
            val fit = layout.bounded()
            val request = ImageRequest.Builder(context).data(source).size(2048, 2048).allowHardware(false)
                .apply { if (fit.removeBackground) transformations(UniformArtistBackground(fit.backgroundTolerance)) }.build()
            val bitmap = (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
                ?: error("Cannot decode artist image")
            coroutineContext.ensureActive()
            context.contentResolver.openOutputStream(destination, "w")?.use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            } ?: error("Cannot open destination")
            // Coil may own/cache this bitmap. Never recycle or mutate it.
        }
    }
}
