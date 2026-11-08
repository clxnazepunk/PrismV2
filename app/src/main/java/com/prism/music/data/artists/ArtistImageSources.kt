package com.prism.music.data.artists

// Adapted from BitChord's ArtistSignatures.kt, AppleArtistArt.kt and AppleCatalogue.kt.
// BitChord contributors, GNU GPL v3. See docs/THIRD_PARTY_PORTS.md for pinned sources.

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class RemoteArtistImage(val url: String, val source: String? = null, val license: String? = null)

/** Public metadata only; the caller's HTTP client has no YouTube session or cookies. */
class ArtistImageSources(private val get: (String) -> String?) {
    fun signature(name: String, base: String): RemoteArtistImage? = signatures(name, base).firstOrNull()

    fun signatures(name: String, base: String): List<RemoteArtistImage> {
        if (base.isBlank()) return emptyList()
        val url = "$base/v1/signatures?artist=${URLEncoder.encode(name, "UTF-8")}"
        return get(url)?.let(::parseSignatures).orEmpty()
    }

    /** A same-named artist is insufficient: verify at least one song/release against their catalogue. */
    fun logo(name: String, titles: Collection<String>): RemoteArtistImage? {
        val keys = titles.mapNotNull(::titleKey).toSet()
        if (keys.isEmpty()) return null
        val search = get("https://itunes.apple.com/search?term=${URLEncoder.encode(name, "UTF-8")}&entity=musicArtist&limit=5") ?: return null
        val rows = JSONObject(search).optJSONArray("results") ?: return null
        for (i in 0 until minOf(rows.length(), 5)) {
            val row = rows.optJSONObject(i) ?: continue
            if (matchKey(row.optString("artistName")) != matchKey(name)) continue
            val id = row.optLong("artistId").takeIf { it > 0 } ?: continue
            val body = get("https://itunes.apple.com/lookup?id=$id&entity=song&limit=200") ?: continue
            if (!sharesTitles(body, keys)) continue
            val supplied = row.optString("artistLinkUrl").substringBefore('?').toHttpUrlOrNull()
            val page = supplied?.takeIf { it.isHttps && it.host == "music.apple.com" }?.toString() ?: "https://music.apple.com/us/artist/$id"
            val html = get(page) ?: continue
            parseAppleLogo(html, page)?.let { return it }
        }
        return null
    }

    companion object {
        fun baseUrl(raw: String): String? {
            val url = raw.trim().trimEnd('/').takeIf { it.length <= 500 }?.toHttpUrlOrNull() ?: return null
            return url.takeIf { it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.query == null && it.fragment == null }?.toString()?.trimEnd('/')
        }

        internal fun matchKey(name: String): String = Normalizer.normalize(name.lowercase(Locale.ROOT).replace('$', 's'), Normalizer.Form.NFD).filter { it.isLetterOrDigit() }
        internal fun titleKey(title: String): String? = matchKey(title.substringBefore(" (").substringBefore(" [").removeSuffix(" - Single").removeSuffix(" - EP")).takeIf { it.length >= 4 }

        internal fun sharesTitles(body: String, keys: Set<String>): Boolean {
            val rows = JSONObject(body).optJSONArray("results") ?: return false
            return (0 until rows.length()).any { i ->
                val row = rows.optJSONObject(i) ?: return@any false
                listOf("trackName", "collectionName").any { titleKey(row.optString(it)) in keys }
            }
        }

        internal fun parseSignature(body: String): RemoteArtistImage? = parseSignatures(body).firstOrNull()

        internal fun parseSignatures(body: String): List<RemoteArtistImage> {
            val root = JSONObject(body)
            // The provider explicitly returns matches instead of guessing when a name is ambiguous.
            val signatures = root.optJSONArray("signatures") ?: return emptyList()
            return (0 until signatures.length()).mapNotNull { i ->
                val sig = signatures.optJSONObject(i) ?: return@mapNotNull null
                val asset = sig.optJSONObject("asset")
                val url = listOf(asset?.optString("url"), sig.optString("asset"), sig.optString("url"), sig.optString("original_url"), sig.optString("originalUrl"))
                    .firstOrNull { it?.toHttpUrlOrNull()?.isHttps == true } ?: return@mapNotNull null
                val license = sig.optJSONObject("license")
                val format = asset?.optString("format")?.ifBlank { null } ?: sig.optString("format").ifBlank { url.substringBefore('?').substringAfterLast('.') }
                val score = (if (license?.optString("status") == "known") 1000 else 0) + when (format.lowercase(Locale.ROOT)) { "svg" -> 200; "png" -> 100; else -> 0 } +
                    when (sig.optString("verification")) { "artist_verified" -> 30; "verified" -> 20; else -> 0 }
                score to RemoteArtistImage(url, sig.optJSONObject("source")?.optString("url")?.ifBlank { null }, license?.optString("name")?.ifBlank { null })
            }.sortedByDescending { it.first }.map { it.second }
        }

        internal fun parseAppleLogo(html: String, source: String): RemoteArtistImage? {
            val marker = html.indexOf("id=\"serialized-server-data\"")
            if (marker < 0) return null
            val start = html.indexOf('>', marker) + 1
            val end = html.indexOf("</script>", start)
            if (start <= 0 || end < 0) return null
            val root = JSONObject("{\"d\":${html.substring(start, end)}}").get("d")
            val header = findHeader(root, 0) ?: return null
            val logo = header.optJSONObject("artistLogo")?.optJSONObject("dictionary") ?: return null
            val w = logo.optInt("width")
            val h = logo.optInt("height")
            if (w <= 0 || h <= 0) return null
            val url = logo.optString("url").replace("{w}", "1000").replace("{h}", (1000L * h / w).coerceAtLeast(1).toString()).replace("{c}", "bb").replace("{f}", "png")
            return url.takeIf { it.toHttpUrlOrNull()?.isHttps == true }?.let { RemoteArtistImage(it, source, "Apple Music artwork") }
        }

        private fun findHeader(node: Any?, depth: Int): JSONObject? {
            if (depth > 64) return null
            when (node) {
                is JSONObject -> {
                    if (node.has("artistLogo")) return node
                    val keys = node.keys()
                    while (keys.hasNext()) findHeader(node.opt(keys.next()), depth + 1)?.let { return it }
                }
                is JSONArray -> for (i in 0 until node.length()) findHeader(node.opt(i), depth + 1)?.let { return it }
            }
            return null
        }
    }
}
