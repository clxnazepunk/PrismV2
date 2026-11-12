package com.prism.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prism.music.R
import com.prism.music.data.meta.MusicTags
import com.prism.music.tr
import com.prism.music.ui.components.PrismChip
import com.prism.music.ui.components.PrismTextButton
import com.prism.music.ui.theme.LocalUi

@Composable
internal fun PlaylistFilterPanel(
    expanded: Boolean, onToggle: () -> Unit, genres: List<Pair<String, Int>>, moods: List<Pair<String, Int>>,
    showMoods: Boolean, genre: String?, mood: String?, onGenre: (String?) -> Unit, onMood: (String?) -> Unit,
    onRefresh: (() -> Unit)?, moodMessage: Int, idle: Color,
) {
    Column {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val active = listOfNotNull(genre, mood).joinToString(" · ") { MusicTags.shown(it) }
            Text(
                active.ifBlank { tr(if (showMoods) R.string.moods_genres else R.string.genre) },
                Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            )
            PrismTextButton(onClick = onToggle) { Text(tr(if (expanded) R.string.show_less else R.string.show_more)) }
        }
        val reduceMotion = LocalUi.current.reduceMotion
        AnimatedVisibility(
            expanded,
            enter = if (reduceMotion) EnterTransition.None else fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = if (reduceMotion) ExitTransition.None else fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column {
                if (genres.isNotEmpty()) TagChips(genres, genre, onGenre, idle)
                if (showMoods) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(R.string.music_moods), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        PrismTextButton(onClick = { onRefresh?.invoke() }, enabled = onRefresh != null) { Text(tr(R.string.moods_refresh)) }
                    }
                    if (moods.isNotEmpty()) TagChips(moods, mood, onMood, idle)
                    if (moods.isEmpty() || moodMessage == R.string.loading || moodMessage == R.string.moods_unavailable) Text(tr(moodMessage), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun TagChips(tags: List<Pair<String, Int>>, selected: String?, onSelect: (String?) -> Unit, idle: Color) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PrismChip(selected == null, { onSelect(null) }, tr(R.string.all), idle = idle) }
        items(tags, key = { it.first }) { (tag, count) ->
            PrismChip(selected == tag, { onSelect(tag.takeUnless { it == selected }) }, "${MusicTags.shown(tag)} · $count", idle = idle)
        }
    }
}
