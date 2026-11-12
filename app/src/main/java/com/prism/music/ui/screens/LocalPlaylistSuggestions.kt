package com.prism.music.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.size
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.foryou.PlaylistSuggestions
import com.prism.music.data.model.Song
import com.prism.music.tr
import com.prism.music.ui.components.LoadingState
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.components.PrismSpinner
import com.prism.music.ui.components.SectionHeader
import com.prism.music.ui.components.SongRow
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Owned by the collection screen: scrolling past the footer does not repeat its requests. */
@Stable
internal class LocalPlaylistSuggestionsState {
    var songs by mutableStateOf<List<Song>>(emptyList())
    var requested by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var adding by mutableStateOf<Set<String>>(emptySet())
    val history = LinkedHashMap<String, Long>()
    var candidates: List<Song> = emptyList()
    var appliedTuning: com.prism.music.data.foryou.PlaylistDiscovery? = null
    var slotIds by mutableStateOf<Map<String, Int>>(emptyMap())

    fun recent(): Set<String> {
        val cutoff = System.currentTimeMillis() - 30 * 60_000L
        history.entries.removeAll { it.value < cutoff }
        return history.keys.toSet()
    }

    fun rememberShown(songs: List<Song>) {
        val now = System.currentTimeMillis()
        songs.forEach { history.remove(it.id); history[it.id] = now }
        while (history.size > 150) history.remove(history.keys.first())
    }
    var round = 0
    val continuations = HashMap<String, String?>()
}

