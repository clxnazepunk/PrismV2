package com.prism.music.data.artists

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

enum class ArtistImageRole { LOGO, SIGNATURE }

@Serializable
data class ArtistImageAsset(val file: String, val aspect: Float, val source: String? = null, val license: String? = null)

@Serializable
data class ArtistIdentity(
    val logo: ArtistImageAsset? = null,
    val signature: ArtistImageAsset? = null,
    val localLogo: ArtistImageAsset? = null,
    val localSignature: ArtistImageAsset? = null,
    val checkedAt: Long = 0,
    val api: String = "",
    val catalogue: Int = 0,
    val display: ArtistDisplayChoice? = null,
    val logoLayout: ArtistImageLayout = ArtistImageLayout(),
    val signatureLayout: ArtistImageLayout = ArtistImageLayout(),
    val signatureCaption: Boolean = true,
    val logos: List<ArtistImageAsset> = emptyList(),
    val signatures: List<ArtistImageAsset> = emptyList(),
    val assetLayouts: Map<String, ArtistImageLayout> = emptyMap(),
) {
    fun local(role: ArtistImageRole) = if (role == ArtistImageRole.LOGO) localLogo else localSignature

    fun assets(role: ArtistImageRole): List<ArtistImageAsset> =
        ((if (role == ArtistImageRole.LOGO) logos else signatures) + listOfNotNull(local(role))).distinctBy { it.file }

    fun layout(role: ArtistImageRole, asset: ArtistImageAsset? = chosen(role)): ArtistImageLayout {
        val legacy = if (role == ArtistImageRole.LOGO) logoLayout else signatureLayout
        return asset?.let { assetLayouts[it.file] } ?: if (assets(role).size <= 1) legacy else ArtistImageLayout()
    }

    fun withLayout(role: ArtistImageRole, asset: ArtistImageAsset, layout: ArtistImageLayout): ArtistIdentity =
        if (assets(role).size <= 1 && chosen(role)?.file == asset.file) {
            val next = if (role == ArtistImageRole.LOGO) copy(logoLayout = layout) else copy(signatureLayout = layout)
            next.copy(assetLayouts = next.assetLayouts + (asset.file to layout))
        } else copy(assetLayouts = assetLayouts + (asset.file to layout))

    fun withAssets(role: ArtistImageRole, ordered: List<ArtistImageAsset>, selected: ArtistImageAsset?): ArtistIdentity =
        if (role == ArtistImageRole.LOGO) copy(logos = ordered, localLogo = selected)
        else copy(signatures = ordered, localSignature = selected)

    fun select(role: ArtistImageRole, file: String): ArtistIdentity {
        val ordered = assets(role)
        val selected = ordered.firstOrNull { it.file == file } ?: return this
        return withAssets(role, ordered, selected)
    }

    fun cycle(role: ArtistImageRole): ArtistIdentity {
        val ordered = assets(role)
        if (ordered.size < 2) return this
        val at = ordered.indexOfFirst { it.file == local(role)?.file }
        return withAssets(role, ordered, ordered[(at + 1) % ordered.size])
    }

    fun reorder(role: ArtistImageRole, file: String, newIndex: Int): ArtistIdentity {
        val ordered = assets(role).toMutableList()
        val at = ordered.indexOfFirst { it.file == file }
        if (at < 0 || newIndex !in ordered.indices) return this
        val asset = ordered.removeAt(at)
        ordered.add(newIndex, asset)
        return withAssets(role, ordered, local(role))
    }

    fun chosen(role: ArtistImageRole, automatic: Boolean = true): ArtistImageAsset? = when (role) {
        ArtistImageRole.LOGO -> localLogo ?: logo.takeIf { automatic }
        ArtistImageRole.SIGNATURE -> localSignature ?: signature.takeIf { automatic }
    }
}

