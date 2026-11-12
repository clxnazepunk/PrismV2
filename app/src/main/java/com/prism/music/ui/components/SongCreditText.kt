package com.prism.music.ui.components

import com.prism.music.data.model.ArtistRef
import com.prism.music.data.model.BrowseItem
import com.prism.music.data.model.Song
import com.prism.music.data.model.SongItem

private const val FEAT = "feat."
private val bracketCredit = Regex("""(\(|\[)(feat\.?|ft\.?|featuring|con)\s+([^\[\]()]+)(\)|\])""", RegexOption.IGNORE_CASE)
private val dashCredit = Regex("""(\s+[–—-]\s+)(feat\.?|ft\.?|featuring|con)\s+(.+?)(?=\s+[–—-]\s+|$)""", RegexOption.IGNORE_CASE)
private val plainCredit = Regex("""\s+(feat\.|ft\.|featuring|con)\s+(?=(.+)$)""", RegexOption.IGNORE_CASE)
private val separator = Regex("""^\s*(?:[,/&+·;]\s*|(?:y|and|e|x)\s+)+""", RegexOption.IGNORE_CASE)

private data class DisplayCredit(val start: Int, val end: Int, val guests: List<ArtistRef>)

/** Match known guest names one at a time; name punctuation can itself include a comma or ampersand. */
private fun Song.matchGuests(text: String): List<ArtistRef> {
    val available = artists.drop(1).filter { it.name.isNotBlank() }.distinctBy { it.name.trim().lowercase() }
        .sortedByDescending { it.name.trim().length }.toMutableList()
    val matched = mutableListOf<ArtistRef>()
    var remainder = text.trim()
    while (remainder.isNotEmpty()) {
        val guest = available.firstOrNull {
            val name = it.name.trim()
            remainder.startsWith(name, ignoreCase = true) &&
                (remainder.length == name.length || separator.containsMatchIn(remainder.substring(name.length)))
        } ?: return emptyList()
        available.remove(guest)
        matched += guest
        remainder = remainder.substring(guest.name.trim().length).trimEnd()
        if (remainder.isNotEmpty()) {
            val connector = separator.find(remainder) ?: return emptyList()
            remainder = remainder.substring(connector.range.last + 1)
        }
    }
    return matched
}

private fun Song.displayCredits(): List<DisplayCredit> {
    if (artists.size < 2) return emptyList()
    fun parse(match: MatchResult, marker: Int, names: Int): DisplayCredit? {
        val token = match.groups[marker] ?: return null
        val guests = matchGuests(match.groupValues[names])
        return guests.takeIf { it.isNotEmpty() }?.let { DisplayCredit(token.range.first, token.range.last + 1, it) }
    }
    val delimited = bracketCredit.findAll(title).mapNotNull { match ->
        val opening = match.groupValues[1]
        val closing = match.groupValues[4]
        if ((opening == "(" && closing == ")") || (opening == "[" && closing == "]")) parse(match, 2, 3) else null
    }.toList() + dashCredit.findAll(title).mapNotNull { parse(it, 2, 3) }
    if (delimited.isNotEmpty()) return delimited.sortedBy { it.start }
    return plainCredit.findAll(title).mapNotNull { match ->
        val parsed = parse(match, 1, 2) ?: return@mapNotNull null
        val localized = match.groupValues[1].equals("con", ignoreCase = true)
        parsed.takeIf { !localized || it.guests.all(ArtistRef::featured) }
    }.toList()
}

/** Only presentation changes; the provider title is retained in Song and sent back untouched. */
internal val Song.displayTitle: String
    get() = displayCredits().asReversed().fold(title) { value, credit ->
        value.replaceRange(credit.start, credit.end, FEAT)
    }

internal val Song.displayArtists: String
    get() {
        val guests = (artists.drop(1).filter(ArtistRef::featured) + displayCredits().flatMap { it.guests }).distinct()
        if (guests.isEmpty()) return artistText
        val lead = artists.filterNot { it in guests }.joinToString(", ") { it.name }
        return "$lead $FEAT " + guests.joinToString(", ") { it.name }
    }

internal val BrowseItem.displayTitle: String get() = (this as? SongItem)?.song?.displayTitle ?: title
internal val BrowseItem.displaySubtitle: String get() = (this as? SongItem)?.song?.displayArtists ?: subtitle
