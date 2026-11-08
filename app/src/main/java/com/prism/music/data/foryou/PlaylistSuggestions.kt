package com.prism.music.data.foryou

import com.prism.music.data.model.Song
import kotlinx.coroutines.CancellationException

/**
 * A small set of suggestions for a local playlist, using Prism's existing radio.
 * Private uploads and device-only ids are never sent as radio seeds.
 */
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
        radio: suspend (Song) -> List<Song>,
    ): List<Song> {
        val candidates = ArrayList<Song>()
        var failure: Exception? = null
        var answered = false
        for (seed in seeds(songs.filter(allowed), round)) {
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
        return select(songs, candidates, allowed = { it.id !in excluded && allowed(it) })
    }
}
