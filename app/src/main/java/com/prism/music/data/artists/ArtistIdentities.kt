package com.prism.music.data.artists

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.prism.music.data.model.AlbumItem
import com.prism.music.data.model.ArtistPage
import com.prism.music.data.model.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** A separate unauthenticated client: YouTube cookies never reach an artwork provider. */
class ArtistIdentities(private val context: Context) {
    val store = ArtistIdentityStore(File(context.filesDir, "artist_identity"))
    val entries = store.entries
    private val prefs = context.getSharedPreferences("artist_resource_settings", Context.MODE_PRIVATE)
    val automatic = MutableStateFlow(prefs.getBoolean("automatic", true))
    val signatureApi = MutableStateFlow(prefs.getString("signature_api", DEFAULT_API) ?: DEFAULT_API)
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
        .followSslRedirects(false).build()
    private val source = ArtistImageSources { get(it, MAX_METADATA_BYTES)?.toString(Charsets.UTF_8) }
    private val lookups = ConcurrentHashMap<String, Mutex>()

    fun setAutomatic(on: Boolean) { prefs.edit { putBoolean("automatic", on) }; automatic.value = on }

    fun setSignatureApi(raw: String): Boolean {
        val base = if (raw.isBlank()) "" else ArtistImageSources.baseUrl(raw) ?: return false
        prefs.edit { putString("signature_api", base) }
        signatureApi.value = base
        return true
    }

    /** Local choices always win. Disabling automatic resources leaves local images and fonts intact. */
    fun asset(id: String, role: ArtistImageRole): ArtistImageAsset? {
        val entry = entries.value[id] ?: return null
        val selected = entry.chosen(role, automatic.value && (role != ArtistImageRole.SIGNATURE || signatureApi.value.isNotBlank()))
        return selected?.takeIf { store.file(it) != null }
    }

    suspend fun import(id: String, role: ArtistImageRole, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { bounded(it, ArtistIdentityStore.MAX_IMAGE_BYTES) } ?: return@withContext false
            store.install(id, role, bytes, local = true)
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }

    suspend fun refresh(page: ArtistPage, force: Boolean = false) = withContext(Dispatchers.IO) {
        // Private uploads' names/catalogues are not submitted to any public provider.
        if (!automatic.value || com.prism.music.data.innertube.Parser.isUploadArtist(page.id)) return@withContext
        lookups.getOrPut(page.id) { Mutex() }.withLock {
            val titles = page.shelves.flatMap { it.items }.mapNotNull {
                when (it) { is SongItem -> it.song.title; is AlbumItem -> it.title; else -> null }
            }
            val catalogue = titles.mapNotNull(ArtistImageSources::titleKey).sorted().hashCode()
            val base = signatureApi.value
            val old = entries.value[page.id]
            val now = System.currentTimeMillis()
            if (!force && old != null && old.api == base && old.catalogue == catalogue && now - old.checkedAt < (if (old.logo == null && old.signature == null) 6 * 60 * 60 * 1000L else FRESH_MS)) return@withLock
            // A full local customization needs no external requests.
            if (old?.localLogo != null && old.localSignature != null) return@withLock
            coroutineScope {
                val logo = async { safely { if (old?.localLogo == null) source.logo(page.name, titles)?.let { save(page.id, ArtistImageRole.LOGO, it) } } }
                val signature = async {
                    if (old?.localSignature == null) safely {
                        for (candidate in source.signatures(page.name, base).take(3)) {
                            try {
                                if (save(page.id, ArtistImageRole.SIGNATURE, candidate)) break
                            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* try the next supported image */ }
                        }
                    }
                }
                logo.await(); signature.await()
            }
            // Provider failures never replace a known image with null or overwrite a local choice.
            store.update(page.id) { it.copy(checkedAt = now, api = base, catalogue = catalogue) }
        }
    }

    private suspend fun save(id: String, role: ArtistImageRole, remote: RemoteArtistImage): Boolean {
        val bytes = get(remote.url, ArtistIdentityStore.MAX_IMAGE_BYTES) ?: return false
        store.install(id, role, bytes, local = false, source = remote.source, license = remote.license)
        return true
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* keep the old image */ }
    }

    private fun get(url: String, max: Int): ByteArray? {
        if (!url.startsWith("https://")) return null
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body.byteStream().use { bounded(it, max) }
        }
    }

    companion object {
        const val DEFAULT_API = "https://artistsignature.velixir.run"
        private const val FRESH_MS = 7L * 24 * 60 * 60 * 1000
        private const val MAX_METADATA_BYTES = 8 * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"

        internal fun bounded(input: InputStream, max: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(out.size() + n <= max) { "Artist resource too large" }
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
    }
}
