package com.prism.music.data.foryou

import com.prism.music.data.model.Song
import kotlinx.coroutines.CancellationException

/**
 * A small set of suggestions for a local playlist, using Prism's existing radio.
 * Private uploads and device-only ids are never sent as radio seeds.
 */
data class PlaylistDiscovery(
    val genre: Int = 1,
    val artist: Int = 1,
    val width: Int = 50,
    val limitRepetition: Boolean = true,
) {
    fun encode() = listOf(genre, artist, width, if (limitRepetition) 1 else 0).joinToString(",")
    companion object {
        fun decode(value: String?) = value?.split(",")?.mapNotNull(String::toIntOrNull)?.takeIf { it.size == 4 }
            ?.let { PlaylistDiscovery(it[0].coerceIn(0, 2), it[1].coerceIn(0, 2), it[2].coerceIn(0, 100), it[3] != 0) }
            ?: PlaylistDiscovery()
    }
}

object PlaylistSuggestions {
    const val LIMIT = 10
    private const val MAX_SEEDS = 3

    private fun eligible(song: Song) =
        song.id.isNotBlank() && !song.isLocal && !song.isUpload && !song.isRss &&
            !song.isEpisode && !song.isLive

    /** Spread the lookups across the playlist, not just its opening track. */
    fun seeds(songs: List<Song>, round: Int = 0): List<Song> {
        val eligible = songs.filter(::eligible).distinctBy { it.id }
        if (eligible.isEmpty()) return emptyList()
        val start = Math.floorMod(round, eligible.size)
        return listOf(eligible[start], eligible[(start + eligible.size / 2) % eligible.size], eligible[(start + eligible.size - 1) % eligible.size])
            .distinctBy { it.id }.take(MAX_SEEDS)
    }

    fun discoverySeeds(songs: List<Song>, genres: Map<String, Set<String>>, round: Int): List<Song> {
        val tracks = songs.filter(::eligible).distinctBy { it.id }
        val buckets = tracks.groupBy { genres[it.id].orEmpty().sorted().firstOrNull() }
            .values.sortedByDescending { it.size }
        if (buckets.size <= 1) return seeds(tracks, round)
        val offset = Math.floorMod(round, buckets.size)
        return List(minOf(MAX_SEEDS, buckets.size)) { i ->
            val bucket = buckets[(offset + i) % buckets.size]
            bucket[Math.floorMod(round / buckets.size, bucket.size)]
        }
    }

    fun rank(
        existing: List<Song>, candidates: List<Song>, genres: Map<String, Set<String>>,
        tuning: PlaylistDiscovery, recent: Set<String>, limit: Int = LIMIT,
    ): List<Song> {
        fun names(song: Song) = song.artists.map { it.id ?: it.name.trim().lowercase() }.filter(String::isNotBlank)
        fun recording(song: Song) = song.title.lowercase().substringBefore(" (").substringBefore(" [").trim() to names(song).firstOrNull()
        val distribution = existing.flatMap { genres[it.id].orEmpty() }.groupingBy { it }.eachCount()
        val peakGenre = distribution.values.maxOrNull() ?: 1
        val artists = existing.flatMap(::names).toSet()
        val existingRecordings = existing.mapTo(hashSetOf(), ::recording)
        val remaining = select(existing, candidates, limit = 160).toMutableList()
        val playedArtists = hashMapOf<String, Int>()
        val playedGenres = hashMapOf<String, Int>()
        val recordings = hashSetOf<Pair<String, String?>>()
        val chosen = mutableListOf<Song>()
        val breadth = tuning.width / 100.0
        repeat(minOf(limit.coerceAtLeast(0), remaining.size)) {
            val best = remaining.indices.maxByOrNull { i ->
                val song = remaining[i]
                val tags = genres[song.id].orEmpty()
                val familiar = names(song).any { it in artists }
                val overlap = tags.maxOfOrNull { (distribution[it] ?: 0).toDouble() / peakGenre }
                val genreScore = when (tuning.genre) {
                    0 -> (overlap ?: 0.2) * 4
                    2 -> (overlap?.let { 1 - it } ?: 0.2) * 4
                    else -> (overlap?.let { if (it > 0) 0.7 else 0.45 } ?: 0.2) * 2
                }
                val artistScore = when (tuning.artist) {
                    0 -> if (familiar) 3.0 else 0.0
                    2 -> if (!familiar && names(song).isNotEmpty()) 3.0 else 0.0
                    else -> 0.0
                }
                val duplicate = recording(song) in existingRecordings || recording(song) in recordings
                val variety = tags.maxOfOrNull { playedGenres[it] ?: 0 } ?: 0
                val artistRepeats = names(song).maxOfOrNull { playedArtists[it] ?: 0 } ?: 0
                genreScore + artistScore + (overlap ?: 0.0) * (1 - breadth) * 2 -
                    variety * (0.7 + breadth * 1.7) - artistRepeats * (if (tuning.limitRepetition) 2.0 else 0.35) -
                    (if (tuning.limitRepetition && duplicate) 8 else 0) -
                    (if (song.id in recent) if (tuning.limitRepetition) 12 else 5 else 0) - i * 0.008
            } ?: return@repeat
            val song = remaining.removeAt(best)
            chosen += song
            recordings += recording(song)
            names(song).forEach { playedArtists[it] = (playedArtists[it] ?: 0) + 1 }
            genres[song.id].orEmpty().forEach { playedGenres[it] = (playedGenres[it] ?: 0) + 1 }
        }
        return chosen
    }

    fun replaceSlot(songs: List<Song>, id: String, replacement: Song?): List<Song> {
        val index = songs.indexOfFirst { it.id == id }
        if (index < 0 || replacement?.id?.let { next -> songs.any { it.id == next } } == true) return songs
        return songs.toMutableList().apply { if (replacement == null) removeAt(index) else set(index, replacement) }
    }

    /** Keep radio order, never alter the playlist, and omit songs already in it. */
    fun select(
        existing: List<Song>, candidates: List<Song>, limit: Int = LIMIT,
        allowed: (Song) -> Boolean = { true },
    ): List<Song> {
        if (limit <= 0) return emptyList()
        val seen = existing.mapTo(HashSet()) { it.id }
        return candidates.asSequence().filter(::eligible).filter(allowed).filter { seen.add(it.id) }.take(limit).toList()
    }

    /**
     * At most three radio requests. A failed seed doesn't discard successful ones.
     * Cancellation always reaches the caller. No queue is played or saved here.
     */
    suspend fun load(
        songs: List<Song>, allowed: (Song) -> Boolean = { true },
        excluded: Set<String> = emptySet(), round: Int = 0,
        seedGenres: Map<String, Set<String>> = emptyMap(), candidateLimit: Int = LIMIT,
        radio: suspend (Song) -> List<Song>,
    ): List<Song> {
        val candidates = ArrayList<Song>()
        var failure: Exception? = null
        var answered = false
        for (seed in discoverySeeds(songs.filter(allowed), seedGenres, round)) {
            try {
                candidates += radio(seed)
                answered = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        if (!answered && failure != null) throw failure
        return select(songs, candidates, limit = candidateLimit, allowed = { it.id !in excluded && allowed(it) })
    }
}
