package com.prism.music.ui.components

import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem

private const val FEAT = "feat."

private val confirmedSuffix = Regex("""(\s+)con\s+(.+)$""", RegexOption.IGNORE_CASE)
private val explicitTitleGuest = Regex("""(?:[(\[]|\s[-–—]\s)feat\.\s+([^\]\)]+)""", RegexOption.IGNORE_CASE)

private val bracketedGuest = Regex("""(\(|\[)con\s+([^\[\]()]+)(\)|\])""", RegexOption.IGNORE_CASE)
private val suffixedGuest = Regex("""(\s+[–—-]\s+)con\s+(.+)$""", RegexOption.IGNORE_CASE)

private fun Song.isGuestCredit(names: String): Boolean {
    val guests = artists.drop(1).map { it.name.trim() }.filter { it.isNotEmpty() }
    return guests.any { it.equals(names.trim(), ignoreCase = true) } ||
        guests.size > 1 && listOf(", ", " & ", " y ").any { guests.joinToString(it).equals(names.trim(), ignoreCase = true) }
}

/** YouTube can localize a credit in a title. Only change text corroborated by its guest artist metadata. */
internal val Song.displayTitle: String
    get() {
        if (artists.size < 2) return title
        val bracketed = bracketedGuest.replace(title) { credit ->
            val (open, names, close) = credit.destructured
            if ((open == "(" && close == ")" || open == "[" && close == "]") && isGuestCredit(names))
                "$open$FEAT $names$close"
            else credit.value
        }
        val suffixed = suffixedGuest.replace(bracketed) { credit ->
            val (separator, names) = credit.destructured
            if (isGuestCredit(names)) "$separator$FEAT $names" else credit.value
        }
        return confirmedSuffix.replace(suffixed) { credit ->
            val (separator, names) = credit.destructured
            if (artists.any { it.featured } && isGuestCredit(names)) "$separator$FEAT $names" else credit.value
        }
    }

/** Keep an explicit credit on the guest line when the title supplies one. */
internal val Song.displayArtists: String
    get() {
        val otherArtists = artists.drop(1)
        val fromMetadata = otherArtists.filter { it.featured }
        val guest = explicitTitleGuest.find(displayTitle)?.groupValues?.get(1).orEmpty()
        val credited = fromMetadata.ifEmpty {
            if (guest.isEmpty() || !isGuestCredit(guest)) return artistText
            otherArtists.filter { it.name.trim().equals(guest.trim(), ignoreCase = true) }.ifEmpty { otherArtists }
        }
        val lead = artists.filterNot { it in credited }.joinToString(", ") { it.name }
        return "$lead $FEAT " + credited.joinToString(", ") { it.name }
    }

internal val BrowseItem.displayTitle: String get() = (this as? SongItem)?.song?.displayTitle ?: title
internal val BrowseItem.displaySubtitle: String get() = (this as? SongItem)?.song?.displayArtists ?: subtitle
