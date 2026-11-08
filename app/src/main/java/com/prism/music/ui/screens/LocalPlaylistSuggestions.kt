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
}

/** First step of playlist recommendations: local lists only, with no remote playlist writes. */
@Composable
internal fun LocalPlaylistSuggestions(
    playlistId: String,
    title: String,
    existing: List<Song>,
    state: LocalPlaylistSuggestionsState,
    scope: CoroutineScope,
) {
    val c = LocalContainer.current
    val context = LocalContext.current
    val current by c.player.currentSong.collectAsState()
    val visible = remember(existing, state.songs) { PlaylistSuggestions.select(existing, state.songs) }

    fun load() {
        if (state.loading) return
        state.requested = true
        state.loading = true
        state.failed = false
        // The screen's scope survives the footer being scrolled away, but ends on leaving the screen.
        scope.launch {
            try {
                val snapshot = c.prismLibrary.list(playlistId)?.songs.orEmpty()
                state.songs = withContext(Dispatchers.IO) {
                    PlaylistSuggestions.load(snapshot) { seed ->
                        c.radio(seed.id, "RDAMVM" + seed.id).songs
                    }
                }
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
            } finally {
                state.adding = state.adding - song.id
            }
        }
    }

    LaunchedEffect(playlistId) { if (!state.requested) load() }
    Column {
        SectionHeader(tr(R.string.more_music))
        when {
            state.loading -> LoadingState()
            state.failed -> PrismTextButton(onClick = { load() }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(tr(R.string.try_again))
            }
            visible.isEmpty() -> PrismTextButton(onClick = { load() }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(tr(R.string.refresh_the_list))
            }
        }
        for (song in visible) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SongRow(song, Modifier.weight(1f), playing = current?.id == song.id) { c.player.playSingle(song) }
            IconButton(
                onClick = { add(song) }, enabled = song.id !in state.adding,
                modifier = Modifier.testTag("addSuggestion:" + song.id),
            ) { Icon(PrismIcons.Add, tr(R.string.add) + " " + song.title) }
        }
    }
}