/** Assets and relative filenames travel together in Library backups; no old phone paths are stored. */
class ArtistIdentityStore(val dir: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), ArtistIdentity.serializer())
    private val index = File(dir, "index.json")
    private val lock = Mutex()
    private val state = MutableStateFlow(runCatching { json.decodeFromString(serializer, index.readText()) }.getOrDefault(emptyMap()))
    val entries: StateFlow<Map<String, ArtistIdentity>> = state

    fun file(asset: ArtistImageAsset?): File? {
        if (asset == null || !ASSET_NAME.matches(asset.file)) return null
        return File(dir, asset.file).takeIf { it.isFile && it.canonicalFile.parentFile == dir.canonicalFile }
    }

    suspend fun update(id: String, change: (ArtistIdentity) -> ArtistIdentity) = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.mkdirs()
            val next = state.value + (id to change(state.value[id] ?: ArtistIdentity()))
            val tmp = File(dir, "index.json.tmp")
            try {
                tmp.writeText(json.encodeToString(serializer, next))
                Files.move(tmp.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING)
                state.value = next
            } finally { tmp.delete() }
        }
    }

    /** Validate before replacing anything. Import failure leaves every old choice in place. */
    suspend fun install(
        id: String, role: ArtistImageRole, bytes: ByteArray, local: Boolean,
        source: String? = null, license: String? = null,
    ): ArtistImageAsset = withContext(Dispatchers.IO) {
        val info = imageInfo(bytes)
        dir.mkdirs()
        val target = File(dir, "${if (local) "local" else "remote"}-${UUID.randomUUID()}.${info.first}")
        var committed = false
        try {
            target.writeBytes(bytes)
            val asset = ArtistImageAsset(target.name, info.second, source, license)
            var previous: ArtistImageAsset? = null
            update(id) { old ->
                if (local) {
                    val ordered = old.assets(role)
                    require(ordered.size < MAX_ASSETS_PER_ROLE)
                    // Migrate the original single image and its layout on the first append.
                    val layouts = ordered.associate { it.file to old.layout(role, it) }
                    val next = old.withAssets(role, ordered + asset, asset)
                    next.copy(assetLayouts = next.assetLayouts + layouts + (asset.file to ArtistImageLayout()))
                } else {
                    previous = if (role == ArtistImageRole.LOGO) old.logo else old.signature
                    val next = if (role == ArtistImageRole.LOGO) old.copy(logo = asset) else old.copy(signature = asset)
                    next.copy(assetLayouts = next.assetLayouts - previous?.file.orEmpty())
                }
            }
            committed = true
            file(previous)?.delete()
            asset
        } finally { if (!committed) target.delete() }
    }

    suspend fun clearLocal(id: String, role: ArtistImageRole) = removeLocal(id, role, null)

    suspend fun removeLocal(id: String, role: ArtistImageRole, filename: String?) {
        var removed: List<ArtistImageAsset> = emptyList()
        update(id) { old ->
            val ordered = old.assets(role)
            removed = ordered.filter { filename == null || it.file == filename }
            val remaining = ordered - removed.toSet()
            val selected = old.local(role)?.takeIf { it in remaining } ?: remaining.firstOrNull()
            old.withAssets(role, remaining, selected).copy(assetLayouts = old.assetLayouts - removed.map { it.file }.toSet())
        }
        withContext(Dispatchers.IO) { removed.forEach { file(it)?.delete() } }
    }

    companion object {
        const val MAX_ASSETS_PER_ROLE = 12
        const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
        private val ASSET_NAME = Regex("(?:local|remote)-[a-f0-9-]{36}\\.(?:png|jpg|webp|svg)")

        internal fun imageInfo(bytes: ByteArray): Pair<String, Float> {
            require(bytes.isNotEmpty() && bytes.size <= MAX_IMAGE_BYTES)
            val prefix = bytes.take(256).toByteArray().toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\n', '\r', '\t')
            if (prefix.startsWith("<")) {
                val text = bytes.toString(Charsets.UTF_8)
                require(!Regex("<!DOCTYPE|<!ENTITY|<script", RegexOption.IGNORE_CASE).containsMatchIn(text))
                // No outside files, network references, or embedded image documents in imported SVGs.
                require(!Regex("(?:href|xlink:href)\\s*=\\s*[\"'](?!#)", RegexOption.IGNORE_CASE).containsMatchIn(text))
                val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
                val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
                require(root.localName == "svg" || root.nodeName == "svg")
                val box = root.getAttribute("viewBox").trim().split(Regex("[ ,]+"))
                val w = box.getOrNull(2)?.toFloatOrNull() ?: root.getAttribute("width").removeSuffix("px").toFloatOrNull() ?: 100f
                val h = box.getOrNull(3)?.toFloatOrNull() ?: root.getAttribute("height").removeSuffix("px").toFloatOrNull() ?: 100f
                require(w.isFinite() && h.isFinite() && w > 0 && h > 0)
                return "svg" to (w / h).coerceIn(0.05f, 30f)
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..32768 && bounds.outHeight in 1..32768)
            val ext = when (bounds.outMimeType) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                "image/webp" -> "webp"
                else -> throw IllegalArgumentException("Unsupported artist image")
            }
            return ext to (bounds.outWidth.toFloat() / bounds.outHeight).coerceIn(0.05f, 30f)
        }
    }
}
