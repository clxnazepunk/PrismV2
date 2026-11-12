package com.prism.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.compose.runtime.mutableStateMapOf
import com.prism.music.data.artists.ArtistPhotoFrame
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.PlaylistItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** #305 (1.8.1): the song's picture as Prism shows it: the cover picked for it or its album, else its own. */
val com.prism.music.data.model.Song.art: String? get() = PlaylistCovers.artOf(this)

/**
 * Pictures the listener picked for their playlists (and for Liked songs and Downloads),
 * kept on this phone by playlist id: either a photo copied into the app or a song's cover.
 */
class PlaylistCovers(private val context: Context) {
    private val prefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
    /** Compose state, so every cover on screen updates the moment one is changed. */
    private val covers = mutableStateMapOf<String, String>().apply {
        prefs.all.forEach { (k, v) ->
            if (!k.startsWith(ARTIST_FRAME_PREFIX) && v is String) put(k, restoredCover(v))
        }
    }
    private val artistFrames = mutableStateMapOf<String, ArtistPhotoFrame>().apply {
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(ARTIST_FRAME_PREFIX) && value is String) {
                runCatching { Json.decodeFromString<ArtistPhotoFrame>(value).normalized() }.getOrNull()?.let {
                    put(key.removePrefix(ARTIST_FRAME_PREFIX), it)
                }
            }
        }
    }
    val dir: File get() = File(context.filesDir, "covers")

    /** Old backups held absolute private paths. Rebind only owned cover files, never arbitrary external images. */
    private fun restoredCover(value: String): String = runCatching {
        val uri = value.toUri()
        if (uri.scheme != "file") return@runCatching value
        val old = File(uri.path ?: return@runCatching value)
        if (old.parentFile?.name != "covers" || old.parentFile?.parentFile?.name != "files") return@runCatching value
        val restored = File(dir, old.name)
        if (restored.isFile && restored.canonicalFile.parentFile == dir.canonicalFile) restored.toUri().toString() else value
    }.getOrDefault(value)

    fun custom(id: String): String? = covers[id]

    /** The picture to show for playlist [id]: the listener's own if they picked one, else [fallback]. */
    fun art(id: String, fallback: String?): String? = covers[id] ?: fallback

    /**
     * Any item's picture, with a playlist's custom cover (or an artist's own photo, 1.7.6, or a song's or an album's,
     * #305) in place of YouTube's.
     */
    fun art(item: BrowseItem): String? = when (item) {
        is PlaylistItem -> art(item.id, item.thumbnail)
        is com.prism.music.data.model.ArtistItem -> covers["artist:${item.id}"] ?: item.thumbnail
        is com.prism.music.data.model.SongItem -> art(item.song)
        is com.prism.music.data.model.AlbumItem -> albumArt(item.id, item.thumbnail)
        else -> item.thumbnail
    }

    /** A song's own cover (#305, 1.8.1), kept under the song's id. */
    fun songKey(id: String) = "song:$id"

    /** An album's own cover (#305), kept under the album's id; its songs show it too. */
    fun albumKey(id: String) = "album:$id"

    /**
     * #305: the picture to show for [song] wherever it shows: the one picked for it, else the one picked for its album,
     * else the cover of the version picked to play in its place (#328), else its own. Read in a composable, it follows a
     * change at once.
     */
    fun art(song: com.prism.music.data.model.Song): String? = picked(song) ?: SongVersions.shared?.coverOf(song.id) ?: song.thumbnail

    /** The cover picked for [song] (or its album), if any. */
    fun picked(song: com.prism.music.data.model.Song): String? =
        // With no covers picked (most phones), no keys are made for each row drawn (the empty check is read too, so a
        // first pick still shows at once).
        if (covers.isEmpty()) null
        else covers[songKey(song.id)] ?: song.album?.id?.let { covers[albumKey(it)] }

    /** An album's picture: the one picked for it (#305), else [fallback]. */
    fun albumArt(id: String, fallback: String?): String? = covers[albumKey(id)] ?: fallback

    init { shared = this }

    companion object {
        private const val ARTIST_FRAME_PREFIX = "artist_frame:"
        /** The phone's covers, for what draws a song without the app's container at hand (the media session, a row). */
        @Volatile var shared: PlaylistCovers? = null
            internal set

        /** [art] for [song], or its own picture before the covers have been read. */
        fun artOf(song: com.prism.music.data.model.Song): String? = shared?.art(song) ?: song.thumbnail
    }

    private fun put(id: String, value: String, frame: ArtistPhotoFrame? = null) {
        val old = covers[id]
        covers[id] = value
        val edit = prefs.edit().putString(id, value)
        if (id.startsWith("artist:")) writeArtistFrame(id, frame, edit)
        edit.apply()
        old?.let(::deleteIfOurs)
    }

    /** One of the playlist's own songs' covers (a web address). */
    fun useImage(id: String, url: String) = put(id, url)

    /** Copies a picked photo into the app as a square, so it survives the original being moved or deleted. */
    suspend fun usePhoto(id: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val bmp = runCatching { decodeCover(context, uri) }.getOrNull() ?: return@withContext false
        var square: Bitmap? = null
        var out: Bitmap? = null
        var file: File? = null
        var committed = false
        try {
            val side = minOf(bmp.width, bmp.height)
            val cropped = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
            square = cropped
            val result = if (side > 1200) Bitmap.createScaledBitmap(cropped, 1200, 1200, true) else cropped
            out = result
            dir.mkdirs()
            val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val target = File(dir, "$safe-${java.util.UUID.randomUUID()}.jpg")
            file = target
            val compressed = target.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            if (!compressed) return@withContext false
            withContext(Dispatchers.Main) {
                put(id, Uri.fromFile(target).toString())
                committed = true
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
        finally {
            if (!committed) file?.delete()
            if (out !== square && out !== bmp) out?.recycle()
            if (square !== bmp) square?.recycle()
            bmp.recycle()
        }
    }

    /** A picture made in Prism (the cover maker), saved as a photo would be. */
    suspend fun useBitmap(id: String, bmp: Bitmap): Boolean = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val safe = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "$safe-${System.currentTimeMillis()}.jpg")
        runCatching { file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) } }.getOrElse { return@withContext false }
        withContext(Dispatchers.Main) { put(id, Uri.fromFile(file).toString()) }
        true
    }

    /** An artist's own photo (1.7.6), kept under the artist's id. */
    fun artistKey(id: String) = "artist:$id"

    fun artistFrame(id: String): ArtistPhotoFrame? = artistFrames[artistKey(id)]

    fun adjustArtistPhoto(id: String, expectedPhoto: String, frame: ArtistPhotoFrame): Boolean {
        val key = artistKey(id)
        if (covers[key] != expectedPhoto) return false
        val edit = prefs.edit()
        writeArtistFrame(key, frame, edit)
        edit.apply()
        return true
    }

    private fun writeArtistFrame(key: String, frame: ArtistPhotoFrame?, edit: android.content.SharedPreferences.Editor) {
        val preference = ARTIST_FRAME_PREFIX + key
        if (frame == null) {
            artistFrames.remove(key)
            edit.remove(preference)
        } else {
            val safe = frame.normalized()
            artistFrames[key] = safe
            edit.putString(preference, Json.encodeToString(ArtistPhotoFrame.serializer(), safe))
        }
    }

    /** Keep the full aspect ratio. The profile frame is saved alongside the existing library cover. */
    suspend fun useArtistPhoto(id: String, uri: Uri, frame: ArtistPhotoFrame): Boolean = withContext(Dispatchers.IO) {
        var target: File? = null
        var committed = false
        try {
            val bytes = artistPhotoBytes(context, uri) ?: return@withContext false
            dir.mkdirs()
            val file = File(dir, "artist-${UUID.randomUUID()}.jpg")
            target = file
            file.writeBytes(bytes)
            withContext(Dispatchers.Main) {
                put(artistKey(id), Uri.fromFile(file).toString(), frame)
                committed = true
            }
            true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { false }
        finally { if (!committed) target?.delete() }
    }

    fun reset(id: String) {
        val old = covers.remove(id) ?: return
        val edit = prefs.edit().remove(id)
        if (id.startsWith("artist:")) writeArtistFrame(id, null, edit)
        edit.apply()
        deleteIfOurs(old)
    }

    private fun deleteIfOurs(value: String) {
        if (!value.startsWith("file:")) return
        val f = File(Uri.parse(value).path ?: return)
        if (f.canonicalFile.parentFile == dir.canonicalFile) f.delete()
    }

}

internal fun artistPhotoBytes(context: Context, uri: Uri): ByteArray? {
    val bitmap = decodeCover(context, uri, honorExif = true) ?: return null
    return try {
        ByteArrayOutputStream().use { output ->
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)) output.toByteArray() else null
        }
    } finally { bitmap.recycle() }
}

/** Decoded at a sensible size, the right way up (phone photos often carry a rotation). */
private fun decodeCover(context: Context, uri: Uri, honorExif: Boolean = false): Bitmap? {
    if (Build.VERSION.SDK_INT >= 28) {
        val decoded = runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 2400) d.setTargetSampleSize(longest / 2400 + 1)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        } }.getOrNull()
        if (decoded != null) return decoded
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1200) sample *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    if (!honorExif) return bitmap
    val orientation = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull()
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        else -> return bitmap
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
        if (it !== bitmap) bitmap.recycle()
    }
}
