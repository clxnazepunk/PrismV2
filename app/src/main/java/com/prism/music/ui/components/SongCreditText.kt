package com.prism.music.ui.components

import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem

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
                "${open}feat. $names$close"
            else credit.value
        }
        return suffixedGuest.replace(bracketed) { credit ->
            val (separator, names) = credit.destructured
            if (isGuestCredit(names)) "${separator}feat. $names" else credit.value
        }
    }

/** Keep an explicit credit on the guest line when the title supplies one. */
internal val Song.displayArtists: String
    get() {
        val guest = displayTitle.substringAfter("feat. ", "").trimEnd(')', ']')
        if (guest.isEmpty() || !isGuestCredit(guest)) return artistText
        return "$primaryArtist feat. " + artists.drop(1).joinToString(", ") { it.name }
    }

internal val BrowseItem.displayTitle: String get() = (this as? SongItem)?.song?.displayTitle ?: title
internal val BrowseItem.displaySubtitle: String get() = (this as? SongItem)?.song?.displayArtists ?: subtitle
