package com.prism.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.foryou.PlaylistDiscovery
import com.prism.music.tr
import com.prism.music.ui.components.PrismTextButton
import androidx.compose.material3.Text
import kotlin.math.roundToInt

@Composable
internal fun PlaylistDiscoverySettings(value: PlaylistDiscovery, compact: Boolean = false, onChange: (PlaylistDiscovery) -> Unit) {
    var width by remember(value.width) { mutableFloatStateOf(value.width.toFloat()) }
    Column {
        Label(tr(R.string.discovery_genre))
        val genreLabels = listOf(R.string.discovery_similar, R.string.balanced, R.string.discovery_exploratory)
        Chips(genreLabels.indices.toList(), value.genre, { tr(genreLabels[it]) }) { onChange(value.copy(genre = it)) }
        Label(tr(R.string.discovery_artist))
        val artistLabels = listOf(R.string.discovery_familiar, R.string.balanced, R.string.discovery_new)
        Chips(artistLabels.indices.toList(), value.artist, { tr(artistLabels[it]) }) { onChange(value.copy(artist = it)) }
        if (!compact) {
        Label(tr(R.string.discovery_width) + " · " + width.roundToInt())
        Slider(width, { width = it }, Modifier.padding(horizontal = 20.dp), valueRange = 0f..100f,
            onValueChangeFinished = { onChange(value.copy(width = width.roundToInt())) })
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr(R.string.discovery_similar), style = MaterialTheme.typography.labelSmall)
            Text(tr(R.string.discovery_exploratory), style = MaterialTheme.typography.labelSmall)
        }
        Toggle(tr(R.string.discovery_repetition), null, value.limitRepetition) { onChange(value.copy(limitRepetition = it)) }
        }
        Toggle(tr(R.string.discovery_no_repeat_artists), null, value.noRepeatArtists) { onChange(value.copy(noRepeatArtists = it)) }
        Label(tr(R.string.discovery_language))
        val languages = listOf(R.string.low, R.string.medium, R.string.high)
        Chips(languages.indices.toList(), value.languageSensitivity, { tr(languages[it]) }) { onChange(value.copy(languageSensitivity = it)) }
        PrismTextButton(onClick = { onChange(PlaylistDiscovery()) }, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(tr(R.string.reset))
        }
    }
}