/** First step of playlist recommendations: local lists only, with no remote playlist writes. */
@Composable
internal fun LocalPlaylistSuggestions(
    playlistId: String,
    title: String,
    existing: List<Song>,
    state: LocalPlaylistSuggestionsState,
    scope: CoroutineScope,
    online: Boolean,
    playlistGenres: Map<String, Set<String>> = emptyMap(),
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val current by c.player.currentSong.collectAsState()
    var tuningOpen by remember(playlistId) { mutableStateOf(false) }
    val playlistArtists = remember(existing) { PlaylistSuggestions.playlistArtistKeys(existing) }
    val settings = com.prism.music.ui.theme.LocalAppSettings.current
    val disliked by c.library.dislikedIds.collectAsState()
    val excludedSongs by c.exclusions.songs.collectAsState()
    val excludedArtists by c.exclusions.artists.collectAsState()
    val excludedPlaylists by c.exclusions.playlists.collectAsState()
    // Respect the same unwanted songs/artists and explicit-content preference as recommendations elsewhere.
    fun allowed(song: Song) = song.id !in disliked &&
        !c.exclusions.outOfTaste(song, playlistId) &&
        (!song.explicit || settings.behavior.explicitContent == com.prism.music.data.prefs.ExplicitContent.SHOW)
    val visible = remember(existing, state.songs, disliked, excludedSongs, excludedArtists, excludedPlaylists, settings.behavior.explicitContent, settings.behavior.playlistDiscovery.noRepeatArtists) {
        PlaylistSuggestions.select(existing, state.songs) {
            allowed(it) && (!settings.behavior.playlistDiscovery.noRepeatArtists || !PlaylistSuggestions.hasPlaylistArtist(it, playlistArtists))
        }
    }

    val hasSeeds = remember(existing, disliked, excludedSongs, excludedArtists, excludedPlaylists, settings.behavior.explicitContent) {
        PlaylistSuggestions.seeds(existing.filter(::allowed)).isNotEmpty()
    }

    suspend fun fetch(snapshot: List<Song>, genres: Map<String, Set<String>>): List<Song> =
        PlaylistSuggestions.load(snapshot, allowed = ::allowed, round = state.round++, seedGenres = genres, candidateLimit = 120) { seed ->
            val page = c.radio(seed.id, "RDAMVM" + seed.id, continuation = state.continuations[seed.id])
            state.continuations[seed.id] = page.continuation
            page.songs
        }

    suspend fun ranked(snapshot: List<Song>, pool: List<Song>, recent: Set<String>, limit: Int, occupied: List<Song> = emptyList()): List<Song> = withContext(Dispatchers.Default) {
        val cached = c.meta.cachedSongTags(snapshot + pool)
        val tracks = snapshot + pool
        val genres = tracks.associate { song ->
            song.id to (cached[song.id].orEmpty() + playlistGenres[song.id].orEmpty())
                .filterNot { it in com.prism.music.data.meta.MusicTags.moods }.toSet()
        }
        val artistGenres = snapshot.filter { !it.primaryArtist.isBlank() }.groupBy { it.primaryArtist.lowercase() }
            .mapValues { (_, songs) -> songs.flatMap { genres[it.id].orEmpty() }.toSet() }
        val signals = genres.mapValues { it.value }.toMutableMap()
        pool.forEach { song -> if (signals[song.id].isNullOrEmpty()) signals[song.id] = artistGenres[song.primaryArtist.lowercase()].orEmpty() }
        PlaylistSuggestions.rank(snapshot, pool.filter(::allowed), signals, settings.behavior.playlistDiscovery, recent, limit,
            languages = cached.mapNotNull { (id, tags) -> tags.firstOrNull { it.startsWith("lang:") }?.removePrefix("lang:")?.let { id to it } }.toMap(),
            occupied = occupied)
    }

    fun load() {
        if (state.loading || state.adding.isNotEmpty() || !online || !hasSeeds) return
        state.requested = true
        state.loading = true
        state.failed = false
        // The screen's scope survives the footer being scrolled away, but ends on leaving the screen.
        scope.launch {
            try {
                val snapshot = c.prismLibrary.list(playlistId)?.songs.orEmpty()
                val previous = state.songs.mapTo(hashSetOf()) { it.id }
                val recent = state.recent() + previous
                val fresh = fetch(snapshot, playlistGenres)
                state.candidates = (fresh + state.candidates).distinctBy { it.id }.take(160)
                val different = state.candidates.filterNot { it.id in previous }
                val pool = if (different.size >= PlaylistSuggestions.LIMIT) different else state.candidates
                state.songs = ranked(snapshot, pool, recent, PlaylistSuggestions.LIMIT)
                state.slotIds = state.songs.mapIndexed { index, song -> song.id to index }.toMap()
                state.appliedTuning = settings.behavior.playlistDiscovery
                state.rememberShown(state.songs)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.failed = true
            } finally {
                state.loading = false
            }
        }
    }

    fun add(song: Song) {
        if (state.loading || state.adding.isNotEmpty()) return
        state.adding = state.adding + song.id
        scope.launch {
            try {
                // PrismLibrary rechecks ids against the current list, then appends and persists.
                val added = withContext(Dispatchers.IO) { c.prismLibrary.add(playlistId, listOf(song)) }
                if (added > 0) {
                    Toast.makeText(context, tr(R.string.added_to, title), Toast.LENGTH_SHORT).show()
                    val snapshot = c.prismLibrary.list(playlistId)?.songs.orEmpty()
                    val existingIds = snapshot.mapTo(hashSetOf()) { it.id }
                    val otherSuggestions = state.songs.filterNot { it.id == song.id }
                    val blocked = existingIds + otherSuggestions.map { it.id } + state.recent()
                    var fresh = state.candidates.filterNot { it.id in blocked }
                    if (fresh.isEmpty() && online) {
                        val fetched = fetch(snapshot, playlistGenres)
                        state.candidates = (fetched + state.candidates).distinctBy { it.id }.take(160)
                        fresh = fetched.filterNot { it.id in blocked }
                    }
                    val replacement = ranked(snapshot, fresh, emptySet(), 1, occupied = otherSuggestions).firstOrNull()
                    val index = state.slotIds[song.id] ?: state.songs.indexOfFirst { it.id == song.id }
                    state.songs = PlaylistSuggestions.replaceSlot(state.songs, song.id, replacement)
                    state.slotIds = (state.slotIds - song.id) + listOfNotNull(replacement?.let { it.id to index }).toMap()
                    replacement?.let { state.rememberShown(listOf(it)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, tr(R.string.try_again), Toast.LENGTH_SHORT).show()
            } finally {
                state.adding = state.adding - song.id
            }
        }
    }

    LaunchedEffect(playlistId, online, hasSeeds) { if (!state.requested) load() }
    LaunchedEffect(playlistId, settings.behavior.playlistDiscovery) {
        snapshotFlow { !state.loading && state.adding.isEmpty() }.first { it }
        val tuning = settings.behavior.playlistDiscovery
        if (state.candidates.isNotEmpty() && state.appliedTuning != tuning) {
            try {
                val snapshot = c.prismLibrary.list(playlistId)?.songs.orEmpty()
                val next = ranked(snapshot, state.candidates, emptySet(), PlaylistSuggestions.LIMIT)
                if (!state.loading && state.adding.isEmpty()) {
                    val previousSlots = state.slotIds
                    var slot = (previousSlots.values.maxOrNull() ?: -1) + 1
                    state.songs = next
                    state.slotIds = next.associate { it.id to (previousSlots[it.id] ?: slot++) }
                    state.appliedTuning = tuning
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.failed = true
            }
        }
    }
    PlaylistRecommendationFooter(
        enabled = settings.behavior.playlistRecommendations, hasSeeds = hasSeeds, online = online, loading = state.loading || state.adding.isNotEmpty(),
        failed = state.failed, hasResults = visible.isNotEmpty() || state.adding.isNotEmpty(), onRefresh = ::load,
    ) {
        val reducedMotion = com.prism.music.ui.theme.LocalUi.current.reduceMotion
        for ((index, slot) in state.songs.withIndex()) key(state.slotIds[slot.id] ?: index) {
            val song = slot.takeIf { candidate -> candidate.id in state.adding || visible.any { it.id == candidate.id } }
            AnimatedContent(song, contentKey = { it?.id }, transitionSpec = {
                if (reducedMotion) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                else (fadeIn() + slideInVertically { it / 6 }) togetherWith (fadeOut() + slideOutVertically { -it / 6 })
            }, label = "suggestion") { item ->
                if (item != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SongRow(item, Modifier.weight(1f), playing = current?.id == item.id) { c.player.playSingle(item) }
                    IconButton(
                        onClick = { add(item) }, enabled = !state.loading && state.adding.isEmpty(),
                        modifier = Modifier.testTag("addSuggestion:" + item.id),
                    ) { Icon(PrismIcons.Add, tr(R.string.add) + " " + item.title) }
                }
            }
        }
    }
    PrismTextButton(onClick = { tuningOpen = !tuningOpen }, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(tr(R.string.settings))
    }
    AnimatedVisibility(tuningOpen) {
        PlaylistDiscoverySettings(settings.behavior.playlistDiscovery, compact = true) { c.settings.setPlaylistDiscovery(it) }
    }
}

@Composable
internal fun PlaylistRecommendationFooter(
    enabled: Boolean, hasSeeds: Boolean, online: Boolean, loading: Boolean, failed: Boolean, hasResults: Boolean,
    onRefresh: () -> Unit, results: @Composable () -> Unit,
) {
    if (!enabled) return
    Column(Modifier.testTag("playlistRecommendations")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(tr(R.string.playlist_recommended_songs), Modifier.weight(1f))
            IconButton(onClick = onRefresh, enabled = !loading && online && hasSeeds, modifier = Modifier.testTag("refreshPlaylistSuggestions")) {
                if (loading && hasResults) PrismSpinner(Modifier.size(18.dp))
                else Icon(PrismIcons.Refresh, tr(R.string.refresh_the_list))
            }
        }
        when {
            !hasSeeds -> Text(tr(R.string.no_results), Modifier.padding(horizontal = 20.dp))
            !online && !hasResults -> Text(tr(R.string.you_re_offline), Modifier.padding(horizontal = 20.dp))
            loading && !hasResults -> LoadingState(Modifier.testTag("loadingPlaylistSuggestions"))
            failed && online -> PrismTextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = 16.dp).testTag("retryPlaylistSuggestions")) {
                Text(tr(R.string.try_again))
            }
            !hasResults -> Text(tr(R.string.no_results), Modifier.padding(horizontal = 20.dp))
        }
        if (hasSeeds && hasResults) results()
    }
}
