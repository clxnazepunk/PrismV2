package com.prism.music.ui.screens

import android.widget.Toast
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
import com.prism.music.ui.components.SectionHeader
import com.prism.music.ui.components.SongRow
import com.prism.music.ui.theme.LocalContainer
import com.prism.music.ui.theme.PrismIcons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owned by the collection screen: scrolling past the footer does not repeat its requests. */
@Stable
internal class LocalPlaylistSuggestionsState {
    var songs by mutableStateOf<List<Song>>(emptyList())
    var requested by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(false)
    var adding by mutableStateOf<Set<String>>(emptySet())
    var recentIds: Set<String> = emptySet()
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
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val current by c.player.currentSong.collectAsState()
    val settings = com.prism.music.ui.theme.LocalAppSettings.current
    val disliked by c.library.dislikedIds.collectAsState()
    val excludedSongs by c.exclusions.songs.collectAsState()
    val excludedArtists by c.exclusions.artists.collectAsState()
    val excludedPlaylists by c.exclusions.playlists.collectAsState()
    // Respect the same unwanted songs/artists and explicit-content preference as recommendations elsewhere.
    fun allowed(song: Song) = song.id !in disliked &&
        !c.exclusions.outOfTaste(song, playlistId) &&
        (!song.explicit || settings.behavior.explicitContent == com.prism.music.data.prefs.ExplicitContent.SHOW)
    val visible = remember(existing, state.songs, disliked, excludedSongs, excludedArtists, excludedPlaylists, settings.behavior.explicitContent) {
        PlaylistSuggestions.select(existing, state.songs, allowed = ::allowed)
    }

    val hasSeeds = remember(existing, disliked, excludedSongs, excludedArtists, excludedPlaylists, settings.behavior.explicitContent) {
        PlaylistSuggestions.seeds(existing.filter(::allowed)).isNotEmpty()
    }

    fun load() {
        if (state.loading || !online || !hasSeeds) return
        state.requested = true
        state.loading = true
        state.failed = false
        // The screen's scope survives the footer being scrolled away, but ends on leaving the screen.
        scope.launch {
            try {
                val snapshot = c.prismLibrary.list(playlistId)?.songs.orEmpty()
                state.songs = withContext(Dispatchers.IO) {
                    PlaylistSuggestions.load(snapshot, allowed = ::allowed, excluded = state.recentIds, round = state.round) { seed ->
                        val page = c.radio(seed.id, "RDAMVM" + seed.id, continuation = state.continuations[seed.id])
                        state.continuations[seed.id] = page.continuation
                        page.songs
                    }
                }
                state.round++
                state.recentIds = (state.recentIds + state.songs.map { it.id }).toList().takeLast(100).toSet()
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
        if (song.id in state.adding) return
        state.adding = state.adding + song.id
        scope.launch {
            try {
                // PrismLibrary rechecks ids against the current list, then appends and persists.
                val added = withContext(Dispatchers.IO) { c.prismLibrary.add(playlistId, listOf(song)) }
                if (added > 0) Toast.makeText(context, tr(R.string.added_to, title), Toast.LENGTH_SHORT).show()
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
    PlaylistRecommendationFooter(
        enabled = settings.behavior.playlistRecommendations, hasSeeds = hasSeeds, online = online, loading = state.loading,
        failed = state.failed, hasResults = visible.isNotEmpty(), onRefresh = ::load,
    ) {
        for (song in visible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SongRow(song, Modifier.weight(1f), playing = current?.id == song.id) { c.player.playSingle(song) }
            IconButton(
                onClick = { add(song) }, enabled = song.id !in state.adding,
                modifier = Modifier.testTag("addSuggestion:" + song.id),
            ) { Icon(PrismIcons.Add, tr(R.string.add) + " " + song.title) }
        }
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
                Icon(PrismIcons.Refresh, tr(R.string.refresh_the_list))
            }
        }
        when {
            !hasSeeds -> Text(tr(R.string.no_results), Modifier.padding(horizontal = 20.dp))
            !online && !hasResults -> Text(tr(R.string.you_re_offline), Modifier.padding(horizontal = 20.dp))
            loading -> LoadingState(Modifier.testTag("loadingPlaylistSuggestions"))
            failed && online -> PrismTextButton(onClick = onRefresh, modifier = Modifier.padding(horizontal = 16.dp).testTag("retryPlaylistSuggestions")) {
                Text(tr(R.string.try_again))
            }
            !hasResults -> Text(tr(R.string.no_results), Modifier.padding(horizontal = 20.dp))
        }
        if (hasSeeds && hasResults) results()
    }
}
